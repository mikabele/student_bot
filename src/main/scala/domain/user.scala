package domain

import doobie.postgres.implicits.pgEnumString
import enumeratum.{DoobieEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.Snakecase

object user {

  sealed trait Role extends EnumEntry with Snakecase

  case object Role extends Enum[Role] with DoobieEnum[Role] {

    val values: IndexedSeq[Role] = findValues

    final case object Admin extends Role

    final case object User extends Role

    implicit override lazy val enumMeta: doobie.Meta[Role] =
      pgEnumString("ROLE", Role.withName, _.entryName)
  }

  final case class StudentCreateDomain(
    lastName:   String,
    firstName:  String,
    course:     Int,
    group:      Int,
    university: String
  )
  final case class StudentReadDomain(
    userId:     Int,
    firstName:  String,
    lastName:   String,
    university: String,
    course:     Int,
    group:      Int,
    role:       Role,
    tgUserId:   Option[String]
  )
}
