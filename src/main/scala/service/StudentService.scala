package service

import canoe.models.User
import cats.Monad
import domain.user._
import error.BotError
import repository.StudentRepository
import service.impl.StudentServiceImpl

trait StudentService[F[_]] {
  def getAuthorizedStudents(course: Int, group: Int): F[List[StudentReadDomain]]

  def registerUser(userId: Int, from: User): F[Either[BotError, Int]]

  def getNonAuthorizedStudents(course: Int, group: Int): F[List[StudentReadDomain]]

  def getGroups(course: Int): F[List[Int]]

  def getCourses: F[List[Int]]

  def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]]

}

object StudentService {
  def of[F[_]: Monad](authRepository: StudentRepository[F]): StudentService[F] = {
    new StudentServiceImpl[F](authRepository)
  }
}
