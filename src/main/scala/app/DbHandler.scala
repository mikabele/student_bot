package app

import cats.effect.{Async, Resource}
import cats.implicits._
import domain.app.DbConf
import doobie.hikari.HikariTransactor
import doobie.{ExecutionContexts, Transactor}

object DbHandler {
  def transactor[F[_]: Async](
    dbConf: DbConf
  ): Resource[F, Transactor[F]] = for {
    ce <- ExecutionContexts.fixedThreadPool[F](10)
    tx <- HikariTransactor.newHikariTransactor[F](
      driverClassName = dbConf.driver,
      url             = dbConf.url,
      user            = dbConf.user,
      pass            = dbConf.password,
      connectEC       = ce
    )
  } yield tx
}
