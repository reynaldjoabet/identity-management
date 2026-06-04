package example

//Signs and verifies the session cookie value
trait DataProtector[F[_]] {
  def protect(plaintext: String): F[String]

  /** Returns `None` when the payload is missing, tampered with, or expired. */
  def unprotect(protectedText: String): F[Option[String]]
}
