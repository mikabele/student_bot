package service

import canoe.models.User
import cats.Monad
import domain.user.UserReadDomain
import error.BotError
import repository.AuthRepository
import service.impl.AuthServiceImpl

trait AuthService[F[_]] {
  def registerUser(userId: Int, from: User): F[Either[BotError, Int]]

  def getStudents(course: Int, group: Int): F[List[UserReadDomain]]

  def getGroups(course: Int): F[List[Int]]

  def getCourses(user: Option[User]): F[Either[BotError, List[Int]]]

}

object AuthService {
  def of[F[_]: Monad](authRepository: AuthRepository[F]): AuthService[F] = {
    new AuthServiceImpl[F](authRepository)
  }
}
