package scenarios.impl

import canoe.api.TelegramClient
import canoe.api.models.Keyboard
import canoe.models._
import canoe.models.messages.TextMessage
import canoe.syntax.textContent
import cats.MonadError
import constants._
import core.Scenario
import error.impl.auth._
import logger.LogHandler
import scenarios.AuthScenarios
import service.StudentService
import syntax.syntax.command
import util.MarshallingUtil._
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension._

import scala.language.implicitConversions

class AuthScenariosImpl[F[_]: TelegramClient](
  authService: StudentService[F],
  bundleUtil:  ResourceBundleUtil
)(
  implicit me: MonadError[F, Throwable],
  logHandler:  LogHandler[F]
) extends AuthScenarios[F] {

  private def checkNonAuthorizedUser(user: Option[User]): Scenario[F, Unit] = {
    for {
      studentE <- Scenario.eval(authService.checkAuthUser(user))
      _ <- studentE.fold(
        {
          case NonAuthorizedUser => Scenario.done:              Scenario[F, Unit]
          case error             => Scenario.raiseError(error): Scenario[F, Unit]
        },
        _ => Scenario.raiseError(AlreadyAuthorizedUser: Throwable): Scenario[F, Unit]
      )
    } yield ()
  }

  override def startBotScenario: Scenario[F, Unit] = scenario(command("start"), bundleUtil) { msg =>
    for {
      _            <- checkNonAuthorizedUser(msg.from)
      flow_name     = "start"
      bundle        = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
      universities <- Scenario.eval(authService.getUniversities)
      query1 <- sendMessageWithCallback(
        defaultMsgAnswer[F, TextMessage](msg),
        bundle.getFormattedString(s"flow.${flow_name}.msg.universities"),
        universitiesInlineKeyboard(universities)
      )
      university = query1.data.get
      courses   <- Scenario.eval(authService.getCourses(university))

      query2 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query1),
        bundle.getFormattedString(s"flow.${flow_name}.msg.courses"),
        coursesInlineKeyboard(courses)
      )
      course  = query2.data.get.toInt
      groups <- Scenario.eval(authService.getGroups(university, course))
      query3 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query2),
        bundle.getFormattedString(s"flow.${flow_name}.msg.groups"),
        groupsInlineKeyboard(groups)
      )
      group     = query3.data.get.toInt
      students <- Scenario.eval(authService.getStudents(university, course, group))
      query4 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query3),
        bundle.getFormattedString(s"flow.${flow_name}.msg.students"),
        studentsInlineKeyboard(students.filter(_.tgUserId.isEmpty))
      )
      studentId = query4.data.get.toInt
      _        <- Scenario.fromEitherF(authService.registerUser(studentId, query4.from))
      _ <- sendMessage(
        defaultCallbackAnswer[F, TextMessage](query4),
        bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
        mainMenuKeyboard(bundle)
      )

    } yield ()
  }

  override def signOutScenario: Scenario[F, Unit] = scenario(command("exit"), bundleUtil) { msg =>
    for {
      student  <- Scenario.fromEitherF(authService.checkAuthUser(msg.from))
      flow_name = "sign_out"
      bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
      _        <- Scenario.eval(authService.signOut(student))
      _ <- sendMessage(
        defaultMsgAnswer[F, TextMessage](msg),
        bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
        Keyboard.Remove
      )
    } yield ()
  }
}
