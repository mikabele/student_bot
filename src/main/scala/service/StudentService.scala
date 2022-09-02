package service

import canoe.models.User
import cats.Monad
import domain.user._
import error.BotError
import org.typelevel.log4cats.Logger
import repository.StudentRepository
import service.impl.StudentServiceImpl

trait StudentService[F[_]] {
  def signOut(student: StudentReadDomain): F[Int]

  def registerUser(userId: Int, from: User): F[Either[BotError, Int]]

  def getGroups(university: String, course: Int): F[List[Int]]

  def getCourses(university: String): F[List[Int]]

  def checkAuthUser(user: Option[User]): F[Either[BotError, StudentReadDomain]]

  def getStudents(university: String, course: Int, group: Int): F[List[StudentReadDomain]]

  def getUniversities: F[List[String]]

  def addGroup(students: List[StudentCreateDomain]): F[Either[BotError, Int]]
}

object StudentService {
  def of[F[_]: Monad: Logger](authRepository: StudentRepository[F]): StudentService[F] = {
    new StudentServiceImpl[F](authRepository)
  }
}
