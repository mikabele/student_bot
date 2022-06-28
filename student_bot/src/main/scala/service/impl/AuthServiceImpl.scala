package service.impl

import canoe.models.User
import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import domain.user._
import error.BotError
import error.impl.auth._
import repository.StudentRepository
import service.AuthService

class AuthServiceImpl[F[_]: Monad](authRepository: StudentRepository[F]) extends AuthService[F] {

  override def registerUser(userId: Int, user: User): F[Either[BotError, Int]] = {
    val res = for {
      count <- EitherT.liftF(authRepository.registerUser(userId, user.username.get))
      _     <- EitherT.cond(count == 1, (), ForbiddenAuthUser): EitherT[F, BotError, Unit]
    } yield count

    res.value
  }

  override def getStudents(course: Int, group: Int): F[List[StudentReadDomain]] = {
    authRepository.getStudents(course, group)
  }

  override def getGroups(course: Int): F[List[Int]] = {
    authRepository.getGroups(course)
  }

  override def getCourses: F[List[Int]] = {
    authRepository.getCourses
  }

  override def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]] = {
    val res = for {
      usr      <- EitherT.fromOption(user, UserNotFound: BotError)
      nickname <- EitherT.fromOption(usr.username, UsernameNotFound: BotError)
      student  <- EitherT.fromOptionF(authRepository.getStudentByNickname(nickname), NonAuthorizedUser: BotError)
    } yield student
    res.value
  }
}
