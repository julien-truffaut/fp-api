package parser

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Monad, SemigroupK}
import org.apache.poi.ss.SpreadsheetVersion
import org.apache.poi.ss.util.{AreaReference, CellReference}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import parser.ReaderError._

import scala.annotation.tailrec
import scala.reflect.ClassTag

/** Typeclass to read data from a workbook */
trait Reader[A]{ self =>
  def read(workbook: XSSFWorkbook): Either[ReaderError, A]

  def flatMap[B](f: A => Reader[B]): Reader[B] = new Reader[B] {
    def read(workbook: XSSFWorkbook): Either[ReaderError, B] =
      self.read(workbook).flatMap(f(_).read(workbook))
  }

  def flatMapF[B](f: A => Either[ReaderError, B]): Reader[B] =
    flatMap(a => Reader.lift(f(a)))

  def flatMapF[B](f: A => Option[B], ifNone: => ReaderError): Reader[B] =
    flatMap(a => Reader.lift(Either.fromOption(f(a), ifNone)))
}

object Reader {
  def apply[A](implicit ev: Reader[A]): Reader[A] = ev

  def success[A](value: A): Reader[A] = lift(Right(value))
  def fail[A](error: ReaderError): Reader[A] = lift(Left(error))

  def lift[A](res: Either[ReaderError, A]): Reader[A] = new Reader[A] {
    def read(workbook: XSSFWorkbook): Either[ReaderError, A] = res
  }

  def boolean(name: String): Reader[Boolean] =
    single(name).flatMapF(_.asBoolean(name))

  def booleanRange(name: String): Reader[List[Boolean]] =
    range(name).flatMapF(_.traverseU(_.asBoolean(name)))

  def booleanInt(name: String): Reader[Boolean] =
    parse(name, int){
      case 0 => Some(false)
      case 1 => Some(true)
      case _ => None
    }

  def numeric(name: String): Reader[Double] =
    single(name).flatMapF(_.asDouble(name))

  def numericRange(name: String): Reader[List[Double]] =
    range(name).flatMapF(_.traverseU(_.asDouble(name)))

  def int(name: String): Reader[Int] =
    single(name).flatMapF(_.asInt(name))

  def intRange(name: String): Reader[List[Int]] =
    range(name).flatMapF(_.traverseU(_.asInt(name)))

  def string(name: String): Reader[String] =
    single(name).flatMapF(_.asString(name))

  def stringRange(name: String): Reader[List[String]] =
    range(name).flatMapF(_.traverseU(_.asString(name)))

  def rawValue(name: String): Reader[String] =
    single(name).map(_.value.getRawValue)

  // useful when there is a mix of number and string format
  def rawValueRange(name: String): Reader[List[String]] =
    range(name).map(_.map(_.value.getRawValue))

  def parse[A, B: ClassTag](name: String, row: String => Reader[A])(f: A => Option[B]): Reader[B] =
    row(name).flatMapF(f, invalidFormat(name, implicitly[ClassTag[B]].runtimeClass.getSimpleName, ""))

  def single(name: String): Reader[SafeCell] = new Reader[SafeCell] {
    def read(workbook: XSSFWorkbook): Either[ReaderError, SafeCell] =
      Either.catchNonFatal{
        val area    = new AreaReference(workbook.getName(name).getRefersToFormula, SpreadsheetVersion.EXCEL97)
        if(area.getAllReferencedCells.length > 1){
          Left(ReaderError.invalidFormat(name, "single", "more than 1 value"))
        } else {
          val cellRef = area.getFirstCell
          Right(
            workbook
              .getSheet(cellRef.getSheetName)
              .getRow(cellRef.getRow)
              .getCell(cellRef.getCol)
          )
        }
      }.leftMap(_ => ReaderError.missingName(name))
        .flatten
        .map(SafeCell)
  }

  def range(name: String): Reader[List[SafeCell]] = new Reader[List[SafeCell]] {
    def read(workbook: XSSFWorkbook): Either[ReaderError, List[SafeCell]] =
      Either.catchNonFatal{
        val area    = new AreaReference(workbook.getName(name).getRefersToFormula, SpreadsheetVersion.EXCEL97)
        area.getAllReferencedCells.toList.map(cellRef =>
          workbook
            .getSheet(cellRef.getSheetName)
            .getRow(cellRef.getRow)
            .getCell(cellRef.getCol)
        )
      }.bimap(
        _  => ReaderError.missingName(name),
        _.map(SafeCell)
      )
  }

  def nel[A](name: String, f: String => Reader[List[A]]): Reader[NonEmptyList[A]] =
    f(name).flatMapF(NonEmptyList.fromList(_).fold[Either[ReaderError, NonEmptyList[A]]](Left(ReaderError.invalidFormat(name, "non empty list", "no values")))(Right(_)))

  def fromCellRef(sheetReader: String, cellRef: CellReference): Reader[SafeCell] = new Reader[SafeCell] {
    def read(workbook: XSSFWorkbook): Either[ReaderError, SafeCell] =
      Either.catchNonFatal{
        workbook
          .getSheet(sheetReader)
          .getRow(cellRef.getRow)
          .getCell(cellRef.getCol)
      }.bimap(
        _ => ReaderError.missingName(cellRef.toString),
        SafeCell
      )
  }

  implicit val instance: Monad[Reader] with SemigroupK[Reader] = new Monad[Reader] with SemigroupK[Reader] {
    def combineK[A](x: Reader[A], y: Reader[A]): Reader[A] = new Reader[A] {
      def read(workbook: XSSFWorkbook): Either[ReaderError, A] =
        x.read(workbook) orElse y.read(workbook)
    }

    def flatMap[A, B](fa: Reader[A])(f: A => Reader[B]): Reader[B] =
      fa.flatMap(f)

    def tailRecM[A, B](a: A)(f: A => Reader[Either[A, B]]): Reader[B] = new Reader[B] {
      def read(workbook: XSSFWorkbook): Either[ReaderError, B] = {
        @tailrec
        def loop(thisA: A): Either[ReaderError, B] = f(thisA).read(workbook) match {
          case Left(a1)        => Left(a1)
          case Right(Left(a1)) => loop(a1)
          case Right(Right(b)) => Right(b)
        }
        loop(a)
      }
    }

    def pure[A](x: A): Reader[A] = Reader.success(x)
  }
}
