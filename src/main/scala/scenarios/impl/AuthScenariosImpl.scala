package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{Scenario, TelegramClient}
import canoe.models._
import canoe.models.messages.TextMessage
import canoe.syntax.{command, textContent}
import cats.Monad
import constants._
import error.impl.auth._
import org.typelevel.log4cats.Logger
import scenarios.AuthScenarios
import service.StudentService
import util.MarshallingUtil._
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension._

import scala.language.implicitConversions

class AuthScenariosImpl[F[_]: TelegramClient: Monad](
  studentService: StudentService[F],
  bundleUtil:     ResourceBundleUtil
)(
  implicit logger: Logger[F]
) extends AuthScenarios[F] {

  private def checkNonAuthorizedUser(
    user:   Option[User],
  ): Scenario[F, Unit] = {
    for {
      studentE <- Scenario.eval(studentService.checkAuthUser(user))
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
    val bundle = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
    for {
      _            <- checkNonAuthorizedUser( msg.from)
      flow_name     = "start"
      universities <- Scenario.eval(studentService.getUniversities)
      query1 <- sendMessageWithCallback(
        defaultMsgAnswer[F, TextMessage](msg),
        bundle.getFormattedString(s"flow.${flow_name}.msg.universities"),
        universitiesInlineKeyboard(universities)
      )
      university = query1.data.get
      courses   <- Scenario.eval(studentService.getCourses(university))

      query2 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query1),
        bundle.getFormattedString(s"flow.${flow_name}.msg.courses"),
        coursesInlineKeyboard(courses)
      )
      course  = query2.data.get.toInt
      groups <- Scenario.eval(studentService.getGroups(university, course))
      query3 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query2),
        bundle.getFormattedString(s"flow.${flow_name}.msg.groups"),
        groupsInlineKeyboard(groups)
      )
      group     = query3.data.get.toInt
      students <- Scenario.eval(studentService.getStudents(university, course, group))
      query4 <- sendMessageWithCallback(
        defaultCallbackAnswer[F, TextMessage](query3),
        bundle.getFormattedString(s"flow.${flow_name}.msg.students"),
        studentsInlineKeyboard(students.filter(_.tgUserId.isEmpty))
      )
      studentId = query4.data.get.toInt
      _        <- Scenario.fromEitherF(studentService.registerUser(studentId, query4.from))
      student   = students.find(_.userId == studentId).get
      _ <- sendMessage(
        defaultCallbackAnswer[F, TextMessage](query4),
        bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
        userMenuKeyboard(bundle)
      )

    } yield ()
  }

  override def signOutScenario: Scenario[F, Unit] = scenario(command("exit"), bundleUtil) { msg =>
    for {
      student  <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
      flow_name = "sign_out"
      bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
      _        <- Scenario.eval(studentService.signOut(student))
      _ <- sendMessage(
        defaultMsgAnswer[F, TextMessage](msg),
        bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
        Keyboard.Remove
      )
    } yield ()
  }
}
