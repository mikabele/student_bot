package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{Scenario, TelegramClient}
import canoe.methods.files.GetFile
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.Monad
import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.all._
import constants.{DEFAULT_LANG, adminMenuKeyboard}
import domain.queue.QueueSeriesCreateDomain
import domain.user.{Role, StudentCreateDomain}
import error.impl.admin.{NotEnoughPermissions, ParseFailure}
import fs2._
import implicits.bot.containingWithBundle
import info.fingo.spata.{CSVParser, Decoded}
import org.typelevel.log4cats.Logger
import scenarios.AdminScenarios
import service.{QueueService, StudentService}
import util.MarshallingUtil.{defaultMsgAnswer, scenario, sendMessage}
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension.StringExtension

//TODO change logic - add different command /admin
//TODO remove resource bundle from menu buttons
class AdminScenariosImpl[F[_]: TelegramClient: Monad: Sync](
  studentService: StudentService[F],
  queueService: QueueService[F],
  bundleUtil:     ResourceBundleUtil
)(
  implicit logger: Logger[F]
) extends AdminScenarios[F] {

  override def initAdminMenuScenario: Scenario[F, Unit] = {
    scenario(command("admin"), bundleUtil) { msg =>
      for {
        admin <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        _ <-
          if (admin.role != Role.Admin)
            Scenario.raiseError(NotEnoughPermissions(NonEmptyList.of(Role.Admin))): Scenario[F, Unit]
          else Scenario.pure(()):                                                   Scenario[F, Unit]

        flow_name = "init_admin"
        bundle = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          adminMenuKeyboard(bundle)
        ): Scenario[F, Option[TextMessage]]
      } yield ()
    }
  }

  override def addGroupScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.admin.add_group", bundleUtil), bundleUtil) { msg =>
      for {
        admin <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        _ <-
          if (admin.role != Role.Admin)
            Scenario.raiseError(NotEnoughPermissions(NonEmptyList.of(Role.Admin))): Scenario[F, Unit]
          else Scenario.pure(()):                                                   Scenario[F, Unit]

        flow_name = "add_group"
        bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.download_file"),
          Keyboard.Unchanged
        ): Scenario[F, Option[TextMessage]]
        document <- Scenario.expect(documentMessage)
        file     <- Scenario.eval(GetFile(document.document.fileId).call)
        data <- Scenario.eval(
          implicitly[TelegramClient[F]]
            .downloadFile(file.filePath.get)
            .through(text.utf8.decode)
            .flatMap(str => Stream[F, Char](str.toCharArray.toSeq: _*))
            .through(CSVParser[F].parse)
            .map(_.to[StudentCreateDomain](): Decoded[StudentCreateDomain])
            .compile
            .toList
        )
        students <- Scenario.fromEitherF(
          data
            .traverse[Either[Throwable, *], StudentCreateDomain](student => student)
            .handleErrorWith(_ => ParseFailure.asLeft)
            .pure
        )
        _ <- Scenario.fromEitherF(studentService.addGroup(students))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          Keyboard.Unchanged
        )
      } yield ()
    }

  override def addQueuesScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.admin.add_queue_series", bundleUtil), bundleUtil) { msg =>
      for {
        admin <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        _ <-
          if (admin.role != Role.Admin)
            Scenario.raiseError(NotEnoughPermissions(NonEmptyList.of(Role.Admin))): Scenario[F, Unit]
          else Scenario.pure(()):                                                   Scenario[F, Unit]

        flow_name = "add_queue_series"
        bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.download_file"),
          Keyboard.Unchanged
        ): Scenario[F, Option[TextMessage]]
        document <- Scenario.expect(documentMessage)
        file     <- Scenario.eval(GetFile(document.document.fileId).call)
        data <- Scenario.eval(
          implicitly[TelegramClient[F]]
            .downloadFile(file.filePath.get)
            .through(text.utf8.decode)
            .flatMap(str => Stream[F, Char](str.toCharArray.toSeq: _*))
            .through(CSVParser[F].parse)
            .map(_.to[QueueSeriesCreateDomain](): Decoded[QueueSeriesCreateDomain])
            .compile
            .toList
        )
        queueSeries <- Scenario.fromEitherF(
          data
            .traverse[Either[Throwable, *], QueueSeriesCreateDomain](student => student)
            .handleErrorWith(_ => ParseFailure.asLeft)
            .pure
        )
        _ <- Scenario.fromEitherF(queueService.addQueueSeries(queueSeries))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          Keyboard.Unchanged
        )
      } yield ()
    }
}
