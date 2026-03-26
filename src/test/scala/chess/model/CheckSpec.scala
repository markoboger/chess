package chess.model

import chess.io.fen.RegexFenParser
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CheckSpec extends AnyWordSpec with Matchers:

  "findKing" should {
    "find white king on initial board" in {
      val board = Board.initial
      board.findKing(Color.White) shouldBe Some(Square("e1"))
    }

    "find black king on initial board" in {
      val board = Board.initial
      board.findKing(Color.Black) shouldBe Some(Square("e8"))
    }

    "find king on a custom position" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3K4/8/8/8").get
      board.findKing(Color.White) shouldBe Some(Square("d4"))
    }

    "return None when no king is present" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/8").get
      board.findKing(Color.White) shouldBe None
    }
  }

  "isAttackedBy" should {
    "detect pawn attack from white" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/4P3/8/4K3").get
      board.isAttackedBy(Square("d4"), Color.White) shouldBe true
      board.isAttackedBy(Square("f4"), Color.White) shouldBe true
    }

    "not detect pawn attack straight ahead" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/4P3/8/4K3").get
      board.isAttackedBy(Square("e4"), Color.White) shouldBe false
    }

    "detect pawn attack from black" in {
      val board = RegexFenParser.parseFEN("4k3/8/4p3/8/8/8/8/8").get
      board.isAttackedBy(Square("d5"), Color.Black) shouldBe true
      board.isAttackedBy(Square("f5"), Color.Black) shouldBe true
    }

    "detect knight attack" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3N4/8/8/4K3").get
      board.isAttackedBy(Square("e6"), Color.White) shouldBe true
      board.isAttackedBy(Square("f5"), Color.White) shouldBe true
      board.isAttackedBy(Square("e5"), Color.White) shouldBe false
    }

    "detect bishop attack" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3B4/8/8/4K3").get
      board.isAttackedBy(Square("f6"), Color.White) shouldBe true
      board.isAttackedBy(Square("a1"), Color.White) shouldBe true
    }

    "detect rook attack" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3R4/8/8/4K3").get
      board.isAttackedBy(Square("d8"), Color.White) shouldBe true
      board.isAttackedBy(Square("a4"), Color.White) shouldBe true
    }

    "detect queen attack on diagonal" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3Q4/8/8/4K3").get
      board.isAttackedBy(Square("g7"), Color.White) shouldBe true
    }

    "detect queen attack on rank" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3Q4/8/8/4K3").get
      board.isAttackedBy(Square("h4"), Color.White) shouldBe true
    }

    "detect king attack" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/3K4/8/8/8").get
      board.isAttackedBy(Square("e5"), Color.White) shouldBe true
      board.isAttackedBy(Square("d5"), Color.White) shouldBe true
      board.isAttackedBy(Square("e6"), Color.White) shouldBe false
    }

    "not detect attack through blocking piece" in {
      // Rook on a1, own pawn on a4 blocks attack on a5
      val board = RegexFenParser.parseFEN("8/8/8/8/P7/8/8/R3K3").get
      board.isAttackedBy(Square("a5"), Color.White) shouldBe false
      board.isAttackedBy(Square("a3"), Color.White) shouldBe true
    }
  }

  "isInCheck" should {
    "return false for initial position" in {
      Board.initial.isInCheck(Color.White) shouldBe false
      Board.initial.isInCheck(Color.Black) shouldBe false
    }

    "detect check by rook" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4R3").get
      board.isInCheck(Color.Black) shouldBe true
    }

    "detect check by bishop" in {
      // Bishop on b5 attacks e8 diagonally
      val board = RegexFenParser.parseFEN("4k3/8/8/1B6/8/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe true
    }

    "detect check by knight" in {
      val board = RegexFenParser.parseFEN("4k3/8/5N2/8/8/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe true
    }

    "detect check by pawn" in {
      val board = RegexFenParser.parseFEN("4k3/3P4/8/8/8/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe true
    }

    "detect check by queen" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/4Q3/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe true
    }

    "not detect check when line is blocked" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/4p3/4R3/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe false
    }

    "return false when no king present" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/4R3/8/8/4K3").get
      board.isInCheck(Color.Black) shouldBe false
    }
  }

  "move with check validation" should {
    "reject a move that leaves own king in check" in {
      // White king on e1, white rook on e2 shields from black rook on e8
      // Moving the rook away from e-file exposes the king
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/4R3/4K3").get
      val result = board.move(Square("e2"), Square("a2"))
      result.isFailed shouldBe true
    }

    "allow a move that does not leave king in check" in {
      // Same position, rook moves along the e-file (still shields)
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/4R3/4K3").get
      val result = board.move(Square("e2"), Square("e5"))
      result.isSuccess shouldBe true
    }

    "reject a king move into check" in {
      // White king on e1, black rook on d8 — Kd1 walks into the rook's line
      val board = RegexFenParser.parseFEN("3r4/8/8/8/8/8/8/4K3").get
      val result = board.move(Square("e1"), Square("d1"))
      result.isFailed shouldBe true
    }

    "allow a king move out of check" in {
      // White king on e1 in check from black rook on e8, can escape to d1
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/8/4K3").get
      board.isInCheck(Color.White) shouldBe true
      val result = board.move(Square("e1"), Square("d1"))
      result.isSuccess shouldBe true
    }

    "allow capturing the checking piece" in {
      // White king on e1, white bishop on b1, black rook on e4 gives check
      // Bishop on b1 can capture rook on e4 diagonally
      val board = RegexFenParser.parseFEN("8/8/8/8/4r3/8/8/1B2K3").get
      board.isInCheck(Color.White) shouldBe true
      val result = board.move(Square("b1"), Square("e4"))
      result.isSuccess shouldBe true
      result.board.isInCheck(Color.White) shouldBe false
    }

    "allow blocking a check" in {
      // White king on e1, white rook on a2, black rook on e8 gives check
      // White rook blocks by moving to e2
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/R7/4K3").get
      board.isInCheck(Color.White) shouldBe true
      val result = board.move(Square("a2"), Square("e2"))
      result.isSuccess shouldBe true
      result.board.isInCheck(Color.White) shouldBe false
    }
  }

  "isCheckmate" should {
    "detect back-rank mate" in {
      // Black king on g8, white rook on a8, white queen on d8
      // Pawns on f7, g7, h7 block escape
      val board = RegexFenParser.parseFEN("3Q2k1/5ppp/8/8/8/8/8/4K3").get
      board.isCheckmate(Color.Black) shouldBe true
    }

    "detect scholar's mate pattern" in {
      // Correct scholar's mate: queen on f7, bishop on c4, black queen blocks d8
      val board = RegexFenParser
        .parseFEN("r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR")
        .get
      board.isCheckmate(Color.Black) shouldBe true
    }

    "not report checkmate when king can escape" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4RK2").get
      board.isInCheck(Color.Black) shouldBe true
      board.isCheckmate(Color.Black) shouldBe false
    }

    "not report checkmate when not in check" in {
      Board.initial.isCheckmate(Color.White) shouldBe false
    }

    "not report checkmate when check can be blocked" in {
      // Black king on e8, white rook on e1, but black rook on a5 can block on e5
      val board = RegexFenParser.parseFEN("4k3/8/8/r7/8/8/8/4RK2").get
      board.isInCheck(Color.Black) shouldBe true
      board.isCheckmate(Color.Black) shouldBe false
    }
  }

  "isStalemate" should {
    "detect stalemate when king has no legal moves" in {
      // Black king on a8, white queen on b6, white king on c8
      // King can't move anywhere but is not in check
      val board = RegexFenParser.parseFEN("k7/8/1Q6/8/8/8/8/2K5").get
      board.isInCheck(Color.Black) shouldBe false
      board.isStalemate(Color.Black) shouldBe true
    }

    "not report stalemate for initial position" in {
      Board.initial.isStalemate(Color.White) shouldBe false
    }

    "not report stalemate when in check" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4RK2").get
      board.isInCheck(Color.Black) shouldBe true
      board.isStalemate(Color.Black) shouldBe false
    }
  }

  "legalMoves" should {
    "have moves for both sides from initial position" in {
      val board = Board.initial
      board.legalMoves(Color.White) should not be empty
      board.legalMoves(Color.Black) should not be empty
    }

    "have exactly 20 opening moves for white" in {
      // 16 pawn moves + 4 knight moves
      Board.initial.legalMoves(Color.White) should have length 20
    }

    "have no legal moves in checkmate" in {
      val board = RegexFenParser.parseFEN("3Q2k1/5ppp/8/8/8/8/8/4K3").get
      board.legalMoves(Color.Black) shouldBe empty
    }

    "have no legal moves in stalemate" in {
      val board = RegexFenParser.parseFEN("k7/8/1Q6/8/8/8/8/2K5").get
      board.legalMoves(Color.Black) shouldBe empty
    }

    "only include moves that don't leave king in check" in {
      // White king on e1, pinned rook on e2, black rook on e8
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/4R3/4K3").get
      val moves = board.legalMoves(Color.White)
      // Rook on e2 can only move along e-file (stays in the pin line)
      val rookMoves = moves.filter(_._1 == Square("e2"))
      rookMoves.foreach { case (_, to) =>
        to.file shouldBe File.E
      }
    }
  }
