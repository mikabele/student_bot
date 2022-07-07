package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{Scenario, _}
import canoe.models.messages.TextMessage
import canoe.models.{ForceReply, ReplyMarkup, User}
import canoe.syntax._
import cats.MonadError
import cats.effect.Concurrent
import cats.syntax.all._
import dev.profunktor.redis4cats.RedisCommands
import domain.callback.Flow.{AddToQueueFlow, GetQueueFlow}
import domain.callback.{CallbackAnswer, CallbackQueryExt}
import domain.message.ReplyMessage
import domain.queue.{AddToQueueOption, Queue, QueueSeries}
import domain.user.StudentReadDomain
import implicits.circe._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.syntax._
import logger.LogHandler
import scenarios.QueueScenarios
import service.{AuthService, QueueService}
import util.DecodingUtil.parseRedisData
import util.MarshallingUtil.{replyMsg, scenario}
import util.TelegramElementBuilder.{buildAddToQueueOptionsKeyboard, buildInlineKeyboard}

import java.text.SimpleDateFormat
import scala.util.Try

class QueueScenariosImpl[F[_]: TelegramClient: Concurrent](
  redisCommands: RedisCommands[F, String, String],
  queueService:  QueueService[F],
  authService:   AuthService[F]
)(
  implicit me: MonadError[F, Throwable],
  logHandler:  LogHandler[F]
) extends QueueScenarios[F] {

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

  private val sdf = new SimpleDateFormat("dd/MM/yyyy")
  sdf.setLenient(false)

  private def dateValidation(textMessage: Expect[TextMessage]): Expect[TextMessage] = {
    textMessage.when(msg => Try(sdf.parse(msg.text)).fold(_ => false, _ => true))
  }

  override def addToQueueScenario: Scenario[F, Unit] = {
    scenario(textMessage.containing("Записаться в очередь")) { msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        detailedChat <- Scenario.eval(msg.chat.details)
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _   <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        flow = AddToQueueFlow(studentId = student.userId.some)
        key  = msg.from.get.username.get + flow.value
        kb = buildInlineKeyboard[QueueSeries](
          queueSeries,
          qs => qs.name,
          qs => CallbackAnswer(flow.value, 1, qs.id.toString)
        )
        rm   = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записаться!", keyboard = kb)
        _   <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
        _   <- Scenario.eval(logHandler.info(s"Send message to Redis - $flow"))
        res <- Scenario.eval(replyMsg(chat, rm))
        _    = println(res)
        _   <- Scenario.eval(logHandler.info(s"Send message to user"))
        dateMsg <- Scenario
          .expect(dateValidation(textMessage))
          .stopOn { m =>
            detailedChat.pinnedMessage match {
              case Some(value) => value.messageId != msg.messageId
              case None        => true
            }
          }
//          .tolerateAll(msg => {
//            for {
//              _ <- logHandler.info("Incorrect date input")
//              _ <- msg.chat.send("Попробуй снова ввести дату.")
//            } yield ()
//          })
        //_               = println(dateMsg.replyToMessage)
        addFlow        <- parseRedisInScenario[AddToQueueFlow](key)
        _              <- Scenario.eval(logHandler.info(s"Get message from Redis - $addFlow"))
        optionFlow      = addFlow.copy(date = sdf.parse(dateMsg.text).some)
        addToQueueOptKb = buildAddToQueueOptionsKeyboard(optionFlow)
        rm              = ReplyMessage("Отлично, выбери как ты хочешь записаться в очередь!", keyboard = addToQueueOptKb)
        _              <- Scenario.eval(redisCommands.set(key, optionFlow.asJson.toString()))
        _              <- Scenario.eval(replyMsg(chat, rm))
      } yield ()
    }
  }

  private def getDateForQueue(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value <- redisCommands.get(key)
      flow  <- me.fromEither(parseRedisData[AddToQueueFlow](value))
      _     <- checkForAuthorization(query.query.from.some)
      rm = ReplyMessage(
        s"Отлично, ты выбрал очередь. Теперь напиши дату, на которую хочешь записаться в формате dd/MM/yyyy",
        keyboard = new Keyboard {
          override def replyMarkup: Option[ReplyMarkup] = Some(ForceReply(forceReply = true))
        }
      )
      nextFlow = flow.copy(queueSeriesId = query.answer.value.toInt.some)
      _       <- redisCommands.set(key, nextFlow.asJson.toString())
      _       <- query.query.message.traverse(_.delete)
      _       <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
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

  override def answers: Seq[PartialFunction[CallbackQueryExt, F[Unit]]] = Seq(addToQueueFlow(), getQueueFlow)

  override def getQueueSeries: Scenario[F, Unit] = {
    scenario(textMessage.containing("Посмотреть список очередей")) { msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _    <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        qsStr = queueSeries.map(" - " |+| _.name).reduce(_ |+| "\n" |+| _)
        rm    = ReplyMessage("Вот список очередей, в которые ты можешь записаться!\n" + qsStr)
        _    <- Scenario.eval(replyMsg(chat, rm))
      } yield ()
    }
  }

  override def getQueue: Scenario[F, Unit] = {
    scenario(textMessage.containing("Посмотреть очередь")) { msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        detailedChat <- Scenario.eval(msg.chat.details)
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _   <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        flow = GetQueueFlow()
        key  = msg.from.get.username.get + flow.value
        kb = buildInlineKeyboard[QueueSeries](
          queueSeries,
          qs => qs.name,
          qs => CallbackAnswer(flow.value, 1, qs.id.toString)
        )
        rm = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записаться!", keyboard = kb)
        _ <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
        _ <- Scenario.eval(logHandler.info(s"Send message to Redis - $flow"))
        _ <- Scenario.eval(replyMsg(chat, rm))
        _ <- Scenario.eval(logHandler.info(s"Send message to user"))
        dateMsg <- Scenario
          .expect(dateValidation(textMessage))
          .stopOn { _ =>
            detailedChat.pinnedMessage match {
              case Some(value) => value.messageId != msg.messageId
              case None        => true
            }
          }
//          .tolerateAll(msg => {
//            for {
//              _ <- logHandler.info("Incorrect date input")
//              _ <- msg.chat.send("Попробуй снова ввести дату.")
//            } yield ()
//          })
        addFlow <- parseRedisInScenario[GetQueueFlow](key)
        _       <- Scenario.eval(logHandler.info(s"Get message from Redis - $addFlow"))
        queueE <- Scenario.eval(
          queueService.getQueue(addFlow.queueSeriesId.get, sdf.parse(dateMsg.text))
        )
        queue <- queueE.fold(
          error => Scenario.raiseError(error): Scenario[F, Queue],
          value => Scenario.pure(value): Scenario[F, Queue]
        )
        recordsStr = queue.records
          .map(record => s" - ${record.place}. ${record.student.lastName} ${record.student.firstName}\n")
          .fold("")(_ |+| _)
        rm = ReplyMessage("Вот список людей, которые сейчас записаны в очередь.\n" |+| recordsStr)
        _ <- Scenario.eval(replyMsg(chat, rm))
      } yield ()
    }
  }

  private def getDateForGetQueueFlow(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value <- redisCommands.get(key)
      flow  <- me.fromEither(parseRedisData[GetQueueFlow](value))
      _     <- checkForAuthorization(query.query.from.some)
      rm = ReplyMessage(
        s"Отлично, ты выбрал очередь. Теперь напиши дату в формате dd/MM/yyyy, на которую хочешь посмотреть очередь"
      )
      nextFlow = flow.copy(queueSeriesId = query.answer.value.toInt.some)
      _       <- redisCommands.set(key, nextFlow.asJson.toString())
      _       <- query.query.message.traverse(_.delete)
      _       <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _       <- query.query.finish
    } yield ()
  }

  private def getQueueFlow: PartialFunction[CallbackQueryExt, F[Unit]] = {
    case query if query.answer.flowId == 3 =>
      query.answer.step match {
        case 1 => getDateForGetQueueFlow(query)
      }
  }

  override def addFriendToQueue: Scenario[F, Unit] = scenario(textMessage.containing("Записать друга в очередь")) {
    msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        detailedChat <- Scenario.eval(msg.chat.details)
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _   <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        flow = AddToQueueFlow(studentId = student.userId.some)
        key  = msg.from.get.username.get + flow.value
        kb = buildInlineKeyboard[QueueSeries](
          queueSeries,
          qs => qs.name,
          qs => CallbackAnswer(flow.value, 1, qs.id.toString)
        )
        rm = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записаться!", keyboard = kb)
        _ <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
        _ <- Scenario.eval(logHandler.info(s"Send message to Redis - $flow"))
        _ <- Scenario.eval(replyMsg(chat, rm))
        _ <- Scenario.eval(logHandler.info(s"Send message to user"))
        dateMsg <- Scenario
          .expect(dateValidation(textMessage))
          .stopOn { m =>
            detailedChat.pinnedMessage match {
              case Some(value) => value.messageId != msg.messageId
              case None        => true
            }
          }
        //          .tolerateAll(msg => {
        //            for {
        //              _ <- logHandler.info("Incorrect date input")
        //              _ <- msg.chat.send("Попробуй снова ввести дату.")
        //            } yield ()
        //          })
        addFlow        <- parseRedisInScenario[AddToQueueFlow](key)
        _              <- Scenario.eval(logHandler.info(s"Get message from Redis - $addFlow"))
        optionFlow      = addFlow.copy(date = sdf.parse(dateMsg.text).some)
        addToQueueOptKb = buildAddToQueueOptionsKeyboard(optionFlow)
        rm              = ReplyMessage("Отлично, выбери как ты хочешь записаться в очередь!", keyboard = addToQueueOptKb)
        _              <- Scenario.eval(redisCommands.set(key, optionFlow.asJson.toString()))
        _              <- Scenario.eval(replyMsg(chat, rm))
      } yield ()
  }

//  def startBotScenario: Scenario[F, Unit] = {
//    scenario(command("start")) { msg =>
//      {
//        val chat = msg.chat
//        for {
//          _       <- answerForAuthorization(msg.from, chat)
//          courses <- Scenario.eval(authService.getCourses)
//          flow     = AuthFlow()
//          key      = msg.from.get.username.get + flow.value
//          kb = buildInlineKeyboard[Int](
//            courses,
//            course => course.toString,
//            course => CallbackAnswer(flow.value, 1, course.toString)
//          )
//          rm = ReplyMessage(
//            "Привет, студент. Я Бот, могу помочь тебе с некоторыми штучками. Но сначала мне надо знать, кто ты. Выбери, пожалуйста, номер курса.",
//            keyboard = kb
//          )
//          _ <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
//          _ <- Scenario.eval(replyMsg(chat, rm))
//        } yield ()
//      }
//    }
//  }
//
//  private def getStudentsAnswer(query: CallbackQueryExt): F[Unit] = {
//    val key = query.query.from.username.get + query.answer.flowId
//    for {
//      value    <- redisCommands.get(key)
//      flow     <- me.fromEither(parseRedisData[AuthFlow](value))
//      group     = query.answer.value.toInt
//      _        <- checkForAuthorization(query.query.from.some)
//      students <- authService.getStudents(flow.course.get, group)
//      nextFlow  = flow.copy(course = group.some)
//      kb = buildInlineKeyboard[StudentReadDomain](
//        students,
//        student => student.lastName + " " + student.firstName,
//        student => query.answer.copy(step = query.answer.step + 1, value = student.userId.toString)
//      )
//      rm = ReplyMessage("Круто, давай найдем, кто ты из списка.", keyboard = kb)
//      _ <- redisCommands.set(key, nextFlow.asJson.toString())
//      _ <- query.query.message.traverse(_.delete)
//      _ <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
//      _ <- query.query.finish
//    } yield ()
//  }
//
//  private def getGroupsAnswer(query: CallbackQueryExt): F[Unit] = {
//    val key = query.query.from.username.get + query.answer.flowId
//    for {
//      value   <- redisCommands.get(key)
//      flow    <- me.fromEither(parseRedisData[AuthFlow](value))
//      course   = query.answer.value.toInt
//      _       <- checkForAuthorization(query.query.from.some)
//      groups  <- authService.getGroups(course)
//      nextFlow = flow.copy(course = course.some)
//      kb = buildInlineKeyboard[Int](
//        groups,
//        group => group.toString,
//        group => query.answer.copy(step = query.answer.step + 1, value = group.toString)
//      )
//      rm = ReplyMessage("Круто, давай выберем группу", keyboard = kb)
//      _ <- redisCommands.set(key, nextFlow.asJson.toString())
//      _ <- query.query.message.traverse(_.delete)
//      _ <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
//      _ <- query.query.finish
//    } yield ()
//  }
}
