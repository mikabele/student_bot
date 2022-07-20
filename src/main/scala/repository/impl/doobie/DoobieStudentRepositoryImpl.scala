package repository.impl.doobie

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import domain.user.StudentReadDomain
import doobie.Fragment
import doobie.implicits._
import doobie.util.fragments.in
import doobie.util.transactor.Transactor
import repository.StudentRepository
import repository.impl.doobie.logger.logger.log4jLogger

class DoobieStudentRepositoryImpl[F[_]: Async](tx: Transactor[F]) extends StudentRepository[F] {

  private val getStudentsQuery =
    Fragment.const("SELECT id,first_name,last_name,course,\"group\",tg_user_id FROM student")
  private val getGroupsQuery    = Fragment.const("SELECT DISTINCT \"group\" FROM student")
  private val getCoursesQuery   = Fragment.const("SELECT DISTINCT course FROM student")
  private val registerUserQuery = Fragment.const("UPDATE student SET ")
  private val getGroupSizeQuery = Fragment.const("SELECT COUNT(*) FROM student")

  override def getStudents(course: Int, group: Int): F[List[StudentReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE course = $course AND " ++ Fragment.const(
      "\"group\""
    ) ++ fr" = $group ORDER BY id")
      .query[StudentReadDomain]
      .to[List]
      .transact(tx)
  }

  override def getGroups(course: Int): F[List[Int]] = {
    (getGroupsQuery ++ fr" WHERE course = $course").query[Int].to[List].transact(tx)
  }

  override def getCourses: F[List[Int]] = {
    getCoursesQuery.query[Int].to[List].transact(tx)
  }

  override def registerUser(userId: Int, tgUserId: String): F[Int] = {
    (registerUserQuery ++ fr" tg_user_id = $tgUserId WHERE id = $userId AND tg_user_id IS NULL").update.run
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
}
