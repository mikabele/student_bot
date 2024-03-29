package domain

object app {

  final case class AppConf(
    db:     DbConf,
    tg:     TgConf,
    bundle: Bundle
  )

  final case class DbConf(
    driver:             String,
    url:                String,
    user:               String,
    password:           String,
  )

  final case class TgConf(
    token: String
  )

  final case class Bundle(
    path:      String,
    languages: List[String]
  )
}
