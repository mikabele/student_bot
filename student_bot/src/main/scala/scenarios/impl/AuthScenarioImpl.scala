package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{callbackQueryApi, chatApi, messageApi, Scenario, TelegramClient}
import canoe.models._
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import domain.message.ReplyMessage
import domain.user.UserReadDomain
import error.BotError
import error.auth.AlreadyAuthorizedUser
import scenarios.AuthScenario
import service.AuthService
import util.MarshallingUtil.replyMsg

class AuthScenarioImpl[F[_]: TelegramClient: Monad](authService: AuthService[F]) extends AuthScenario[F] {

  private val mainMenuKeyboard: Keyboard.Reply = {
    val mainMenuBtns: Seq[Seq[KeyboardButton]] = Seq(
      Seq(KeyboardButton("Записаться в очередь"), KeyboardButton("Записать друга в очередь")),
      Seq(KeyboardButton("Записаться на другое место в очереди"), KeyboardButton("Выписаться из очереди")),
      Seq(KeyboardButton("Посмотреть список очередей"), KeyboardButton("Посмотреть очередь"))
    )
    val mainMenuMp: ReplyKeyboardMarkup = ReplyKeyboardMarkup(mainMenuBtns, resizeKeyboard = true.some)
    Keyboard.Reply(mainMenuMp)
  }

  private def getReplyMessageFromCourses(coursesET: Either[BotError, List[Int]]): ReplyMessage[TextMessage] = {
    coursesET match {
      case Right(courses) => {
        val buttons = courses.map(c => InlineKeyboardButton.callbackData(text = c.toString, cbd = s"getGroups--$c"))
        val ikm     = InlineKeyboardMarkup.singleColumn(buttons)
        val kb      = Keyboard.Inline(ikm)
        ReplyMessage(
          "Привет, студент. Я Бот, могу помочь тебе с некоторыми штучками. Но сначала мне надо знать, кто ты. Выбери, пожалуйста, номер курса.",
          keyboard = kb
        )
      }
      case Left(error @ AlreadyAuthorizedUser) => ReplyMessage(error.getMessage, keyboard = mainMenuKeyboard)
      case Left(error) => ReplyMessage(error.getMessage)
    }
  }

  private def createInlineKeyboardFromStudents(
    course:   Int,
    group:    Int,
    students: List[UserReadDomain]
  ): Keyboard.Inline = {
    val buttons = students.map(s =>
      InlineKeyboardButton.callbackData(
        text = s.lastName + " " + s.firstName,
        cbd  = s"registerUser--$course--$group--${s.userId}"
      )
    )
    val ikm = InlineKeyboardMarkup.singleColumn(buttons)
    Keyboard.Inline(ikm)
  }

  private def createInlineKeyboardFromGroups(course: Int, groups: List[Int]): Keyboard.Inline = {
    val buttons =
      groups.map(g => InlineKeyboardButton.callbackData(text = g.toString, cbd = s"getStudents--$course--$g"))
    val ikm = InlineKeyboardMarkup.singleColumn(buttons)
    Keyboard.Inline(ikm)
  }

  override def startBot: Scenario[F, Unit] = {
    for {
      msg     <- Scenario.expect(command("start"))
      courses <- Scenario.eval(authService.getCourses(msg.from))
      rm       = getReplyMessageFromCourses(courses)
      _       <- Scenario.eval(replyMsg(msg.chat, rm))
    } yield ()

  }

  private def getStudentsAnswer: PartialFunction[CallbackQuery, F[Unit]] = {
    case query if query.data.exists(_.startsWith("getStudents")) =>
      val List(_, course, group) = query.data.get.split("--").toList
      val courseInt              = course.toInt
      val groupInt               = group.toInt
      for {
        students <- authService.getStudents(courseInt, groupInt)
        kb        = createInlineKeyboardFromStudents(courseInt, groupInt, students)
        _        <- query.message.traverse(_.delete)
        _ <-
          query.message.traverse(_.chat.send("Круто, давай найдем, кто ты из списка.", keyboard = kb))
        _ <- query.finish
      } yield ()
  }

  private def getGroupsAnswer: PartialFunction[CallbackQuery, F[Unit]] = {
    case query if query.data.exists(_.startsWith("getGroups")) =>
      val List(_, course) = query.data.get.split("--").toList
      val courseInt       = course.toInt
      for {
        groups <- authService.getGroups(courseInt)
        kb      = createInlineKeyboardFromGroups(courseInt, groups)
        _      <- query.message.traverse(_.delete)
        _      <- query.message.traverse(_.chat.send("Круто, давай выберем группу", keyboard = kb))
        _      <- query.finish
      } yield ()
  }

  private def registerUser: PartialFunction[CallbackQuery, F[Unit]] = {
    case query if query.data.exists(_.startsWith("registerUser")) => {
      val List(_, _, _, user) = query.data.get.split("--").toList
      val userId              = user.toInt
      val res = for {
        _ <- EitherT(authService.registerUser(userId, query.from))
        rm = ReplyMessage(
          "Ура, теперь я знаю как тебя зовут и я полностью готов к работе",
          keyboard = mainMenuKeyboard
        )
        _ <- EitherT.liftF[F, BotError, Option[Boolean]](query.message.traverse(_.delete))
        _ <- EitherT.liftF[F, BotError, Option[TextMessage]](query.message.traverse(msg => replyMsg(msg.chat, rm)))
      } yield ()

      //TODO add error handling
      res.value.void
    }
  }

  override def answers: Seq[PartialFunction[CallbackQuery, F[Unit]]] =
    Seq(getStudentsAnswer, getGroupsAnswer, registerUser)
}
