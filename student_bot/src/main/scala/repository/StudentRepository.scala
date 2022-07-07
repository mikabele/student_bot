package repository

import cats.effect.kernel.Async
import domain.user.StudentReadDomain
import doobie.util.transactor.Transactor
import repository.impl.doobie.DoobieStudentRepositoryImpl

trait StudentRepository[F[_]] {
  def getStudentByNickname(nickname: String): F[Option[StudentReadDomain]]

  def getStudentById(studentId: Int): F[Option[StudentReadDomain]]

  def registerUser(userId: Int, username: String): F[Int]

  def getStudents(course: Int, group: Int): F[List[StudentReadDomain]]

  def getGroups(course: Int): F[List[Int]]

  def getCourses: F[List[Int]]

  def getGroupSize(student: StudentReadDomain): F[Int]
}

object StudentRepository {
  def of[F[_]: Async](tx: Transactor[F]): StudentRepository[F] = {
    new DoobieStudentRepositoryImpl[F](tx)
  }
}