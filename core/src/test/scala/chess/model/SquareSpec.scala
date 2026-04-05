package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SquareSpec extends AnyWordSpec with Matchers:

  "File" should {
    "have correct index and letter for all files" in {
      File.A.index shouldBe 1
      File.A.letter shouldBe 'a'
      File.H.index shouldBe 8
      File.H.letter shouldBe 'h'
    }

    "compute difference between files" in {
      (File.E - File.A) shouldBe 4
      (File.A - File.E) shouldBe -4
      (File.D - File.D) shouldBe 0
    }

    "offset to a valid file" in {
      File.A.offset(1) shouldBe Some(File.B)
      File.D.offset(3) shouldBe Some(File.G)
      File.H.offset(-7) shouldBe Some(File.A)
    }

    "offset to None for out-of-range" in {
      File.A.offset(-1) shouldBe None
      File.H.offset(1) shouldBe None
      File.A.offset(8) shouldBe None
    }

    "construct from valid Int" in {
      File.fromInt(1) shouldBe Some(File.A)
      File.fromInt(4) shouldBe Some(File.D)
      File.fromInt(8) shouldBe Some(File.H)
    }

    "return None from invalid Int" in {
      File.fromInt(0) shouldBe None
      File.fromInt(9) shouldBe None
      File.fromInt(-1) shouldBe None
    }

    "construct from valid Char (lowercase)" in {
      File.fromChar('a') shouldBe Some(File.A)
      File.fromChar('e') shouldBe Some(File.E)
      File.fromChar('h') shouldBe Some(File.H)
    }

    "construct from valid Char (uppercase)" in {
      File.fromChar('A') shouldBe Some(File.A)
      File.fromChar('H') shouldBe Some(File.H)
    }

    "return None from invalid Char" in {
      File.fromChar('i') shouldBe None
      File.fromChar('z') shouldBe None
      File.fromChar('0') shouldBe None
    }

    "contain all 8 files in order" in {
      File.all should have size 8
      File.all.head shouldBe File.A
      File.all.last shouldBe File.H
      File.all.map(_.index) shouldBe (1 to 8).toVector
    }
  }

  "Rank" should {
    "have correct index for all ranks" in {
      Rank._1.index shouldBe 1
      Rank._8.index shouldBe 8
    }

    "compute difference between ranks" in {
      (Rank._5 - Rank._2) shouldBe 3
      (Rank._1 - Rank._8) shouldBe -7
      (Rank._4 - Rank._4) shouldBe 0
    }

    "offset to a valid rank" in {
      Rank._1.offset(1) shouldBe Some(Rank._2)
      Rank._4.offset(4) shouldBe Some(Rank._8)
      Rank._8.offset(-7) shouldBe Some(Rank._1)
    }

    "offset to None for out-of-range" in {
      Rank._1.offset(-1) shouldBe None
      Rank._8.offset(1) shouldBe None
      Rank._1.offset(8) shouldBe None
    }

    "construct from valid Int" in {
      Rank.fromInt(1) shouldBe Some(Rank._1)
      Rank.fromInt(4) shouldBe Some(Rank._4)
      Rank.fromInt(8) shouldBe Some(Rank._8)
    }

    "return None from invalid Int" in {
      Rank.fromInt(0) shouldBe None
      Rank.fromInt(9) shouldBe None
      Rank.fromInt(-1) shouldBe None
    }

    "contain all 8 ranks in order" in {
      Rank.all should have size 8
      Rank.all.head shouldBe Rank._1
      Rank.all.last shouldBe Rank._8
      Rank.all.map(_.index) shouldBe (1 to 8).toVector
    }
  }

  "Square" should {
    "display as algebraic notation via toString" in {
      Square(File.A, Rank._1).toString shouldBe "a1"
      Square(File.E, Rank._4).toString shouldBe "e4"
      Square(File.H, Rank._8).toString shouldBe "h8"
    }

    "construct from valid algebraic notation string" in {
      Square("a1") shouldBe Square(File.A, Rank._1)
      Square("e4") shouldBe Square(File.E, Rank._4)
      Square("h8") shouldBe Square(File.H, Rank._8)
    }

    "throw on invalid notation string" in {
      an[IllegalArgumentException] should be thrownBy Square("z9")
      an[IllegalArgumentException] should be thrownBy Square("")
      an[IllegalArgumentException] should be thrownBy Square("e44")
    }

    "construct from valid coords via fromCoords" in {
      Square.fromCoords(1, 1) shouldBe Some(Square(File.A, Rank._1))
      Square.fromCoords(5, 4) shouldBe Some(Square(File.E, Rank._4))
      Square.fromCoords(8, 8) shouldBe Some(Square(File.H, Rank._8))
    }

    "return None from invalid coords" in {
      Square.fromCoords(0, 1) shouldBe None
      Square.fromCoords(9, 1) shouldBe None
      Square.fromCoords(1, 0) shouldBe None
      Square.fromCoords(1, 9) shouldBe None
    }

    "return None from fromCoords with invalid file but valid rank" in {
      Square.fromCoords(0, 4) shouldBe None
    }

    "return None from fromCoords with valid file but invalid rank" in {
      Square.fromCoords(4, 0) shouldBe None
    }

    "construct from valid string via fromString" in {
      Square.fromString("a1") shouldBe Some(Square(File.A, Rank._1))
      Square.fromString("e4") shouldBe Some(Square(File.E, Rank._4))
      Square.fromString("h8") shouldBe Some(Square(File.H, Rank._8))
    }

    "return None from fromString with wrong length" in {
      Square.fromString("") shouldBe None
      Square.fromString("e") shouldBe None
      Square.fromString("e44") shouldBe None
    }

    "return None from fromString with invalid file char" in {
      Square.fromString("z4") shouldBe None
    }

    "return None from fromString with invalid rank char" in {
      Square.fromString("e0") shouldBe None
      Square.fromString("e9") shouldBe None
    }

    "have all 64 squares" in {
      Square.all should have size 64
    }

    "have all squares in rank-major order" in {
      Square.all.head shouldBe Square(File.A, Rank._1)
      Square.all(8) shouldBe Square(File.A, Rank._2)
      Square.all.last shouldBe Square(File.H, Rank._8)
    }

    "roundtrip through toString and fromString" in {
      Square.all.foreach { sq =>
        Square.fromString(sq.toString) shouldBe Some(sq)
      }
    }

    "roundtrip through fromCoords and index accessors" in {
      Square.all.foreach { sq =>
        Square.fromCoords(sq.file.index, sq.rank.index) shouldBe Some(sq)
      }
    }

    "support equality via case class" in {
      Square(File.E, Rank._4) shouldBe Square(File.E, Rank._4)
      Square(File.E, Rank._4) should not be Square(File.D, Rank._4)
      Square(File.E, Rank._4) should not be Square(File.E, Rank._3)
    }
  }
