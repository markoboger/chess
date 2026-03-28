package chess.model

import chess.controller.io.fen.RegexFenParser
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class IllegalMoveSpec extends AnyWordSpec with Matchers {

  "Illegal Move Detection" when {
    "testing pawn moves" should {
      "reject pawn moving 2 squares diagonally (e2d4)" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("d4"))
        result.isFailed shouldBe true
      }

      "reject pawn moving backwards" in {
        val board = Board.initial
        val result = board.move(Square("e4"), Square("e3"))
        result.isFailed shouldBe true
      }

      "reject pawn moving 3 squares" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("e5"))
        result.isFailed shouldBe true
      }

      "allow pawn moving 2 squares from starting position" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("e4"))
        result.isSuccess shouldBe true
        result.board.pieceAt(Square("e4")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
        result.board.pieceAt(Square("e2")) shouldEqual None
      }

      "reject pawn moving 2 squares from non-starting position" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
        yield b2
        val board = setup.get
        val result = board.move(Square("e4"), Square("e6"))
        result.isFailed shouldBe true
      }

      "reject pawn moving to occupied square" in {
        val board = Board.initial
        val afterE3 = board.move(Square("e2"), Square("e3"))
        afterE3.isSuccess shouldBe true
        val result = afterE3.board.move(Square("e4"), Square("e3"))
        result.isFailed shouldBe true
      }

      "allow pawn capturing diagonally" in {
        val result = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("d5"))
        yield b3
        result.isSuccess shouldBe true
        result.board.pieceAt(Square("d5")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
      }

      "reject pawn capturing non-diagonally" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("e5"))
        yield b3
        setup.isSuccess shouldBe true
        val result = setup.board.move(Square("e5"), Square("d6"))
        result.isFailed shouldBe true
      }
    }

    "testing knight moves" should {
      "reject knight moving in straight line" in {
        val board = Board.initial
        val result = board.move(Square("b1"), Square("b3"))
        result.isFailed shouldBe true
      }
    }

    "testing bishop moves" should {
      "reject bishop moving in straight line" in {
        val board = Board.initial
        val result = board.move(Square("c1"), Square("c3"))
        result.isFailed shouldBe true
      }
    }

    "testing rook moves" should {
      "reject rook moving diagonally" in {
        val board = Board.initial
        val result = board.move(Square("a1"), Square("c3"))
        result.isFailed shouldBe true
      }
    }

    "testing queen moves" should {
      "reject queen moving in L-shape" in {
        val board = Board.initial
        val result = board.move(Square("d1"), Square("f2"))
        result.isFailed shouldBe true
      }
    }

    "testing king moves" should {
      "reject king moving 2 squares" in {
        val board = Board.initial
        val result = board.move(Square("e1"), Square("e3"))
        result.isFailed shouldBe true
      }

      // Regression: isValidCastling used to approve any move in the kingside
      // direction without checking that 'to' is the correct castling square.
      // A king at e1 could therefore "castle" all the way to h8.
      "reject white king teleporting from e1 to h8 even if black rook is there" in {
        // White: King e1, Rook h1 (castling rights intact).  Black: Rook h8, King e8.
        val board = RegexFenParser.parseFEN("4k2r/8/8/8/8/8/8/4K2R").get
        board.move(Square("e1"), Square("h8")).isFailed shouldBe true
      }

      "reject white king teleporting from e1 to h1 (rook square)" in {
        val board = RegexFenParser.parseFEN("4k2r/8/8/8/8/8/8/4K2R").get
        board.move(Square("e1"), Square("h1")).isFailed shouldBe true
      }

      "reject black king teleporting from e8 to h1 even if white rook is there" in {
        // Black to move: give black the move by using a FEN where it is black's turn
        val board = RegexFenParser.parseFEN("4k2r/8/8/8/8/8/8/4K2R").get
        // Force it: ask Board directly whether the move is valid
        board.move(Square("e8"), Square("h1")).isFailed shouldBe true
      }

      "reject white king moving to a non-adjacent, non-castling square (e1 to a8)" in {
        val board = RegexFenParser.parseFEN("r3k3/8/8/8/8/8/8/4K2R").get
        board.move(Square("e1"), Square("a8")).isFailed shouldBe true
      }

      "still allow normal kingside castling (e1 to g1)" in {
        // King e1, Rook h1, f1 and g1 empty, castling rights intact
        val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4K2R").get
        val result = board.move(Square("e1"), Square("g1"))
        result.isSuccess shouldBe true
        result.board.pieceAt(Square("g1")) shouldEqual Some(Piece(Role.King, Color.White))
        result.board.pieceAt(Square("f1")) shouldEqual Some(Piece(Role.Rook, Color.White))
      }

      "legalMoves must not include king teleport from e1 to h8" in {
        val board = RegexFenParser.parseFEN("4k2r/8/8/8/8/8/8/4K2R").get
        val kingMoves = board.legalMoves(Color.White).filter(_._1 == Square("e1"))
        kingMoves.map(_._2) should not contain Square("h8")
      }

      // Regression: white king at e6 could capture the black king on f7 because
      // after the capture the black king was gone from the board, so isInCheck
      // on the resulting position found no attacker and allowed the move.
      "reject white king capturing adjacent black king (e6 takes f7)" in {
        // White: King e6.  Black: King f7.  Kings are diagonally adjacent.
        val board = RegexFenParser.parseFEN("8/5k2/4K3/8/8/8/8/8").get
        board.move(Square("e6"), Square("f7")).isFailed shouldBe true
      }

      "reject white king capturing adjacent black king (d5 takes e5)" in {
        val board = RegexFenParser.parseFEN("8/8/8/3Kk3/8/8/8/8").get
        board.move(Square("d5"), Square("e5")).isFailed shouldBe true
      }

      "reject black king capturing adjacent white king (e5 takes d5)" in {
        val board = RegexFenParser.parseFEN("8/8/8/3Kk3/8/8/8/8").get
        board.move(Square("e5"), Square("d5")).isFailed shouldBe true
      }

      "legalMoves must not include white king capturing the black king" in {
        val board = RegexFenParser.parseFEN("8/5k2/4K3/8/8/8/8/8").get
        val kingMoves = board.legalMoves(Color.White).filter(_._1 == Square("e6"))
        kingMoves.map(_._2) should not contain Square("f7")
      }
    }

    "testing board boundaries" should {
      "reject invalid square coordinates" in {
        // With Square types, invalid coordinates are caught at construction time
        Square.fromCoords(5, 9) shouldEqual None
        Square.fromCoords(0, 4) shouldEqual None
        Square.fromCoords(9, 1) shouldEqual None
      }
    }

    "testing capture rules" should {
      "reject capturing own piece" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("d1"))
        result.isFailed shouldBe true
      }
    }
  }
}
