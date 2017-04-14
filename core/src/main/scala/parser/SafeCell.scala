package parser

import cats.syntax.either._
import org.apache.poi.xssf.usermodel.XSSFCell

case class SafeCell(cell: XSSFCell){
  def asDouble: Either[ReaderError, Double] =
    Either.catchNonFatal(cell.getNumericCellValue).leftMap(e =>
      ReaderError.invalidFormat(cell.getReference, "Numeric", e.getMessage)
    )

  def asInt: Either[ReaderError, Int] =
    asDouble.flatMap(d => Either.fromOption(doubleToInt(d), ReaderError.invalidFormat(cell.getReference, "Int", s"$d is not an Int")))

  def asString: Either[ReaderError, String] =
    Either.catchNonFatal(cell.getStringCellValue).leftMap(e =>
      ReaderError.invalidFormat(cell.getReference, "String", e.getMessage)
    )

  def asBoolean: Either[ReaderError, Boolean] =
    Either.catchNonFatal(cell.getBooleanCellValue).leftMap(e =>
      ReaderError.invalidFormat(cell.getReference, "Boolean", e.getMessage)
    )

  private def doubleToInt(d: Double): Option[Int] =
    if(d.isValidInt) Some(d.toInt) else None
}
