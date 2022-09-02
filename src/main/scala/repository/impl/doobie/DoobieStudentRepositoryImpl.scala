package repository.impl.doobie

import cats.data.NonEmptyList
import cats.effect.Async
import domain.user._
import doobie.Fragment
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragments.{in, values}
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import repository.StudentRepository
import repository.impl.doobie.logger.logger._

class DoobieStudentRepositoryImpl[F[_]: Async: Logger](tx: Transactor[F]) extends StudentRepository[F] {

  private val getStudentsQuery =
    Fragment.const("SELECT id,first_name,last_name,university,course,\"group\",st_role,tg_user_id FROM student")
  private val getGroupsQuery       = Fragment.const("SELECT DISTINCT \"group\" FROM student")
  private val getCoursesQuery      = Fragment.const("SELECT DISTINCT course FROM student")
  private val updateUserQuery      = Fragment.const("UPDATE student SET ")
  private val getGroupSizeQuery    = Fragment.const("SELECT COUNT(*) FROM student")
  private val getUniversitiesQuery = Fragment.const("SELECT DISTINCT university FROM student")
  private val addGroupQuery        = Fragment.const("INSERT INTO student(last_name,first_name,course,\"group\",university) ")

  override def getStudents(university: String, course: Int, group: Int): F[List[StudentReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE university = $university AND course = $course AND " ++ Fragment.const(
      "\"group\""
    ) ++ fr" = $group ORDER BY id")
      .query[StudentReadDomain]
      .to[List]
      .transact(tx)
  }

  override def getGroups(university: String, course: Int): F[List[Int]] = {
    (getGroupsQuery ++ fr" WHERE university = $university AND course = $course").query[Int].to[List].transact(tx)
  }

  override def getCourses(university: String): F[List[Int]] = {
    (getCoursesQuery ++ fr"WHERE university = $university").query[Int].to[List].transact(tx)
  }

  override def registerUser(userId: Int, tgUserId: String): F[Int] = {
    (updateUserQuery ++ fr" tg_user_id = $tgUserId WHERE id = $userId AND tg_user_id IS NULL").update.run
      .transact(tx)
  }

  override def getStudentById(studentId: Int): F[Option[StudentReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE id = $studentId").query[StudentReadDomain].option.transact(tx)
  }

  override def getGroupSize(student: StudentReadDomain): F[Int] = {
    (getGroupSizeQuery ++ Fragment.const(" WHERE \"group\" = ") ++ fr"${student.group}").query[Int].unique.transact(tx)
  }

  override def getStudentsByIds(studentIds: NonEmptyList[Int]): F[List[StudentReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE " ++ in(fr"id", studentIds)).query[StudentReadDomain].to[List].transact(tx)

  }

  override def getStudentByTgID(tgID: String): F[Option[StudentReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE tg_user_id=$tgID").query[StudentReadDomain].option.transact(tx)
  }

  override def removeUser(userId: Int): F[Int] = {
    (updateUserQuery ++ fr" tg_user_id = NULL WHERE user_id = $userId").update.run.transact(tx)
  }

  override def getUniversities(): F[List[String]] = {
    getUniversitiesQuery.query[String].to[List].transact(tx)
  }

  override def addGroup(students: NonEmptyList[StudentCreateDomain]): F[Int] = {
    (addGroupQuery ++ values(students)).update.run.transact(tx)
  }
}
