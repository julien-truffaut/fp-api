package parser

sealed trait ReaderError extends Product with Serializable

object ReaderError {
  private case class InvalidFormat(ref: String, expectedFormat: String, message: String) extends ReaderError
  private case class MissingName(name: String) extends ReaderError
  private case class MissingCell(ref: String) extends ReaderError

  def invalidFormat(ref: String, expectedFormat: String, message: String): ReaderError = InvalidFormat(ref, expectedFormat, message)
  def missingName(name: String): ReaderError = MissingName(name)
  def missingCell(ref: String): ReaderError = MissingCell(ref)
}