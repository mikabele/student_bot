package scenarios.impl

import canoe.api.models.Keyboard
import canoe.api.{callbackQueryApi, Scenario, TelegramClient}
import canoe.methods.messages.SendMessage
import canoe.models.CallbackQuery
import canoe.models.messages.TextMessage
import canoe.syntax._
import cats.effect.Concurrent
import cats.syntax.all._
import constants._
import domain.queue.AddToQueueOption
import domain.user.StudentReadDomain
import implicits.bot.containingWithBundle
import org.typelevel.log4cats.Logger
import scenarios.QueueScenarios
import service.{QueueService, StudentService}
import util.DateValidationUtil
import util.DateValidationUtil.dateValidation
import util.MarshallingUtil._
import util.bundle.ResourceBundleUtil
import util.bundle.StringFormatExtension._

import java.time.LocalDate
import java.util.ResourceBundle

class QueueScenariosImpl[F[_]: TelegramClient: Concurrent](
  queueService:   QueueService[F],
  studentService: StudentService[F],
  bundleUtil:     ResourceBundleUtil
)(
  implicit logger: Logger[F]
) extends QueueScenarios[F] {

  private def handleAddTOQueueOption(
    query:    CallbackQuery,
    bundle:   ResourceBundle,
    flowName: String,
    student:  StudentReadDomain,
    qsId:     Int,
    date:     LocalDate,
    option:   AddToQueueOption
  ): Scenario[F, Option[Int]] = {
    option match {
      case AddToQueueOption.TakePlace =>
        for {
          places <- Scenario.fromEitherF(queueService.getAvailablePlaces(student, qsId, date))
          query4 <- sendMessageWithCallback(
            defaultCallbackAnswer[F, TextMessage](query),
            bundle.getFormattedString(s"flow.${flowName}.msg.places"),
            placesInlineKeyboard(places)
          )
          place = query4.data.get.toInt.some
          _    <- Scenario.eval(query4.finish)
        } yield place
      case _ =>
        for {
          _ <- Scenario.eval(query.finish)
        } yield Option.empty[Int]
    }
  }

  override def addToQueueScenario: Scenario[F, Unit] = {
    scenario(containingWithBundle("button.main.take_place", bundleUtil), bundleUtil) { msg =>
      for {
        student     <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name    = "take_place"
        bundle       = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        queueSeries <- Scenario.eval(queueService.getQueueSeries(student))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          queueSeriesInlineKeyboard(queueSeries)
        )
        qsId = query1.data.get.toInt
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.date"),
          Keyboard.Unchanged
        )

        msg2 <- Scenario.expect(dateValidation(textMessage))
        date  = LocalDate.parse(msg2.text, DateValidationUtil.pattern)
        query3 <- sendMessageWithCallback(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.option"),
          buildAddToQueueOptionsKeyboard(bundle)
        )
        option = AddToQueueOption.withName(query3.data.get)
        place <- handleAddTOQueueOption(query3, bundle, flow_name, student, qsId, date, option)
        res <- Scenario.fromEitherF(
          queueService.addToQueue(student, qsId, date, option, place)
        )
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish", res),
          mainMenuUserKeyboard(bundle)
        )

      } yield ()
    }

  }

  override def getQueueSeriesScenario: Scenario[F, Unit] = {
    scenario(containingWithBundle("button.main.view_queue_series", bundleUtil), bundleUtil) { msg =>
      for {
        student     <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name    = "view_queue_series"
        bundle       = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        queueSeries <- Scenario.eval(queueService.getQueueSeries(student))
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(
            s"flow.${flow_name}.msg.finish",
            queueSeries.map(qs => "- " + qs.name).reduce(_ |+| "\n" |+| _)
          ),
          mainMenuUserKeyboard(bundle)
        )

      } yield ()
    }
  }

  override def getQueueScenario: Scenario[F, Unit] = {
    scenario(containingWithBundle("button.main.view_queues", bundleUtil), bundleUtil) { msg =>
      for {
        student     <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name    = "view_queue"
        bundle       = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        queueSeries <- Scenario.eval(queueService.getQueueSeries(student))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          queueSeriesInlineKeyboard(queueSeries)
        )
        qsId = query1.data.get.toInt
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.date"),
          Keyboard.Unchanged
        )

        msg2         <- Scenario.expect(dateValidation(textMessage))
        date          = LocalDate.parse(msg2.text, DateValidationUtil.pattern)
        queue        <- Scenario.fromEitherF(queueService.getQueue(qsId, date))
        student_names = queue.records.map(q => "-" |+| q.student.lastName |+| " " |+| q.student.firstName)
        _ <- sendMessage(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish", student_names.fold("")(_ |+| "\n" |+| _)),
          mainMenuUserKeyboard(bundle)
        )

      } yield ()
    }
  }

  override def addFriendToQueueScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.main.add_friend_place", bundleUtil), bundleUtil) { msg =>
      for {
        student  <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name = "add_friend_to_queue"
        bundle    = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        courses  <- Scenario.eval(studentService.getCourses(student.university))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.courses"),
          coursesInlineKeyboard(courses)
        )
        course  = query1.data.get.toInt
        groups <- Scenario.eval(studentService.getGroups(student.university, course))
        query2 <- sendMessageWithCallback(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.groups"),
          groupsInlineKeyboard(groups)
        )
        group     = query2.data.get.toInt
        students <- Scenario.eval(studentService.getStudents(student.university, course, group))
        query3 <- sendMessageWithCallback(
          defaultCallbackAnswer[F, TextMessage](query2),
          bundle.getFormattedString(s"flow.${flow_name}.msg.students"),
          studentsInlineKeyboard(students.filter(s => s.tgUserId.nonEmpty && s.userId != student.userId))
        )
        friendId     = query3.data.get.toInt
        friend       = students.find(_.userId == friendId).get
        queueSeries <- Scenario.eval(queueService.getQueueSeries(friend))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          queueSeriesInlineKeyboard(queueSeries)
        )
        qsId = query1.data.get.toInt
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.date"),
          Keyboard.Unchanged
        )

        msg2 <- Scenario.expect(dateValidation(textMessage))
        date  = LocalDate.parse(msg2.text, DateValidationUtil.pattern)
        query3 <- sendMessageWithCallback(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.option"),
          buildAddToQueueOptionsKeyboard(bundle)
        )
        option = AddToQueueOption.withName(query3.data.get)
        place <- handleAddTOQueueOption(query3, bundle, flow_name, friend, qsId, date, option)
        res <- Scenario.fromEitherF(
          queueService.addToQueue(friend, qsId, date, option, place)
        )
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query3),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          mainMenuUserKeyboard(bundle)
        )

        queue = queueSeries.find(_.id == qsId).get
        _ <- Scenario.eval(
          SendMessage(
            friend.tgUserId.get,
            bundle.getFormattedString(s"flow.${flow_name}.msg.friend_notification", queue.name, date, res),
          ).call
        )
      } yield ()
    }

  override def removeFromQueueScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.main.remove_place", bundleUtil), bundleUtil) { msg =>
      for {
        student     <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name    = "remove_place"
        bundle       = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        queueSeries <- Scenario.eval(queueService.getQueueSeries(student))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          queueSeriesInlineKeyboard(queueSeries)
        )
        qsId = query1.data.get.toInt
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.date"),
          Keyboard.Unchanged
        )

        msg2 <- Scenario.expect(dateValidation(textMessage))
        date  = LocalDate.parse(msg2.text, DateValidationUtil.pattern)
        _    <- Scenario.fromEitherF(queueService.removeFromQueue(student, qsId, date))
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish"),
          mainMenuUserKeyboard(bundle)
        )

      } yield ()
    }

  override def takeAnotherPlaceScenario: Scenario[F, Unit] =
    scenario(containingWithBundle("button.main.update_place", bundleUtil), bundleUtil) { msg =>
      for {
        student     <- Scenario.fromEitherF(studentService.checkAuthUser(msg.from))
        flow_name    = "take_another_place"
        bundle       = bundleUtil.getBundle(msg.from.flatMap(_.languageCode).getOrElse(DEFAULT_LANG))
        queueSeries <- Scenario.eval(queueService.getQueueSeries(student))
        query1 <- sendMessageWithCallback(
          defaultMsgAnswer[F, TextMessage](msg),
          bundle.getFormattedString(s"flow.${flow_name}.msg.queue_series"),
          queueSeriesInlineKeyboard(queueSeries)
        )
        qsId = query1.data.get.toInt
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.date"),
          Keyboard.Unchanged
        )

        msg2 <- Scenario.expect(dateValidation(textMessage))
        date  = LocalDate.parse(msg2.text, DateValidationUtil.pattern)
        query3 <- sendMessageWithCallback(
          defaultCallbackAnswer[F, TextMessage](query1),
          bundle.getFormattedString(s"flow.${flow_name}.msg.option"),
          buildAddToQueueOptionsKeyboard(bundle)
        )
        option = AddToQueueOption.withName(query3.data.get)
        place <- handleAddTOQueueOption(query3, bundle, flow_name, student, qsId, date, option)
        res <- Scenario.fromEitherF(
          queueService.takeAnotherPlace(student, qsId, date, place.get)
        )
        _ <- sendMessage(
          defaultCallbackAnswer[F, TextMessage](query3),
          bundle.getFormattedString(s"flow.${flow_name}.msg.finish", res),
          mainMenuUserKeyboard(bundle)
        )

      } yield ()
    }
}
