package scenarios

import canoe.models.CallbackQuery

trait CallbackAnswerHandler[F[_]] {
  def answers: Seq[PartialFunction[CallbackQuery, F[Unit]]]
}
