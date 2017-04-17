package parser

import cats.syntax.either._
import org.apache.poi.ss.usermodel.Cell

case class SafeCell(cell: Cell){
  val reference = s"${cell.getSheet.getSheetName}!${cell.getAddress}"

  def asDouble: Either[ParserError, Double] =
    Either.catchNonFatal(cell.getNumericCellValue).leftMap(e =>
      ParserError.invalidFormat(reference, "Numeric", e.getMessage)
    )

  def asInt: Either[ParserError, Int] =
    asDouble.flatMap(d => Either.fromOption(doubleToInt(d), ParserError.invalidFormat(reference, "Int", s"$d is not an Int")))

  def asString: Either[ParserError, String] =
    Either.catchNonFatal(cell.getStringCellValue).leftMap(e =>
      ParserError.invalidFormat(reference, "String", e.getMessage)
    )

  def asBoolean: Either[ParserError, Boolean] =
    Either.catchNonFatal(cell.getBooleanCellValue).leftMap(e =>
      ParserError.invalidFormat(reference, "Boolean", e.getMessage)
    )

  private def doubleToInt(d: Double): Option[Int] =
    if(d.isValidInt) Some(d.toInt) else None
}
