package example

case class Account(
    userName: String,
    fullName: String,
    mailAddress: String,
    password: String,
    isAdmin: Boolean,
    url: Option[String],
    registeredDate: java.util.Date,
    updatedDate: java.util.Date,
    lastLoginDate: Option[java.util.Date],
    image: Option[String],
    isGroupAccount: Boolean,
    isRemoved: Boolean,
    description: Option[String]
)
