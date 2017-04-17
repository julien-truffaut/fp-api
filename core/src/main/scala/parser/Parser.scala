package parser

import cats.instances.either._
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.{Monad, SemigroupK}
import org.apache.poi.ss.SpreadsheetVersion
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.{AreaReference, CellReference}
import parser.ParserError._

import scala.annotation.tailrec
import scala.reflect.ClassTag

/** Typeclass to read data from a workbook */
trait Parser[A]{ self =>
  def parse(workbook: Workbook): Either[ParserError, A]

  def flatMap[B](f: A => Parser[B]): Parser[B] = new Parser[B] {
    def parse(workbook: Workbook): Either[ParserError, B] =
      self.parse(workbook).flatMap(f(_).parse(workbook))
  }

  def flatMapF[B](f: A => Either[ParserError, B]): Parser[B] =
    flatMap(a => Parser.lift(f(a)))

  def flatMapF[B](f: A => Option[B], ifNone: => ParserError): Parser[B] =
    flatMap(a => Parser.lift(Either.fromOption(f(a), ifNone)))
}

object Parser {
  def apply[A](implicit ev: Parser[A]): Parser[A] = ev

  def success[A](value: A): Parser[A] = lift(Right(value))
  def fail[A](error: ParserError): Parser[A] = lift(Left(error))

  def lift[A](res: Either[ParserError, A]): Parser[A] = new Parser[A] {
    def parse(workbook: Workbook): Either[ParserError, A] = res
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

  def parse[A, B: ClassTag](name: String, row: String => Parser[A])(f: A => Option[B]): Parser[B] =
    row(name).flatMapF(f, invalidFormat(name, implicitly[ClassTag[B]].runtimeClass.getSimpleName, ""))

  def single(name: String): Parser[SafeCell] = new Parser[SafeCell] {
    def parse(workbook: Workbook): Either[ParserError, SafeCell] =
      for {
        area  <- getArea(workbook, name)
        _     <- if(area.getAllReferencedCells.length == 1) Right(())
                 else Left(ParserError.invalidFormat(name, "single", "a single value"))
        cell  <- getSafeCell(workbook, area.getFirstCell)
      } yield cell
  }

  def range(name: String): Parser[List[SafeCell]] = new Parser[List[SafeCell]] {
    def parse(workbook: Workbook): Either[ParserError, List[SafeCell]] =
      for {
        area  <- getArea(workbook, name)
        cells <- area.getAllReferencedCells.toList.traverseU(getSafeCell(workbook, _))
      } yield cells
  }

  def getArea(workbook: Workbook, name: String): Either[ParserError, AreaReference] =
    Either.catchNonFatal(
       new AreaReference(workbook.getName(name).getRefersToFormula, SpreadsheetVersion.EXCEL97)
    ).leftMap(_ => ParserError.missingName(name))

  def getSafeCell(workbook: Workbook, cellRef: CellReference): Either[ParserError, SafeCell] =
    Either.catchNonFatal(SafeCell(
      workbook
        .getSheet(cellRef.getSheetName)
        .getRow(cellRef.getRow)
        .getCell(cellRef.getCol)
    )).leftMap(_ => ParserError.missingCell(cellRef.toString))

  implicit val instance: Monad[Parser] with SemigroupK[Parser] = new Monad[Parser] with SemigroupK[Parser] {
    def combineK[A](x: Parser[A], y: Parser[A]): Parser[A] = new Parser[A] {
      def parse(workbook: Workbook): Either[ParserError, A] =
        x.parse(workbook) orElse y.parse(workbook)
    }

    def flatMap[A, B](fa: Parser[A])(f: A => Parser[B]): Parser[B] =
      new Parser[B] {
        def parse(workbook: Workbook): Either[ParserError, B] =
          fa.parse(workbook).flatMap(f(_).parse(workbook))
      }

    def tailRecM[A, B](a: A)(f: A => Parser[Either[A, B]]): Parser[B] = new Parser[B] {
      def parse(workbook: Workbook): Either[ParserError, B] = {
        @tailrec
        def loop(thisA: A): Either[ParserError, B] = f(thisA).parse(workbook) match {
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
