package repository

import cats.effect.kernel.Async
import domain.user.UserReadDomain
import doobie.util.transactor.Transactor
import repository.impl.doobie.DoobieAuthRepositoryImpl

trait AuthRepository[F[_]] {
  def checkUserByNickname(usr: String): F[Boolean]

  def registerUser(userId: Int, username: String): F[Int]

  def getStudents(course: Int, group: Int): F[List[UserReadDomain]]

  def getGroups(course: Int): F[List[Int]]
  def getCourses(): F[List[Int]]
}

object AuthRepository {
  def of[F[_]: Async](tx: Transactor[F]): AuthRepository[F] = {
    new DoobieAuthRepositoryImpl[F](tx)
  }
}
