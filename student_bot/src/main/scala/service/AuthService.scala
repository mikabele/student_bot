package service

import canoe.models.User
import cats.Monad
import domain.user._
import error.BotError
import repository.StudentRepository
import service.impl.AuthServiceImpl

trait AuthService[F[_]] {
  def registerUser(userId: Int, from: User): F[Either[BotError, Int]]

  def getStudents(course: Int, group: Int): F[List[StudentReadDomain]]

  def getGroups(course: Int): F[List[Int]]

  def getCourses: F[List[Int]]

  def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]]

}

object AuthService {
  def of[F[_]: Monad](authRepository: StudentRepository[F]): AuthService[F] = {
    new AuthServiceImpl[F](authRepository)
  }
}
