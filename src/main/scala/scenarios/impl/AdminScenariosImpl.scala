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
import constants.DEFAULT_LANG
import domain.user.{Role, StudentCreateDomain}
import error.impl.admin.NotEnoughPermissions
import fs2._
import implicits.bot.containingWithBundle
import info.fingo.spata.{CSVParser, Decoded}
import org.typelevel.log4cats.Logger
import scenarios.AdminScenarios
import service.StudentService
import util.MarshallingUtil.{defaultMsgAnswer, scenario, sendMessage}
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension.StringExtension

class AdminScenariosImpl[F[_]: TelegramClient: Monad: Sync](
  studentService: StudentService[F],
  bundleUtil:     ResourceBundleUtil
)(
  implicit logger: Logger[F]
) extends AdminScenarios[F] {
  override def addGroupScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.admin.add_group", bundleUtil), bundleUtil) { msg =>
      for {
        admin <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        _ <- if (admin.role != Role.Admin)
          Scenario.raiseError(NotEnoughPermissions(NonEmptyList.of(Role.Admin)))
        flow_name = "add_group"
        bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.download_file"),
          Keyboard.Unchanged
        )
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
            .pure
        )
        _ <- Scenario.eval(studentService.addGroup(students))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          Keyboard.Unchanged
        )
      } yield ()
    }

  override def addQueuesScenario: Scenario[F, Unit] = ???
}
