package domain

object user {
  final case class StudentReadDomain(
    userId:    Int,
    firstName: String,
    lastName:  String,
    course:    Int,
    group:     Int,
    tgUserId:  Option[String]
  )
}
