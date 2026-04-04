package chess.controller.lichess

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Square, File, Rank, PromotableRole}
import scala.util.{Success, Failure}

class UciHelperSpec extends AnyFlatSpec with Matchers:

  "UciHelper.parseUciMove" should "parse a simple pawn move" in {
    val result = UciHelper.parseUciMove("e2e4")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.E, Rank._2)
    to shouldEqual Square(File.E, Rank._4)
    promotion shouldBe None
  }

  it should "parse a knight move" in {
    val result = UciHelper.parseUciMove("g1f3")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.G, Rank._1)
    to shouldEqual Square(File.F, Rank._3)
    promotion shouldBe None
  }

  it should "parse a castling move (kingside)" in {
    val result = UciHelper.parseUciMove("e1g1")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.E, Rank._1)
    to shouldEqual Square(File.G, Rank._1)
    promotion shouldBe None
  }

  it should "parse a castling move (queenside)" in {
    val result = UciHelper.parseUciMove("e1c1")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.E, Rank._1)
    to shouldEqual Square(File.C, Rank._1)
    promotion shouldBe None
  }

  it should "parse a pawn promotion to queen" in {
    val result = UciHelper.parseUciMove("e7e8q")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.E, Rank._7)
    to shouldEqual Square(File.E, Rank._8)
    promotion shouldBe Some(PromotableRole.Queen)
  }

  it should "parse a pawn promotion to rook" in {
    val result = UciHelper.parseUciMove("a7a8r")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.A, Rank._7)
    to shouldEqual Square(File.A, Rank._8)
    promotion shouldBe Some(PromotableRole.Rook)
  }

  it should "parse a pawn promotion to bishop" in {
    val result = UciHelper.parseUciMove("h7h8b")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.H, Rank._7)
    to shouldEqual Square(File.H, Rank._8)
    promotion shouldBe Some(PromotableRole.Bishop)
  }

  it should "parse a pawn promotion to knight" in {
    val result = UciHelper.parseUciMove("b7b8n")
    result should be a Symbol("success")

    val (from, to, promotion) = result.get
    from shouldEqual Square(File.B, Rank._7)
    to shouldEqual Square(File.B, Rank._8)
    promotion shouldBe Some(PromotableRole.Knight)
  }

  it should "fail on invalid move string (too short)" in {
    val result = UciHelper.parseUciMove("e2e")
    result should be a Symbol("failure")
  }

  it should "fail on invalid file" in {
    val result = UciHelper.parseUciMove("x2e4")
    result should be a Symbol("failure")
  }

  it should "fail on invalid rank" in {
    val result = UciHelper.parseUciMove("e9e4")
    result should be a Symbol("failure")
  }

  it should "fail on invalid promotion piece" in {
    val result = UciHelper.parseUciMove("e7e8k")
    result should be a Symbol("failure")
  }

  "UciHelper.squareToUci" should "convert square to UCI notation" in {
    UciHelper.squareToUci(Square(File.E, Rank._2)) shouldEqual "e2"
    UciHelper.squareToUci(Square(File.A, Rank._1)) shouldEqual "a1"
    UciHelper.squareToUci(Square(File.H, Rank._8)) shouldEqual "h8"
    UciHelper.squareToUci(Square(File.D, Rank._4)) shouldEqual "d4"
  }

  "UciHelper.moveToUci" should "convert a simple move to UCI notation" in {
    val from = Square(File.E, Rank._2)
    val to = Square(File.E, Rank._4)
    UciHelper.moveToUci(from, to) shouldEqual "e2e4"
  }

  it should "convert a move with promotion to UCI notation" in {
    val from = Square(File.E, Rank._7)
    val to = Square(File.E, Rank._8)
    UciHelper.moveToUci(from, to, Some(PromotableRole.Queen)) shouldEqual "e7e8q"
    UciHelper.moveToUci(from, to, Some(PromotableRole.Rook)) shouldEqual "e7e8r"
    UciHelper.moveToUci(from, to, Some(PromotableRole.Bishop)) shouldEqual "e7e8b"
    UciHelper.moveToUci(from, to, Some(PromotableRole.Knight)) shouldEqual "e7e8n"
  }

  it should "round-trip parse and convert" in {
    val testMoves = List("e2e4", "g1f3", "e7e8q", "a1h8", "h7h8n")

    testMoves.foreach { uciMove =>
      val parsed = UciHelper.parseUciMove(uciMove).get
      val (from, to, promotion) = parsed
      val converted = UciHelper.moveToUci(from, to, promotion)
      converted shouldEqual uciMove
    }
  }
