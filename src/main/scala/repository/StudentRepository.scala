package repository

import cats.data.NonEmptyList
import cats.effect.kernel.Async
import domain.user.StudentReadDomain
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import repository.impl.doobie.DoobieStudentRepositoryImpl

trait StudentRepository[F[_]] {
  def getUniversities(): F[List[String]]

  def removeUser(userId: Int): F[Int]

  def getStudentByTgID(tgID: String): F[Option[StudentReadDomain]]

  def getStudentsByIds(studentIds: NonEmptyList[Int]): F[List[StudentReadDomain]]

  def getStudentById(studentId: Int): F[Option[StudentReadDomain]]

  def registerUser(userId: Int, tgID: String): F[Int]

  def getStudents(university: String, course: Int, group: Int): F[List[StudentReadDomain]]

  def getGroups(university: String, course: Int): F[List[Int]]

  def getCourses(university: String): F[List[Int]]

  def getGroupSize(student: StudentReadDomain): F[Int]
}

object StudentRepository {
  def of[F[_]: Async: Logger](tx: Transactor[F]): StudentRepository[F] = {
    new DoobieStudentRepositoryImpl[F](tx)
  }
}
