package service.impl

import canoe.models.User
import cats.Monad
import cats.data.{EitherT, NonEmptyList}
import domain.user._
import error.BotError
import error.impl.admin.EmptyDataFile
import error.impl.auth._
import org.typelevel.log4cats.Logger
import repository.StudentRepository
import service.StudentService

class StudentServiceImpl[F[_]: Monad](authRepository: StudentRepository[F])(implicit logger: Logger[F])
  extends StudentService[F] {

  override def registerUser(userId: Int, user: User): F[Either[BotError, Int]] = {
    val res = for {
      count <- EitherT.liftF(authRepository.registerUser(userId, user.id.toString))
      _     <- EitherT.cond(count == 1, (), ForbiddenAuthUser): EitherT[F, BotError, Unit]
    } yield count

    res.value
  }

  override def getGroups(university: String, course: Int): F[List[Int]] = {
    authRepository.getGroups(university, course)
  }

  override def getCourses(university: String): F[List[Int]] = {
    authRepository.getCourses(university)
  }

  override def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]] = {
    val res = for {
      usr     <- EitherT.fromOption(user, UserNotFound: BotError)
      student <- EitherT.fromOptionF(authRepository.getStudentByTgID(usr.id.toString), NonAuthorizedUser: BotError)
    } yield student
    res.value
  }

  override def getStudents(university: String, course: Int, group: Int): F[List[StudentReadDomain]] =
    authRepository.getStudents(university, course, group)

  override def signOut(student: StudentReadDomain): F[Int] = {
    authRepository.removeUser(student.userId)
  }

  override def getUniversities: F[List[String]] = {
    authRepository.getUniversities()
  }

  override def addGroup(students: List[StudentCreateDomain]): F[Either[BotError, Int]] = {
    val res = for {
      nel <- EitherT
        .fromOption(NonEmptyList.fromList(students), EmptyDataFile: BotError): EitherT[F, BotError, NonEmptyList[
        StudentCreateDomain
      ]]
      res <- EitherT.liftF(authRepository.addGroup(nel)): EitherT[F, BotError, Int]
    } yield res

    res.value
  }
}
