package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OpeningModelSpec extends AnyWordSpec with Matchers {

  private val validFen  = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
  private val validArgs = ("C50", "Italian Game", "1. e4 e5", validFen, 2)

  "Opening.apply" should {
    "create a valid Opening" in {
      val o = Opening(validArgs._1, validArgs._2, validArgs._3, validArgs._4, validArgs._5)
      o.eco       shouldBe "C50"
      o.name      shouldBe "Italian Game"
      o.moves     shouldBe "1. e4 e5"
      o.fen       shouldBe validFen
      o.moveCount shouldBe 2
    }

    "reject invalid ECO code format" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("X99", "Test", "1. e4", validFen, 1)
    }

    "reject ECO code with wrong length" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("A0", "Test", "1. e4", validFen, 1)
    }

    "reject empty name" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("A00", "", "1. e4", validFen, 1)
    }

    "reject empty moves" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("A00", "Polish Opening", "", validFen, 1)
    }

    "reject empty FEN" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("A00", "Polish Opening", "1. b4", "", 1)
    }

    "reject non-positive move count" in {
      an[IllegalArgumentException] should be thrownBy
        Opening("A00", "Polish Opening", "1. b4", validFen, 0)
    }

    "accept all valid ECO letters A through E" in {
      for letter <- List("A", "B", "C", "D", "E") do
        noException should be thrownBy Opening(s"${letter}00", "Test", "1. e4", validFen, 1)
    }
  }

  "Opening.unsafe" should {
    "create an Opening without validation" in {
      val o = Opening.unsafe("Z99", "", "bad moves", "", -1)
      o.eco       shouldBe "Z99"
      o.name      shouldBe ""
      o.moves     shouldBe "bad moves"
      o.moveCount shouldBe -1
    }
  }

  "Opening case class" should {
    "support equality by value" in {
      val o1 = Opening.unsafe("C50", "Italian Game", "1. e4 e5", validFen, 2)
      val o2 = Opening.unsafe("C50", "Italian Game", "1. e4 e5", validFen, 2)
      o1 shouldBe o2
    }

    "produce a useful toString" in {
      val o = Opening.unsafe("C50", "Italian Game", "1. e4 e5", validFen, 2)
      o.toString should include("C50")
      o.toString should include("Italian Game")
    }

    "support copy" in {
      val o1 = Opening.unsafe("C50", "Italian Game", "1. e4 e5", validFen, 2)
      val o2 = o1.copy(name = "Giuoco Piano")
      o2.name shouldBe "Giuoco Piano"
      o2.eco  shouldBe "C50"
    }
  }
}
