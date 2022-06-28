package scenarios.impl

import canoe.api.{Scenario, _}
import canoe.models.User
import canoe.syntax._
import cats.MonadError
import cats.effect.Concurrent
import cats.effect.std.Semaphore
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import domain.callback.Flow.AddToQueueFlow
import domain.callback.{CallbackAnswer, CallbackQueryExt}
import domain.message.ReplyMessage
import domain.queue.{AddToQueueOption, QueueSeries}
import domain.user.StudentReadDomain
import implicits.circe._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.syntax._
import scenarios.QueueScenarios
import service.{AuthService, QueueService}
import util.DecodingUtil.parseRedisData
import util.MarshallingUtil.{handleErrorInScenario, replyMsg}
import util.TelegramElementBuilder.{buildAddToQueueOptionsKeyboard, buildInlineKeyboard}

import java.util.Date

class QueueScenariosImpl[F[_]: TelegramClient: Concurrent](
  redisCommands: RedisCommands[F, String, String],
  queueService:  QueueService[F],
  authService:   AuthService[F],
  semaphores:    Map[String, Semaphore[F]]
)(
  implicit me: MonadError[F, Throwable]
) extends QueueScenarios[F] {

  private val dateRegex = "(0?[1-9]|[12]\\d|30|31)[^\\w\\d\\r\\n:](0?[1-9]|1[0-2])[^\\w\\d\\r\\n:](\\d{4}|\\d{2})"

  private def checkForAuthorization(user: Option[User]): F[StudentReadDomain] = {
    for {
      studentE <- authService.checkAuthUser(user)
      student <- studentE.fold(
        error => me.raiseError(error): F[StudentReadDomain],
        value => value.pure: F[StudentReadDomain]
      )
    } yield student
  }

  //TODO remove duplicated code
  private def answerForAuthorization(user: Option[User]): Scenario[F, StudentReadDomain] = {
    for {
      studentE <- Scenario.eval(authService.checkAuthUser(user))
      student <- studentE.fold(
        error => Scenario.raiseError(error): Scenario[F, StudentReadDomain],
        value => Scenario.pure(value): Scenario[F, StudentReadDomain]
      )
    } yield student
  }

  private def parseRedisInScenario[M: Decoder](key: String): Scenario[F, M] = {
    for {
      value <- Scenario.eval(redisCommands.get(key))
      flow   = parseRedisData[M](value)
      res <- flow.fold(
        error => Scenario.raiseError(error): Scenario[F, M],
        value => Scenario.pure(value): Scenario[F, M]
      )
    } yield res
  }

  override def addToQueueScenario: Scenario[F, Unit] = {
    val res = for {
      msg          <- Scenario.expect(textMessage.containing("Записаться в очередь"))
      chat          = msg.chat
      student      <- answerForAuthorization(msg.from)
      queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
      queueSeries <- queueSeriesE.fold(
        error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
        value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
      )
      flow = AddToQueueFlow(studentId = student.userId.some)
      key  = msg.from.get.username.get + flow.value
      kb = buildInlineKeyboard[QueueSeries](
        queueSeries,
        qs => qs.name,
        qs => CallbackAnswer(flow.value, 1, qs.id.toString)
      )
      rm       = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записаться!", keyboard = kb)
      _       <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
      _       <- Scenario.eval(replyMsg(chat, rm))
      _       <- Scenario.eval(semaphores("addToQueue").acquire)
      addFlow <- parseRedisInScenario[AddToQueueFlow](key)
      // TODO add correct date parsing
      dateMsg <- Scenario
        .expect(textMessage.matching(dateRegex))
        .tolerateN(3)(msg => msg.chat.send("Попробуй снова ввести дату.").void)
      optionFlow      = addFlow.copy(date = new Date(dateMsg.text).some)
      addToQueueOptKb = buildAddToQueueOptionsKeyboard(optionFlow)
      rm              = ReplyMessage("Отлично, выбери как ты хочешь записаться в очередь!", keyboard = addToQueueOptKb)
      _              <- Scenario.eval(redisCommands.set(key, optionFlow.asJson.toString()))
      _              <- Scenario.eval(replyMsg(chat, rm))
    } yield ()
    handleErrorInScenario(res)
  }

  private def getDateForQueue(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value <- redisCommands.get(key)
      flow  <- me.fromEither(parseRedisData[AddToQueueFlow](value))
      _     <- checkForAuthorization(query.query.from.some)
      rm = ReplyMessage(
        s"Отлично, ты выбрал очередь. Теперь напиши дату, на которую хочешь записаться в формате dd/MM/yyyy"
      )
      nextFlow = flow.copy(queueSeriesId = query.answer.value.toInt.some)
      _       <- redisCommands.set(key, nextFlow.asJson.toString())
      _       <- query.query.message.traverse(_.delete)
      _       <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _       <- semaphores("addToQueue").release
      _       <- query.query.finish
    } yield ()
  }

  private def choosePlace(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value   <- redisCommands.get(key)
      flow    <- me.fromEither(parseRedisData[AddToQueueFlow](value))
      _       <- checkForAuthorization(query.query.from.some)
      placesE <- queueService.getAvailablePlaces(flow.studentId.get, flow.queueSeriesId.get, flow.date.get)
      places  <- me.fromEither(placesE)
      kb = buildInlineKeyboard[Int](
        places,
        place => place.toString,
        place => query.answer.copy(step = query.answer.step + 1, value = place.toString)
      )
      rm = ReplyMessage("Отлично, выбери номер свободного места", keyboard = kb)
      _ <- query.query.message.traverse(_.delete)
      _ <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _ <- query.query.finish
    } yield ()
  }

  private def addToQueue(query: CallbackQueryExt, isTakePlace: Boolean): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value <- redisCommands.get(key)
      flow  <- me.fromEither(parseRedisData[AddToQueueFlow](value))
      _     <- checkForAuthorization(query.query.from.some)
      opt    = if (isTakePlace) AddToQueueOption.TakePlace else AddToQueueOption.withName(query.answer.value)
      place  = if (isTakePlace) query.answer.value.toIntOption else None
      resE <- queueService.addToQueue(
        flow.studentId.get,
        flow.queueSeriesId.get,
        flow.date.get,
        opt,
        place
      )
      place <- me.fromEither(resE)
      rm     = ReplyMessage(s"Отлично, твое место $place")
      _     <- query.query.message.traverse(_.delete)
      _     <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _     <- query.query.finish
    } yield ()
  }

  private def addToQueueFlow(): PartialFunction[CallbackQueryExt, F[Unit]] = {
    case query if query.answer.flowId == 2 =>
      query.answer.step match {
        case 1 => getDateForQueue(query)
        case 2 => choosePlace(query)
        case 3 => addToQueue(query, isTakePlace = true)
        case 4 => addToQueue(query, isTakePlace = false)
      }
  }

  //  override def addFriendToQueue: Scenario[F, Unit] = ???
//
//  override def takeAnotherPlace: Scenario[F, Unit] = ???
//
//  override def removeFromQueue: Scenario[F, Unit] = ???
//
//  override def getQueueSeries: Scenario[F, Unit] = ???
//
//  override def getQueue: Scenario[F, Unit] = ???

  override def answers: Seq[PartialFunction[CallbackQueryExt, F[Unit]]] = Seq(addToQueueFlow())
}
