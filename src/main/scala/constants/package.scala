import canoe.api.models.Keyboard
import canoe.models.{InlineKeyboardButton, InlineKeyboardMarkup, KeyboardButton, ReplyKeyboardMarkup}
import cats.syntax.all._
import domain.queue.{AddToQueueOption, QueueSeriesReadDomain}
import domain.user.StudentReadDomain
import util.TelegramElementBuilder.buildInlineKeyboard
import util.bundle.StringFormatExtension._

import java.util.ResourceBundle

package object constants {
  def userMenuKeyboard(bundle: ResourceBundle): Keyboard.Reply = {
    val mainMenuBtns: Seq[Seq[KeyboardButton]] = Seq(
      Seq(
        KeyboardButton(bundle.getFormattedString("button.main.take_place")),
        KeyboardButton(bundle.getFormattedString("button.main.add_friend_place"))
      ),
      Seq(
        KeyboardButton(bundle.getFormattedString("button.main.update_place")),
        KeyboardButton(bundle.getFormattedString("button.main.remove_place"))
      ),
      Seq(
        KeyboardButton(bundle.getFormattedString("button.main.view_queue_series")),
        KeyboardButton(bundle.getFormattedString("button.main.view_queues"))
      )
    )
    val mainMenuMp: ReplyKeyboardMarkup = ReplyKeyboardMarkup(mainMenuBtns, resizeKeyboard = true.some)
    Keyboard.Reply(mainMenuMp)
  }

  def adminMenuKeyboard(bundle: ResourceBundle): Keyboard.Reply = {
    val btns: Seq[Seq[KeyboardButton]] = Seq(
      Seq(
        KeyboardButton(bundle.getFormattedString("button.admin.add_group")),
        KeyboardButton(bundle.getFormattedString("button.admin.add_queue_series"))
      )
    )
    val mainMenuMp: ReplyKeyboardMarkup = ReplyKeyboardMarkup(btns, resizeKeyboard = true.some)
    Keyboard.Reply(mainMenuMp)
  }


  val DEFAULT_LANG: String = "en"

  val DEFAULT_MSG_CONTENT: String = "Oops"

  def universitiesInlineKeyboard(universities: List[String]): Keyboard.Inline =
    buildInlineKeyboard[String](universities.map(Seq(_)), u => u, u => u)

  def coursesInlineKeyboard(courses: List[Int]): Keyboard.Inline =
    buildInlineKeyboard[Int](courses.map(Seq(_)), c => c.toString, c => c.toString)

  def groupsInlineKeyboard(groups: List[Int]): Keyboard.Inline =
    buildInlineKeyboard[Int](groups.map(Seq(_)), g => g.toString, g => g.toString)

  def studentsInlineKeyboard(students: List[StudentReadDomain]): Keyboard.Inline =
    buildInlineKeyboard[StudentReadDomain](
      students.map(Seq(_)),
      s => s.lastName + " " + s.firstName,
      s => s.userId.toString
    )

  def queueSeriesInlineKeyboard(queueSeries: List[QueueSeriesReadDomain]): Keyboard.Inline = buildInlineKeyboard[QueueSeriesReadDomain](
    queueSeries.map(Seq(_)),
    qs => qs.name,
    qs => qs.id.toString
  )

  def buildAddToQueueOptionsKeyboard(bundle: ResourceBundle): Keyboard.Inline = {
    val pushFrontBtn = InlineKeyboardButton(
      bundle.getFormattedString("button.add_to_queue_option.push_front"),
      callbackData = AddToQueueOption.PushFront.toString.some
    )
    val pushBackBtn = InlineKeyboardButton(
      bundle.getFormattedString("button.add_to_queue_option.push_back"),
      callbackData = AddToQueueOption.PushBack.toString.some
    )
    val takePlaceBtn =
      InlineKeyboardButton(
        bundle.getFormattedString("button.add_to_queue_option.take_place"),
        callbackData = AddToQueueOption.TakePlace.toString.some
      )
    val ikm = InlineKeyboardMarkup.singleColumn(Seq(pushFrontBtn, pushBackBtn, takePlaceBtn))
    Keyboard.Inline(ikm)
  }

  def placesInlineKeyboard(places: List[Int]): Keyboard.Inline = buildInlineKeyboard[Int](
    places.grouped(5).toList,
    p => p.toString,
    p => p.toString
  )
}
