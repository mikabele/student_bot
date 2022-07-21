package scenarios

import domain.callback.CallbackQueryExt

trait CallbackAnswerHandler[F[_]] {
  def answers: Seq[PartialFunction[CallbackQueryExt, F[Unit]]]
}
