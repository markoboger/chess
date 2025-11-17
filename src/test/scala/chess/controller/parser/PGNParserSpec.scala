package chess.controller.parser

import chess.model.{Board, Piece, PieceType, PieceColor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure, Try}

final class PGNParserSpec extends AnyWordSpec with Matchers:

  "PGNParser" should {
    "parse pawn moves" in {
      val board = Board.initial
      
      // e4
      val result = PGNParser.parseMove("e4", board, isWhiteToMove = true)
      result should be(Success((5, 2, 5, 4)))
    }

    "parse knight moves" in {
      val board = Board.initial
      
      // Nf3
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result should be(Success((7, 1, 6, 3)))
    }

    "parse bishop moves" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
      
      // Bc4
      val result = PGNParser.parseMove("Bc4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse queen moves" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
      
      // Qh5
      val result = PGNParser.parseMove("Qh5", board, isWhiteToMove = true)
      result should be(Success((4, 1, 8, 5)))
    }

    "parse king moves" in {
      val board = Board.initial
        .move(5, 2, 5, 3)  // e3
        .move(5, 7, 5, 6)  // e6
      
      // Ke2
      val result = PGNParser.parseMove("Ke2", board, isWhiteToMove = true)
      result should be(Success((5, 1, 5, 2)))
    }

    "parse kingside castling" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
        .move(7, 1, 6, 3)  // Nf3
        .move(7, 8, 6, 6)  // Nf6
        .move(6, 1, 5, 2)  // Be2
        .move(6, 8, 5, 7)  // Be7
      
      // O-O
      val result = PGNParser.parseMove("O-O", board, isWhiteToMove = true)
      result should be(Success((5, 1, 7, 1)))
    }

    "parse queenside castling" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
        .move(4, 1, 4, 2)  // Qe2
        .move(4, 8, 4, 7)  // Qe7
        .move(3, 1, 3, 2)  // Bd2
        .move(3, 8, 3, 7)  // Bd7
        .move(2, 1, 2, 2)  // Bc2
        .move(2, 8, 2, 7)  // Bc7
        .move(1, 1, 1, 2)  // Ra2
        .move(1, 8, 1, 7)  // Ra7
      
      // O-O-O
      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = true)
      result should be(Success((5, 1, 3, 1)))
    }

    "parse moves with check symbol" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
      
      // Qh5+
      val result = PGNParser.parseMove("Qh5+", board, isWhiteToMove = true)
      result should be(Success((4, 1, 8, 5)))
    }

    "parse moves with checkmate symbol" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
        .move(5, 7, 5, 5)  // e5
      
      // Qh5#
      val result = PGNParser.parseMove("Qh5#", board, isWhiteToMove = true)
      result should be(Success((4, 1, 8, 5)))
    }

    "fail on single invalid character" in {
      val board = Board.initial
      
      val result = PGNParser.parseMove("X", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "fail on invalid move format" in {
      val board = Board.initial
      
      val result = PGNParser.parseMove("invalid", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "parse black moves correctly" in {
      val board = Board.initial
        .move(5, 2, 5, 4)  // e4
      
      // e5 (black's move)
      val result = PGNParser.parseMove("e5", board, isWhiteToMove = false)
      result should be(Success((5, 7, 5, 5)))
    }

    "handle disambiguation with file hint" in {
      val board = Board.initial
        .move(7, 1, 6, 3)  // Nf3
        .move(7, 8, 6, 6)  // Nf6
      
      // Nbd2 (knight from b-file to d2)
      val result = PGNParser.parseMove("Nbd2", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }
  }

  "PGNParser with FEN-initialized boards" should {
    "parse moves from FEN: after e4 e5" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      
      // Nf3
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result should be(Success((7, 1, 6, 3)))
    }

    "parse moves from FEN: Italian Game position" in {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
      val board = FENParser.parseFEN(fen).get
      
      // d4 (white's move)
      val result = PGNParser.parseMove("d4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse queen moves from FEN: Sicilian Defense" in {
      val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      
      // Nf3
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse bishop moves from FEN: Ruy Lopez" in {
      val fen = "rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R"
      val board = FENParser.parseFEN(fen).get
      
      // Bb5 (bishop move)
      val result = PGNParser.parseMove("Bb5", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse knight moves from FEN: complex position" in {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
      val board = FENParser.parseFEN(fen).get
      
      // Nc3 (white knight to c3)
      val result = PGNParser.parseMove("Nc3", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse rook moves from FEN: endgame position" in {
      val fen = "8/8/8/4k3/8/8/R7/4K3"
      val board = FENParser.parseFEN(fen).get
      
      // Ra5 (rook move)
      val result = PGNParser.parseMove("Ra5", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse pawn moves from FEN with multiple pawns" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/3PP3/8/PPP2PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      
      // c4 (pawn move)
      val result = PGNParser.parseMove("c4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse black moves from FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      
      // e5 (black's response)
      val result = PGNParser.parseMove("e5", board, isWhiteToMove = false)
      result should be(Success((5, 7, 5, 5)))
    }

    "parse multiple consecutive moves from FEN" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      
      // First move: Nf3
      val move1 = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      move1.isSuccess shouldBe true
      
      // Apply move and get new board
      val (f1, r1, t1, tr1) = move1.get
      val board2 = board.move(f1, r1, t1, tr1)
      
      // Second move: Nc6
      val move2 = PGNParser.parseMove("Nc6", board2, isWhiteToMove = false)
      move2.isSuccess shouldBe true
    }

    "parse moves from FEN: Scholar's Mate setup" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR"
      val board = FENParser.parseFEN(fen).get
      
      // Qh5 (threatening mate)
      val result = PGNParser.parseMove("Qh5", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse moves from FEN: King and Rook endgame" in {
      val fen = "8/8/8/4k3/8/8/R7/4K3"
      val board = FENParser.parseFEN(fen).get
      
      // Ke2 (king move)
      val result = PGNParser.parseMove("Ke2", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse castling from FEN: kingside castling available" in {
      val fen = "rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R"
      val board = FENParser.parseFEN(fen).get
      
      // O-O (kingside castling)
      val result = PGNParser.parseMove("O-O", board, isWhiteToMove = true)
      result should be(Success((5, 1, 7, 1)))
    }

    "parse castling from FEN: queenside castling available" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R"
      val board = FENParser.parseFEN(fen).get
      
      // O-O-O (queenside castling)
      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = true)
      result should be(Success((5, 1, 3, 1)))
    }

    "fail to parse invalid move from FEN position" in {
      val fen = "8/8/8/8/8/8/8/4K3"
      val board = FENParser.parseFEN(fen).get
      
      // Try to move a piece that doesn't exist
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }
  }
