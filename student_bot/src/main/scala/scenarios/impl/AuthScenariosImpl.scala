package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{callbackQueryApi, messageApi, Scenario, TelegramClient}
import canoe.models._
import canoe.syntax._
import cats.syntax.all._
import cats.{Monad, MonadError}
import dev.profunktor.redis4cats.RedisCommands
import domain.callback.Flow.AuthFlow
import domain.callback.{CallbackAnswer, CallbackQueryExt}
import domain.message.ReplyMessage
import domain.user.StudentReadDomain
import error.impl.auth._
import io.circe.generic.auto._
import io.circe.syntax._
import logger.LogHandler
import scenarios.AuthScenarios
import service.AuthService
import util.DecodingUtil.parseRedisData
import util.MarshallingUtil._
import util.TelegramElementBuilder._

class AuthScenariosImpl[F[_]: TelegramClient](
  redisCommands: RedisCommands[F, String, String],
  authService:   AuthService[F]
)(
  implicit me: MonadError[F, Throwable],
  logHandler:  LogHandler[F]
) extends AuthScenarios[F] {

  private def checkForNonAuthorization(user: Option[User]): F[Unit] = {
    for {
      studentE <- authService.checkAuthUser(user)
      _ <- studentE.fold(
        error =>
          error match {
            case NonAuthorizedUser => Monad[F].unit:        F[Unit]
            case _                 => me.raiseError(error): F[Unit]
          },
        _ => me.raiseError(AlreadyAuthorizedUser): F[Unit]
      )
    } yield ()
  }

  //TODO remove duplicated code
  private def answerForNonAuthorization(user: Option[User], chat: Chat): Scenario[F, Unit] = {
    for {
      studentE <- Scenario.eval(authService.checkAuthUser(user))
      _ <- studentE.fold(
        error =>
          error match {
            case NonAuthorizedUser => Scenario.done: Scenario[F, Unit]
            case _ =>
              val rm = ReplyMessage(error.getMessage, keyboard = Keyboard.Unchanged)
              for {
                _ <- Scenario.eval(replyMsg(chat, rm))
                _ <- Scenario.raiseError(error): Scenario[F, Unit]
              } yield ()
          },
        _ => {
          val rm = ReplyMessage(AlreadyAuthorizedUser.getMessage, keyboard = Keyboard.Unchanged)
          for {
            _ <- Scenario.eval(replyMsg(chat, rm))
            _ <- Scenario.raiseError(AlreadyAuthorizedUser): Scenario[F, Unit]
          } yield ()
        }
      )
    } yield ()
  }

  override def startBotScenario: Scenario[F, Unit] = {
    scenario(command("start")) { msg =>
      {
        val chat = msg.chat
        for {
          _       <- answerForNonAuthorization(msg.from, chat)
          courses <- Scenario.eval(authService.getCourses)
          flow     = AuthFlow()
          key      = msg.from.get.username.get + flow.value
          kb = buildInlineKeyboard[Int](
            courses,
            course => course.toString,
            course => CallbackAnswer(flow.value, 1, course.toString)
          )
          rm = ReplyMessage(
            "Привет, студент. Я Бот, могу помочь тебе с некоторыми штучками. Но сначала мне надо знать, кто ты. Выбери, пожалуйста, номер курса.",
            keyboard = kb
          )
          _ <- Scenario.eval(redisCommands.set(key, flow.asJson.toString()))
          _ <- Scenario.eval(replyMsg(chat, rm))
        } yield ()
      }
    }
  }

  private def getStudentsAnswer(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value    <- redisCommands.get(key)
      flow     <- me.fromEither(parseRedisData[AuthFlow](value))
      group     = query.answer.value.toInt
      _        <- checkForNonAuthorization(query.query.from.some)
      students <- authService.getStudents(flow.course.get, group)
      nextFlow  = flow.copy(course = group.some)
      kb = buildInlineKeyboard[StudentReadDomain](
        students,
        student => student.lastName + " " + student.firstName,
        student => query.answer.copy(step = query.answer.step + 1, value = student.userId.toString)
      )
      rm = ReplyMessage("Круто, давай найдем, кто ты из списка.", keyboard = kb)
      _ <- redisCommands.set(key, nextFlow.asJson.toString())
      _ <- query.query.message.traverse(_.delete)
      _ <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _ <- query.query.finish
    } yield ()
  }

  private def getGroupsAnswer(query: CallbackQueryExt): F[Unit] = {
    val key = query.query.from.username.get + query.answer.flowId
    for {
      value   <- redisCommands.get(key)
      flow    <- me.fromEither(parseRedisData[AuthFlow](value))
      course   = query.answer.value.toInt
      _       <- checkForNonAuthorization(query.query.from.some)
      groups  <- authService.getGroups(course)
      nextFlow = flow.copy(course = course.some)
      kb = buildInlineKeyboard[Int](
        groups,
        group => group.toString,
        group => query.answer.copy(step = query.answer.step + 1, value = group.toString)
      )
      rm = ReplyMessage("Круто, давай выберем группу", keyboard = kb)
      _ <- redisCommands.set(key, nextFlow.asJson.toString())
      _ <- query.query.message.traverse(_.delete)
      _ <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
      _ <- query.query.finish
    } yield ()
  }

  private def registerUser(query: CallbackQueryExt): F[Unit] = {
    val studentId = query.answer.value.toInt
    for {
      _   <- checkForNonAuthorization(query.query.from.some)
      res <- authService.registerUser(studentId, query.query.from)
      _   <- me.fromEither(res)
      rm   = ReplyMessage("Ура, теперь я знаю как тебя зовут и я полностью готов к работе")
      _   <- query.query.message.traverse(_.delete)
      _   <- query.query.message.traverse(msg => replyMsg(msg.chat, rm))
    } yield ()
  }

  private def authFlow: PartialFunction[CallbackQueryExt, F[Unit]] = {
    case query if query.answer.flowId == 1 =>
      query.answer.step match {
        case 1 => getGroupsAnswer(query)
        case 2 => getStudentsAnswer(query)
        case 3 => registerUser(query)
      }
  }

  override def answers: Seq[PartialFunction[CallbackQueryExt, F[Unit]]] =
    Seq(authFlow)

  //TODO implement method
  override def signOutScenario: Scenario[F, Unit] = {
    Scenario.done
  }
}
