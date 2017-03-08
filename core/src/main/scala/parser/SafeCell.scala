package parser

import cats.syntax.either._
import org.apache.poi.xssf.usermodel.XSSFCell

case class SafeCell(value: XSSFCell){
  def asDouble(name: String): Either[ReaderError, Double] =
    Either.catchNonFatal(value.getNumericCellValue).leftMap(e =>
      ReaderError.invalidFormat(name, "Numeric", e.getMessage)
    )

  def asInt(name: String): Either[ReaderError, Int] =
    asDouble(name).flatMap(d => Either.fromOption(doubleToInt(d), ReaderError.invalidFormat(name, "Int", s"$d is not an Int")))

  def asString(name: String): Either[ReaderError, String] =
    Either.catchNonFatal(value.getStringCellValue).leftMap(e =>
      ReaderError.invalidFormat(name, "String", e.getMessage)
    )

  def asBoolean(name: String): Either[ReaderError, Boolean] =
    Either.catchNonFatal(value.getBooleanCellValue).leftMap(e =>
      ReaderError.invalidFormat(name, "Boolean", e.getMessage)
    )

  private def doubleToInt(d: Double): Option[Int] =
    if(d.isValidInt) Some(d.toInt) else None
}
