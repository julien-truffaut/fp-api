package parser

import cats.syntax.all._
import org.apache.poi.ss.usermodel.Workbook
import org.scalatest.{FreeSpec, Matchers}
import parser.io.load

class ParserTest extends FreeSpec with Matchers {

  val example: Workbook = load("example.xlsx")

  "Reader" - {
    "numeric" in {
      Parser.numeric("ExplorationFee").parse(example) shouldEqual Right(1.4)
    }
    "numeric range" in {
      Parser.numericRange("OilProd").parse(example) shouldEqual Right(List(10.12, 12.34, 8.83, 6.23, 9.18, 12.36, 16.28, 18.25, 20.01))
    }
    "string" in {
      Parser.string("PrimaryProduct").parse(example) shouldEqual Right("Oil")
    }
    "Cartesian" in {
      (Parser.numeric("ExplorationFee") |@| Parser.numeric("PostExplorationFee")).tupled.parse(example) shouldEqual
         Right((1.4, 5.8))
    }
  }

}
