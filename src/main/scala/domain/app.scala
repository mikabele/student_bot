package domain

object app {

  final case class AppConf(
    db:    DbConf,
    tg:    TgConf,
    redis: RedisConf
  )

  final case class DbConf(
    provider:          String,
    driver:            String,
    url:               String,
    user:              String,
    password:          String,
    migrationLocation: String
  )

  final case class TgConf(
    token: String
  )

  final case class RedisConf(
    uri: String
  )
}