package scenarios.impl

import canoe.api.TelegramClient
import canoe.api.models.Keyboard
import canoe.methods.files.GetFile
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.MonadError
import core.Scenario
import core.streaming.TelegramClientStreaming
import logger.LogHandler
import scenarios.AdminScenarios
import syntax.syntax.{command, documentMessage}
import util.MarshallingUtil.{defaultMsgAnswer, scenario, sendMessage}
import util.bundle.ResourceBundleUtil

class AdminScenariosImpl[F[_]: TelegramClient](
  bundleUtil: ResourceBundleUtil
)(
  implicit me: MonadError[F, Throwable],
  streamClient : TelegramClientStreaming[F],
  logHandler:  LogHandler[F]
) extends AdminScenarios[F] {
  override def addGroupScenario: Scenario[F, Unit] = scenario(command("get_file"), bundleUtil) { msg =>
    for {
      _        <- sendMessage(defaultMsgAnswer[F, TextMessage](msg), "get file scenario", Keyboard.Unchanged)
      document <- Scenario.expect(documentMessage)
      file     <- Scenario.eval(GetFile(document.document.fileId).call)
      _         = println(file.filePath)
    } yield ()
  }

  override def addQueuesScenario: Scenario[F, Unit] = ???
}
