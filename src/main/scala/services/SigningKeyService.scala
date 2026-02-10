package services
import domain.models.Key

trait SigningKeyService[F[_]] {

  /** Load all keys from storage (ordered by created/version if you want
    * determinism).
    */
  def loadKeys: F[List[Key]]

  /** Persist a new serialized key. */
  def storeKey(key: Key): F[Unit]

  /** Delete a key by id. */
  def deleteKey(id: String): F[Unit]
}
