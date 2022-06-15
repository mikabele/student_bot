package service.impl

import canoe.models.User
import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import domain.user._
import error.BotError
import error.auth._
import repository.AuthRepository
import service.AuthService

class AuthServiceImpl[F[_]: Monad](authRepository: AuthRepository[F]) extends AuthService[F] {
  override def getStudents(course: Int, group: Int): F[List[UserReadDomain]] = {
    for {
      students <- authRepository.getStudents(course, group)
    } yield students
  }

  override def getGroups(course: Int): F[List[Int]] = {
    for {
      groups <- authRepository.getGroups(course)
    } yield groups
  }

  private def checkAuth(user: Option[User]): F[Either[BotError, Boolean]] = {
    val res = for {
      usr      <- EitherT.fromOption(user, UserNotFound)
      nickname <- EitherT.fromOption(usr.username, UsernameNotFound)
      chk      <- EitherT.liftF[F, BotError, Boolean](authRepository.checkUserByNickname(nickname))
    } yield chk

    res.value
  }

  override def getCourses(user: Option[User]): F[Either[BotError, List[Int]]] = {
    val res = for {
      check   <- EitherT(checkAuth(user))
      _       <- EitherT.cond(!check, (), AlreadyAuthorizedUser)
      courses <- EitherT.liftF[F, BotError, List[Int]](authRepository.getCourses())
    } yield courses

    res.value
  }

  override def registerUser(userId: Int, from: User): F[Either[BotError, Int]] = {
    val res = for {
      check <- EitherT(checkAuth(from.some))
      _     <- EitherT.cond(!check, (), AlreadyAuthorizedUser)
      count <- EitherT.liftF(authRepository.registerUser(userId, from.username.get))
      _     <- EitherT.cond(count == 1, (), ForbiddenAuthUser): EitherT[F, BotError, Unit]
    } yield count

    res.value
  }
}
