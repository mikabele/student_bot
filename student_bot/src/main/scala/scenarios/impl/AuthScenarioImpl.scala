package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{callbackQueryApi, chatApi, Scenario, TelegramClient}
import canoe.models.{CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, KeyboardButton, ReplyKeyboardMarkup}
import canoe.syntax._
import cats.Monad
import cats.syntax.all._
import domain.user.UserReadDomain
import scenarios.AuthScenario
import service.AuthService

class AuthScenarioImpl[F[_]: TelegramClient: Monad](authService: AuthService[F]) extends AuthScenario[F] {

  private val mainMenuBtns: Seq[Seq[KeyboardButton]] = Seq(
    Seq(KeyboardButton("Add yourself in query"), KeyboardButton("Add friend in query")),
    Seq(KeyboardButton("Take other place in query"), KeyboardButton("Remove yourself from query")),
    Seq(KeyboardButton("View all available queries"), KeyboardButton("View query"))
  )

  private val mainMenuMp: ReplyKeyboardMarkup = ReplyKeyboardMarkup(mainMenuBtns, resizeKeyboard = true.some)

  private val mainMenuKeyboard = Keyboard.Reply(mainMenuMp)

  private def createInlineKeyboardFromCourses(courses: List[Int]): Keyboard.Inline = {
    val buttons = courses.map(c => InlineKeyboardButton.callbackData(text = c.toString, cbd = s"getGroups--$c"))
    val ikm     = InlineKeyboardMarkup.singleColumn(buttons)
    Keyboard.Inline(ikm)
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
      _        = println("I got message")
      courses <- Scenario.eval(authService.startAuth())
      kb       = createInlineKeyboardFromCourses(courses)
      _        = println("I create keyboard")
      _ <- Scenario.eval(
        msg.chat.send(
          "Привет, студент. Я Бот, могу помочь тебе с некоторыми штучками. Но сначала мне надо знать, кто ты. Выбери, пожалуйста, номер курса.",
          keyboard = kb
        )
      )
    } yield ()
  }

  override def getStudentsAnswer: PartialFunction[CallbackQuery, F[Unit]] =
    new PartialFunction[CallbackQuery, F[Unit]] {
      override def isDefinedAt(query: CallbackQuery): Boolean =
        query.data.exists(d =>
          d.split("--").toList match {
            case "getStudents" :: course :: group :: Nil if course.toIntOption.nonEmpty && group.toIntOption.nonEmpty =>
              true
            case _ => false
          }
        )

      override def apply(query: CallbackQuery): F[Unit] = {
        val List(_, course, group) = query.data.get.split("--").toList
        val courseInt              = course.toInt
        val groupInt               = group.toInt
        for {
          students <- authService.getStudents(courseInt, groupInt)
          kb        = createInlineKeyboardFromStudents(courseInt, groupInt, students)
          _ <-
            query.message.traverse(_.chat.send("Круто, давай найдем, кто ты из списка.", keyboard = kb))
          _ <- query.finish
        } yield ()
      }
    }

  override def getGroupsAnswer: PartialFunction[CallbackQuery, F[Unit]] = new PartialFunction[CallbackQuery, F[Unit]] {
    override def isDefinedAt(query: CallbackQuery): Boolean = query.data.exists(_.split("--").toList match {
      case "getGroups" :: course :: Nil if course.toIntOption.nonEmpty => true
      case _                                                           => false
    })

    override def apply(query: CallbackQuery): F[Unit] = {
      val List(_, course) = query.data.get.split("--").toList
      val courseInt       = course.toInt
      for {
        groups <- authService.getGroups(courseInt)
        kb      = createInlineKeyboardFromGroups(courseInt, groups)
        _      <- query.message.traverse(_.chat.send("Круто, давай выберем группу", keyboard = kb))
        _      <- query.finish
      } yield ()
    }
  }

  def registerUser: PartialFunction[CallbackQuery, F[Unit]] = {
    case query if query.data.exists(_.split("--").toList match {
          case "registerUser" :: course :: group :: userId :: Nil
              if course.toIntOption.nonEmpty && group.toIntOption.nonEmpty && userId.toIntOption.nonEmpty =>
            true
          case _ => false
        }) => {
      val List(_, _, _, user) = query.data.get.split("--").toList
      val userId              = user.toInt
      for {
        _ <- authService.registerUser(userId, query.from)
        kb = mainMenuKeyboard
        _ <- query.message.traverse(
          _.chat.send("Ура, теперь я знаю как тебя зовут и я полностью готов к работе", keyboard = kb)
        )
        _ <- query.finish
      } yield ()
    }
  }

  override def answers: Seq[PartialFunction[CallbackQuery, F[Unit]]] =
    Seq(getStudentsAnswer, getGroupsAnswer, registerUser)
}
