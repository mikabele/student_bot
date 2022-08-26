package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{Scenario, TelegramClient}
import canoe.methods.files.GetFile
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.Monad
import constants.DEFAULT_LANG
import implicits.bot.containingWithBundle
import org.typelevel.log4cats.Logger
import scenarios.AdminScenarios
import service.StudentService
import util.MarshallingUtil.{defaultMsgAnswer, scenario, sendMessage}
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension.StringExtension

class AdminScenariosImpl[F[_]: TelegramClient:Monad](
  studentService: StudentService[F],
  bundleUtil:     ResourceBundleUtil
)(implicit logger: Logger[F]) extends AdminScenarios[F] {
  override def addGroupScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.admin.add_group", bundleUtil), bundleUtil) { msg =>
      for {
        admin    <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name = "add_group"
        bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          Keyboard.Unchanged
        )
        document <- Scenario.expect(documentMessage)
        file     <- Scenario.eval(GetFile(document.document.fileId).call)
        //data <- Scenario.eval(implicitly[TelegramClient[F]].downloadFile(file.filePath.get))
      } yield ()
    }

  override def addQueuesScenario: Scenario[F, Unit] = ???
}
