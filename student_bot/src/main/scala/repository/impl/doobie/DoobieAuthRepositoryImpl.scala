package repository.impl.doobie

import cats.effect.Async
import domain.user.UserReadDomain
import doobie.Fragment
import doobie.implicits._
import doobie.util.transactor.Transactor
import repository.AuthRepository

class DoobieAuthRepositoryImpl[F[_]: Async](tx: Transactor[F]) extends AuthRepository[F] {

  private val getStudentsQuery  = sql"SELECT id,first_name,last_name FROM students"
  private val getGroupsQuery    = Fragment.const("SELECT DISTINCT \"group\" FROM students")
  private val getCoursesQuery   = sql"SELECT DISTINCT course FROM students"
  private val registerUserQuery = sql"UPDATE students SET "

  override def getStudents(course: Int, group: Int): F[List[UserReadDomain]] = {
    (getStudentsQuery ++ fr" WHERE course = $course AND " ++ Fragment.const(
      "\"group\""
    ) ++ fr" = $group AND tgUsername IS NULL")
      .query[UserReadDomain]
      .to[List]
      .transact(tx)
  }

  override def getGroups(course: Int): F[List[Int]] = {
    val t = getGroupsQuery ++ fr" WHERE course = $course"
    (getGroupsQuery ++ fr" WHERE course = $course").query[Int].to[List].transact(tx)
  }

  override def getCourses(): F[List[Int]] = {
    getCoursesQuery.query[Int].to[List].transact(tx)
  }

  override def registerUser(userId: Int, username: String): F[Int] = {
    (registerUserQuery ++ fr" tgUsername = $username WHERE id = $userId").update.run.transact(tx)
  }
}
