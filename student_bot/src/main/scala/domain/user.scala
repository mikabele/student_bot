package domain

object user {
  final case class UserReadDomain(
    userId:    Int,
    firstName: String,
    lastName:  String
  )
}
