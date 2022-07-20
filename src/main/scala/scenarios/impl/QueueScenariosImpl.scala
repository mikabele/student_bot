package scenarios.impl

import canoe.api.{callbackQueryApi, TelegramClient}
import canoe.methods.messages.SendMessage
import canoe.models.User
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.MonadError
import cats.effect.Concurrent
import cats.syntax.all._
import core._
import dev.profunktor.redis4cats.RedisCommands
import domain.message.ReplyMessage
import domain.queue.{AddToQueueOption, Queue, QueueSeries}
import domain.user.StudentReadDomain
import implicits.circe._
import logger.LogHandler
import scenarios.QueueScenarios
import service.{QueueService, StudentService}
import syntax.syntax.{callback, containing}
import util.MarshallingUtil.{replyMsg, scenario}
import util.TelegramElementBuilder.{buildAddToQueueOptionsKeyboard, buildInlineKeyboard}

import java.text.SimpleDateFormat
import scala.util.Try

class QueueScenariosImpl[F[_]: TelegramClient: Concurrent](
  redisCommands: RedisCommands[F, String, String],
  queueService:  QueueService[F],
  authService:   StudentService[F]
)(
  implicit me: MonadError[F, Throwable],
  logHandler:  LogHandler[F]
) extends QueueScenarios[F] {

  private def answerForAuthorization(user: Option[User]): Scenario[F, StudentReadDomain] = {
    for {
      studentE <- Scenario.eval(authService.checkAuthUser(user))
      student <- studentE.fold(
        error => Scenario.raiseError(error): Scenario[F, StudentReadDomain],
        value => Scenario.pure(value): Scenario[F, StudentReadDomain]
      )
    } yield student
  }

  private val sdf = new SimpleDateFormat("dd/MM/yyyy")
  sdf.setLenient(false)

  private def dateValidation(textMessage: syntax.syntax.Expect[TextMessage]): syntax.syntax.Expect[TextMessage] = {
    textMessage.when(msg => Try(sdf.parse(msg.text)).fold(_ => false, _ => true))
  }

  override def addToQueueScenario: Scenario[F, Unit] = {
    scenario(containing("Записаться в очередь")) { msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _ <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        kb = buildInlineKeyboard[QueueSeries](
          queueSeries,
          qs => qs.name,
          qs => qs.id.toString
        )
        rm      = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записаться!", keyboard = kb)
        msg1   <- Scenario.eval(replyMsg(chat, rm))
        query1 <- Scenario.expect(callback(msg1.some))
        qsId    = query1.data.get.toInt
        _      <- Scenario.eval(logHandler.info(s"Send message to user"))
        rm = ReplyMessage(
          s"Отлично, ты выбрал очередь. Теперь напиши дату, на которую хочешь записаться в формате dd/MM/yyyy",
        )
        _ <- Scenario.eval(query1.message.traverse(msg => replyMsg(msg.chat, rm)))
        _ <- Scenario.eval(query1.finish)
        msg2 <- Scenario
          .expect(dateValidation(syntax.syntax.textMessage))
        date            = sdf.parse(msg2.text)
        addToQueueOptKb = buildAddToQueueOptionsKeyboard
        rm              = ReplyMessage("Отлично, выбери как ты хочешь записаться в очередь!", keyboard = addToQueueOptKb)
        msg3           <- Scenario.eval(replyMsg(chat, rm))
        query3         <- Scenario.expect(callback(msg3.some))
        option          = AddToQueueOption.withName(query3.data.get)
        place <- option match {
          case AddToQueueOption.TakePlace =>
            for {
              placesE <- Scenario.eval(queueService.getAvailablePlaces(student.userId, qsId, date))
              places <- placesE.fold(
                e => Scenario.raiseError(e): Scenario[F, List[Int]],
                value => Scenario.pure(value): Scenario[F, List[Int]]
              )
              kb = buildInlineKeyboard[Int](
                places,
                place => place.toString,
                place => place.toString
              )
              rm      = ReplyMessage("Отлично, выбери номер свободного места", keyboard = kb)
              msg4   <- Scenario.eval(query3.message.traverse(msg => replyMsg(msg.chat, rm)))
              _      <- Scenario.eval(query3.finish)
              query4 <- Scenario.expect(callback(msg4))
              place   = query4.data.get.toInt.some
              _      <- Scenario.eval(query4.finish)
            } yield place
          case _ =>
            for {
              _ <- Scenario.eval(query3.finish)
            } yield Option.empty[Int]
        }
        resE <- Scenario.eval(
          queueService.addToQueue(student.userId, qsId, date, option, place)
        )
        place <- resE.fold(
          e => Scenario.raiseError(e): Scenario[F, Int],
          value => Scenario.pure(value): Scenario[F, Int]
        )
        rm = ReplyMessage(s"Отлично, твое место $place")
        _ <- Scenario.eval(replyMsg(chat, rm))
      } yield ()
    }

  }

  override def getQueueSeries: Scenario[F, Unit] = {
    scenario(containing("Посмотреть список очередей")) { msg =>
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
    scenario(containing("Посмотреть очередь")) { msg =>
      for {
        student      <- answerForAuthorization(msg.from)
        _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
        chat          = msg.chat
        queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
        queueSeries <- queueSeriesE.fold(
          error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
          value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
        )
        _ <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
        kb = buildInlineKeyboard[QueueSeries](
          queueSeries,
          qs => qs.name,
          qs => qs.id.toString
        )
        rm      = ReplyMessage("Отлично, выбери какую очередь ты хочешь посмотреть!", keyboard = kb)
        msg1   <- Scenario.eval(replyMsg(chat, rm))
        _      <- Scenario.eval(logHandler.info(s"Send message to user"))
        query1 <- Scenario.expect(callback(msg1.some))
        qsId    = query1.data.get.toInt
        _      <- Scenario.eval(logHandler.info(s"Send message to user"))
        rm = ReplyMessage(
          s"Отлично, ты выбрал очередь. Теперь напиши дату, на которую хочешь записаться в формате dd/MM/yyyy",
        )
        _ <- Scenario.eval(query1.message.traverse(msg => replyMsg(msg.chat, rm)))
        _ <- Scenario.eval(query1.finish)
        msg2 <- Scenario
          .expect(dateValidation(syntax.syntax.textMessage))
        date = sdf.parse(msg2.text)
        queueE <- Scenario.eval(
          queueService.getQueue(qsId, date)
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

  override def addFriendToQueue: Scenario[F, Unit] = scenario(containing("Записать друга в очередь")) { msg =>
    for {
      _       <- answerForAuthorization(msg.from)
      courses <- Scenario.eval(authService.getCourses)
      chat     = msg.chat
      kb       = buildInlineKeyboard[Int](courses, c => c.toString, c => c.toString)
      rm = ReplyMessage(
        "Давай найдем твоего друга. Выбери курс, на котором учится твой друг.",
        keyboard = kb
      )
      msg1     <- Scenario.eval(replyMsg(msg.chat, rm))
      query1   <- Scenario.expect(callback(msg1.some))
      course    = query1.data.get.toInt
      groups   <- Scenario.eval(authService.getGroups(course))
      kb        = buildInlineKeyboard[Int](groups, g => g.toString, g => g.toString)
      rm        = ReplyMessage("Круто, давай выберем группу", keyboard = kb)
      msg2     <- Scenario.eval { query1.message.traverse(m => replyMsg(m.chat, rm)) }
      _        <- Scenario.eval(query1.finish)
      query2   <- Scenario.expect(callback(msg2))
      group     = query2.data.get.toInt
      students <- Scenario.eval(authService.getAuthorizedStudents(course, group))
      kb        = buildInlineKeyboard[StudentReadDomain](students, s => s.lastName + " " + s.firstName, s => s.userId.toString)
      rm = ReplyMessage(
        "Круто, давай найдем твоего друга в списке. Учти, что если я еще не знаком с твоим другом, то его не будет в списке.",
        keyboard = kb
      )
      msg3         <- Scenario.eval(query2.message.traverse(msg => replyMsg(msg.chat, rm)))
      _            <- Scenario.eval(query2.finish)
      query3       <- Scenario.expect(callback(msg3))
      studentId     = query3.data.get.toInt
      queueSeriesE <- Scenario.eval(queueService.getQueueSeries(studentId))
      queueSeries <- queueSeriesE.fold(
        error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
        value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
      )
      _ <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
      kb = buildInlineKeyboard[QueueSeries](
        queueSeries,
        qs => qs.name,
        qs => qs.id.toString
      )
      rm      = ReplyMessage("Отлично, выбери в какую очередь ты хочешь записать друга!", keyboard = kb)
      msg1   <- Scenario.eval(replyMsg(chat, rm))
      query1 <- Scenario.expect(callback(msg1.some))
      qsId    = query1.data.get.toInt
      _      <- Scenario.eval(logHandler.info(s"Send message to user"))
      rm = ReplyMessage(
        s"Отлично, ты выбрал очередь. Теперь напиши дату, на которую хочешь записать друга в формате dd/MM/yyyy",
      )
      _ <- Scenario.eval(query1.message.traverse(msg => replyMsg(msg.chat, rm)))
      _ <- Scenario.eval(query1.finish)
      msg2 <- Scenario
        .expect(dateValidation(syntax.syntax.textMessage))
      date            = sdf.parse(msg2.text)
      addToQueueOptKb = buildAddToQueueOptionsKeyboard
      rm              = ReplyMessage("Отлично, выбери как ты хочешь записать друга в очередь!", keyboard = addToQueueOptKb)
      msg3           <- Scenario.eval(replyMsg(chat, rm))
      query3         <- Scenario.expect(callback(msg3.some))
      option          = AddToQueueOption.withName(query3.data.get)
      place <- option match {
        case AddToQueueOption.TakePlace =>
          for {
            placesE <- Scenario.eval(queueService.getAvailablePlaces(studentId, qsId, date))
            places <- placesE.fold(
              e => Scenario.raiseError(e): Scenario[F, List[Int]],
              value => Scenario.pure(value): Scenario[F, List[Int]]
            )
            kb = buildInlineKeyboard[Int](
              places,
              place => place.toString,
              place => place.toString
            )
            rm      = ReplyMessage("Отлично, выбери номер свободного места", keyboard = kb)
            msg4   <- Scenario.eval(query3.message.traverse(msg => replyMsg(msg.chat, rm)))
            _      <- Scenario.eval(query3.finish)
            query4 <- Scenario.expect(callback(msg4))
            place   = query4.data.get.toInt.some
            _      <- Scenario.eval(query4.finish)
          } yield place
        case _ =>
          for {
            _ <- Scenario.eval(query3.finish)
          } yield Option.empty[Int]
      }
      resE <- Scenario.eval(
        queueService.addToQueue(studentId, qsId, date, option, place)
      )
      place <- resE.fold(
        e => Scenario.raiseError(e): Scenario[F, Int],
        value => Scenario.pure(value): Scenario[F, Int]
      )
      rm      = ReplyMessage(s"Отлично, место твоего друга $place")
      _      <- Scenario.eval(replyMsg(chat, rm))
      student = students.find(_.userId == studentId).get
      queue   = queueSeries.find(_.id == qsId).get
      _ <- Scenario.eval(
        SendMessage(
          student.tgUserId.get,
          s"Тебя записали в очередь ${queue.name} на дату $date. Твое место $place",
        ).call
      )
    } yield ()
  }

  override def removeFromQueue: Scenario[F, Unit] = scenario(containing("Выписаться из очереди")) { msg =>
    for {
      student      <- answerForAuthorization(msg.from)
      _            <- Scenario.eval(logHandler.info(s"User is authorized - ${student.userId}"))
      chat          = msg.chat
      queueSeriesE <- Scenario.eval(queueService.getQueueSeries(student.userId))
      queueSeries <- queueSeriesE.fold(
        error => Scenario.raiseError(error): Scenario[F, List[QueueSeries]],
        value => Scenario.pure(value): Scenario[F, List[QueueSeries]]
      )
      _ <- Scenario.eval(logHandler.info(s"Found several queue series - $queueSeries"))
      kb = buildInlineKeyboard[QueueSeries](
        queueSeries,
        qs => qs.name,
        qs => qs.id.toString
      )
      rm      = ReplyMessage("Отлично, выбери очерель, из которой хочешь выписаться!", keyboard = kb)
      msg1   <- Scenario.eval(replyMsg(chat, rm))
      query1 <- Scenario.expect(callback(msg1.some))
      qsId    = query1.data.get.toInt
      _      <- Scenario.eval(logHandler.info(s"Send message to user"))
      rm = ReplyMessage(
        s"Отлично, ты выбрал очередь. Теперь напиши дату в формате dd/MM/yyyy",
      )
      _ <- Scenario.eval(query1.message.traverse(msg => replyMsg(msg.chat, rm)))
      _ <- Scenario.eval(query1.finish)
      msg2 <- Scenario
        .expect(dateValidation(syntax.syntax.textMessage))
      date = sdf.parse(msg2.text)
      res <- Scenario.eval(queueService.removeFromQueue(qsId, date))
      rm   = ReplyMessage("Я выписал тебя из очереди!")
      _ <- res.fold(
        error => Scenario.raiseError(error): Scenario[F, Unit],
        _ => Scenario.eval(replyMsg(chat, rm).void): Scenario[F, Unit]
      )
    } yield ()
  }
}
