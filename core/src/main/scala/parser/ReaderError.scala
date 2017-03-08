package parser

sealed trait ReaderError extends Product with Serializable

object ReaderError {
  private case class MissingName(name: String) extends ReaderError
  private case class InvalidFormat(name: String, expectedFormat: String, message: String) extends ReaderError

  def missingName(name: String): ReaderError = MissingName(name)
  def invalidFormat(name: String, expectedFormat: String, message: String): ReaderError = InvalidFormat(name, expectedFormat, message)
}