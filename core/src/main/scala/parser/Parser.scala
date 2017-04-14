package parser

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.either._
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
trait Parser[A]{ self =>
  def parse(workbook: XSSFWorkbook): Either[ReaderError, A]

  def flatMap[B](f: A => Parser[B]): Parser[B] = new Parser[B] {
    def parse(workbook: XSSFWorkbook): Either[ReaderError, B] =
      self.parse(workbook).flatMap(f(_).parse(workbook))
  }

  def flatMapF[B](f: A => Either[ReaderError, B]): Parser[B] =
    flatMap(a => Parser.lift(f(a)))

  def flatMapF[B](f: A => Option[B], ifNone: => ReaderError): Parser[B] =
    flatMap(a => Parser.lift(Either.fromOption(f(a), ifNone)))
}

object Parser {
  def apply[A](implicit ev: Parser[A]): Parser[A] = ev

  def success[A](value: A): Parser[A] = lift(Right(value))
  def fail[A](error: ReaderError): Parser[A] = lift(Left(error))

  def lift[A](res: Either[ReaderError, A]): Parser[A] = new Parser[A] {
    def parse(workbook: XSSFWorkbook): Either[ReaderError, A] = res
  }

  def boolean(name: String): Parser[Boolean] =
    single(name).flatMapF(_.asBoolean)

  def booleanRange(name: String): Parser[List[Boolean]] =
    range(name).flatMapF(_.traverseU(_.asBoolean))

  def booleanInt(name: String): Parser[Boolean] =
    parse(name, int){
      case 0 => Some(false)
      case 1 => Some(true)
      case _ => None
    }

  def numeric(name: String): Parser[Double] =
    single(name).flatMapF(_.asDouble)

  def numericRange(name: String): Parser[List[Double]] =
    range(name).flatMapF(_.traverseU(_.asDouble))

  def int(name: String): Parser[Int] =
    single(name).flatMapF(_.asInt)

  def intRange(name: String): Parser[List[Int]] =
    range(name).flatMapF(_.traverseU(_.asInt))

  def string(name: String): Parser[String] =
    single(name).flatMapF(_.asString)

  def stringRange(name: String): Parser[List[String]] =
    range(name).flatMapF(_.traverseU(_.asString))

  def rawValue(name: String): Parser[String] =
    single(name).map(_.cell.getRawValue)

  // useful when there is a mix of number and string format
  def rawValueRange(name: String): Parser[List[String]] =
    range(name).map(_.map(_.cell.getRawValue))

  def parse[A, B: ClassTag](name: String, row: String => Parser[A])(f: A => Option[B]): Parser[B] =
    row(name).flatMapF(f, invalidFormat(name, implicitly[ClassTag[B]].runtimeClass.getSimpleName, ""))

  def single(name: String): Parser[SafeCell] = new Parser[SafeCell] {
    def parse(workbook: XSSFWorkbook): Either[ReaderError, SafeCell] =
      for {
        area  <- getArea(workbook, name)
        _     <- if(area.getAllReferencedCells.length == 1) Right(())
                 else Left(ReaderError.invalidFormat(name, "single", "a single value"))
        cell  <- getSafeCell(workbook, area.getFirstCell)
      } yield cell
  }

  def range(name: String): Parser[List[SafeCell]] = new Parser[List[SafeCell]] {
    def parse(workbook: XSSFWorkbook): Either[ReaderError, List[SafeCell]] =
      for {
        area  <- getArea(workbook, name)
        cells <- area.getAllReferencedCells.toList.traverseU(getSafeCell(workbook, _))
      } yield cells
  }

  def nel[A](name: String, f: String => Parser[List[A]]): Parser[NonEmptyList[A]] =
    f(name).flatMapF(NonEmptyList.fromList(_).fold[Either[ReaderError, NonEmptyList[A]]](Left(ReaderError.invalidFormat(name, "non empty list", "no values")))(Right(_)))

  def getArea(workbook: XSSFWorkbook, name: String): Either[ReaderError, AreaReference] =
    Either.catchNonFatal(
       new AreaReference(workbook.getName(name).getRefersToFormula, SpreadsheetVersion.EXCEL97)
    ).leftMap(_ => ReaderError.missingName(name))

  def getSafeCell(workbook: XSSFWorkbook, cellRef: CellReference): Either[ReaderError, SafeCell] =
    Either.catchNonFatal(SafeCell(
      workbook
        .getSheet(cellRef.getSheetName)
        .getRow(cellRef.getRow)
        .getCell(cellRef.getCol)
    )).leftMap(_ => ReaderError.missingCell(cellRef.toString))

  implicit val instance: Monad[Parser] with SemigroupK[Parser] = new Monad[Parser] with SemigroupK[Parser] {
    def combineK[A](x: Parser[A], y: Parser[A]): Parser[A] = new Parser[A] {
      def parse(workbook: XSSFWorkbook): Either[ReaderError, A] =
        x.parse(workbook) orElse y.parse(workbook)
    }

    def flatMap[A, B](fa: Parser[A])(f: A => Parser[B]): Parser[B] =
      new Parser[B] {
        def parse(workbook: XSSFWorkbook): Either[ReaderError, B] =
          fa.parse(workbook).flatMap(f(_).parse(workbook))
      }

    def tailRecM[A, B](a: A)(f: A => Parser[Either[A, B]]): Parser[B] = new Parser[B] {
      def parse(workbook: XSSFWorkbook): Either[ReaderError, B] = {
        @tailrec
        def loop(thisA: A): Either[ReaderError, B] = f(thisA).parse(workbook) match {
          case Left(a1)        => Left(a1)
          case Right(Left(a1)) => loop(a1)
          case Right(Right(b)) => Right(b)
        }
        loop(a)
      }
    }

    def pure[A](x: A): Parser[A] = Parser.success(x)
  }
}
