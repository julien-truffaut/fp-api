package parser

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import org.scalatest.{FreeSpec, Matchers}
import eu.timepit.refined.auto._

class RefinedTest extends FreeSpec with Matchers {

  def isAdult(age: Int Refined Positive): Boolean = age.value >= 18

  "isAdult" in {
    isAdult( 4) shouldEqual false
    isAdult(22) shouldEqual true
  }

}
