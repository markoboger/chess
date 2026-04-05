package chess.controller.io.fen

import chess.model.{Board, CastlingRights, Color, File, Piece, Rank, Role, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

final class FullFenSpec extends AnyWordSpec with Matchers:

  val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  val midGameFen = "r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R b KQ - 2 7"

  "FullFen.parse" should {

    "parse the starting position" in {
      val result = FullFen.parse(startFen)
      result shouldBe a[Success[?]]
      val state = result.get
      state.whiteToMove shouldBe true
      state.halfmoveClock shouldBe 0
      state.fullmoveNumber shouldBe 1
      state.board.pieceAt(Square("e1")) should contain(Piece(Role.King, Color.White))
      state.board.pieceAt(Square("e8")) should contain(Piece(Role.King, Color.Black))
    }

    "parse black to move" in {
      val state = FullFen.parse(midGameFen).get
      state.whiteToMove shouldBe false
    }

    "parse halfmove clock and fullmove number" in {
      val state = FullFen.parse(midGameFen).get
      state.halfmoveClock shouldBe 2
      state.fullmoveNumber shouldBe 7
    }

    "default to white to move when side field is missing" in {
      val fenNoSide = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val state = FullFen.parse(fenNoSide).get
      state.whiteToMove shouldBe true
    }

    "default halfmove clock to 0 when missing" in {
      val fenPartial = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"
      val state = FullFen.parse(fenPartial).get
      state.halfmoveClock shouldBe 0
    }

    "default fullmove number to 1 when missing" in {
      val fenPartial = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"
      val state = FullFen.parse(fenPartial).get
      state.fullmoveNumber shouldBe 1
    }

    "clamp negative halfmove clock to 0" in {
      val fenNeg = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -3 1"
      val state = FullFen.parse(fenNeg).get
      state.halfmoveClock shouldBe 0
    }

    "clamp fullmove number below 1 to 1" in {
      val fenZero = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0"
      val state = FullFen.parse(fenZero).get
      state.fullmoveNumber shouldBe 1
    }

    "fail on completely invalid FEN" in {
      val result = FullFen.parse("not a fen string at all")
      result.isFailure shouldBe true
    }
  }

  "FullFen.render" should {

    "render the starting position" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val rendered = FullFen.render(board, whiteToMove = true, halfmoveClock = 0, fullmoveNumber = 1)
      rendered should startWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
      rendered should include(" w ")
      rendered should endWith("0 1")
    }

    "render black to move" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val rendered = FullFen.render(board, whiteToMove = false, halfmoveClock = 0, fullmoveNumber = 1)
      rendered should include(" b ")
    }

    "clamp negative halfmove clock to 0 in render" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val rendered = FullFen.render(board, whiteToMove = true, halfmoveClock = -5, fullmoveNumber = 1)
      rendered should endWith("0 1")
    }

    "clamp fullmove number below 1 to 1 in render" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val rendered = FullFen.render(board, whiteToMove = true, halfmoveClock = 0, fullmoveNumber = 0)
      rendered should endWith("0 1")
    }

    "produce a round-trippable FEN" in {
      val state = FullFen.parse(startFen).get
      val rendered = FullFen.render(state.board, state.whiteToMove, state.halfmoveClock, state.fullmoveNumber)
      val reparsed = FullFen.parse(rendered).get
      reparsed.whiteToMove shouldBe state.whiteToMove
      reparsed.halfmoveClock shouldBe state.halfmoveClock
      reparsed.fullmoveNumber shouldBe state.fullmoveNumber
    }
  }

  "FullFen.openingKey" should {

    "return the first 4 space-separated fields" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val key = FullFen.openingKey(board, whiteToMove = true)
      key.split("\\s+").length shouldBe 4
    }

    "not include halfmove clock or fullmove number" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val key = FullFen.openingKey(board, whiteToMove = true)
      key should not endWith "0 1"
    }

    "differ by active color" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val whiteKey = FullFen.openingKey(board, whiteToMove = true)
      val blackKey = FullFen.openingKey(board, whiteToMove = false)
      whiteKey should not equal blackKey
    }
  }

  "FullFen.parseCastling" should {

    "parse full castling rights" in {
      val rights = FullFen.parseCastling("KQkq")
      rights.whiteKingside shouldBe true
      rights.whiteQueenside shouldBe true
      rights.blackKingside shouldBe true
      rights.blackQueenside shouldBe true
    }

    "parse no castling rights from dash" in {
      val rights = FullFen.parseCastling("-")
      rights.whiteKingside shouldBe false
      rights.whiteQueenside shouldBe false
      rights.blackKingside shouldBe false
      rights.blackQueenside shouldBe false
    }

    "parse no castling rights from empty string" in {
      val rights = FullFen.parseCastling("")
      rights.whiteKingside shouldBe false
      rights.whiteQueenside shouldBe false
      rights.blackKingside shouldBe false
      rights.blackQueenside shouldBe false
    }

    "parse partial castling rights (white only)" in {
      val rights = FullFen.parseCastling("KQ")
      rights.whiteKingside shouldBe true
      rights.whiteQueenside shouldBe true
      rights.blackKingside shouldBe false
      rights.blackQueenside shouldBe false
    }

    "parse partial castling rights (kingside only)" in {
      val rights = FullFen.parseCastling("Kk")
      rights.whiteKingside shouldBe true
      rights.whiteQueenside shouldBe false
      rights.blackKingside shouldBe true
      rights.blackQueenside shouldBe false
    }
  }

  "FullFen.parseEnPassantTarget" should {

    "return None for dash" in {
      FullFen.parseEnPassantTarget("-", whiteToMove = true) shouldBe None
    }

    "return None for empty string" in {
      FullFen.parseEnPassantTarget("", whiteToMove = true) shouldBe None
    }

    "return correct squares for white to move (en passant on rank 6)" in {
      // White to move, black pawn just moved from e7→e5, en passant target is e6
      val result = FullFen.parseEnPassantTarget("e6", whiteToMove = true)
      result shouldBe defined
      val (pawnFrom, pawnTo) = result.get
      pawnFrom shouldBe Square(File.E, Rank._7)
      pawnTo shouldBe Square(File.E, Rank._5)
    }

    "return None for white to move but wrong rank (rank 3)" in {
      FullFen.parseEnPassantTarget("e3", whiteToMove = true) shouldBe None
    }

    "return correct squares for black to move (en passant on rank 3)" in {
      // Black to move, white pawn just moved from e2→e4, en passant target is e3
      val result = FullFen.parseEnPassantTarget("e3", whiteToMove = false)
      result shouldBe defined
      val (pawnFrom, pawnTo) = result.get
      pawnFrom shouldBe Square(File.E, Rank._2)
      pawnTo shouldBe Square(File.E, Rank._4)
    }

    "return None for black to move but wrong rank (rank 6)" in {
      FullFen.parseEnPassantTarget("e6", whiteToMove = false) shouldBe None
    }

    "return None for invalid square string" in {
      FullFen.parseEnPassantTarget("z9", whiteToMove = true) shouldBe None
    }
  }

  "FullFen.renderEnPassant" should {

    "render dash when no last move" in {
      val board = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      FullFen.renderEnPassant(board) shouldBe "-"
    }

    "render en passant target square after a white pawn double push" in {
      val initBoard = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val e2 = Square(File.E, Rank._2)
      val e4 = Square(File.E, Rank._4)
      val boardAfterE4 = initBoard.move(e2, e4).board
      val ep = FullFen.renderEnPassant(boardAfterE4)
      ep shouldBe "e3"
    }

    "render en passant target square after a black pawn double push" in {
      val initBoard = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val e7 = Square(File.E, Rank._7)
      val e5 = Square(File.E, Rank._5)
      val boardAfterE5 = initBoard.move(e7, e5).board
      val ep = FullFen.renderEnPassant(boardAfterE5)
      ep shouldBe "e6"
    }

    "render dash after a non-double pawn push" in {
      val initBoard = RegexFenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
      val e2 = Square(File.E, Rank._2)
      val e3 = Square(File.E, Rank._3)
      val boardAfterE3 = initBoard.move(e2, e3).board
      FullFen.renderEnPassant(boardAfterE3) shouldBe "-"
    }
  }
