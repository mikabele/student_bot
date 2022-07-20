package service.impl

import canoe.models.User
import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import domain.user._
import error.BotError
import error.impl.auth._
import repository.StudentRepository
import service.StudentService

class StudentServiceImpl[F[_]: Monad](authRepository: StudentRepository[F]) extends StudentService[F] {

  override def registerUser(userId: Int, user: User): F[Either[BotError, Int]] = {
    val res = for {
      count <- EitherT.liftF(authRepository.registerUser(userId, user.id.toString))
      _     <- EitherT.cond(count == 1, (), ForbiddenAuthUser): EitherT[F, BotError, Unit]
    } yield count

    res.value
  }

  override def getNonAuthorizedStudents(course: Int, group: Int): F[List[StudentReadDomain]] = {
    for {
      students <- authRepository.getStudents(course, group)
    } yield students.filter(_.tgUserId.isEmpty)
  }

  override def getGroups(course: Int): F[List[Int]] = {
    authRepository.getGroups(course)
  }

  override def getCourses: F[List[Int]] = {
    authRepository.getCourses
  }

  override def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]] = {
    val res = for {
      usr     <- EitherT.fromOption(user, UserNotFound: BotError)
      student <- EitherT.fromOptionF(authRepository.getStudentByTgID(usr.id.toString), NonAuthorizedUser: BotError)
    } yield student
    res.value
  }

  override def getAuthorizedStudents(course: Int, group: Int): F[List[StudentReadDomain]] = {
    for {
      students <- authRepository.getStudents(course, group)
      _         = println(students)
    } yield students.filter(_.tgUserId.nonEmpty)
  }
}
