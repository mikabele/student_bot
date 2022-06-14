package service.impl

import canoe.models.User
import cats.Monad
import domain.user._
import repository.AuthRepository
import service.AuthService
import cats.syntax.all._

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

  override def startAuth(): F[List[Int]] = {
    for {
      courses <- authRepository.getCourses()
    } yield courses
  }

  override def registerUser(userId: Int, from: User): F[Int] = {
    for {
      count <- authRepository.registerUser(userId, from.username.get)
    } yield count
  }
}
