package parser

sealed trait ParserError extends Product with Serializable

object ParserError {
  private case class InvalidFormat(ref: String, expectedFormat: String, message: String) extends ParserError
  private case class MissingName(name: String) extends ParserError
  private case class MissingCell(ref: String) extends ParserError

  def invalidFormat(ref: String, expectedFormat: String, message: String): ParserError = InvalidFormat(ref, expectedFormat, message)
  def missingName(name: String): ParserError = MissingName(name)
  def missingCell(ref: String): ParserError = MissingCell(ref)
}