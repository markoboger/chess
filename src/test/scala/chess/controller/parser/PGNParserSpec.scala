package chess.controller.parser

import chess.model.{Board, Piece, Role, Color, Square, File, Rank}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure, Try}

final class PGNParserSpec extends AnyWordSpec with Matchers:

  "PGNParser" should {
    "parse pawn moves" in {
      val board = Board.initial

      // e4
      val result = PGNParser.parseMove("e4", board, isWhiteToMove = true)
      result should be(Success((Square("e2"), Square("e4"))))
    }

    "parse knight moves" in {
      val board = Board.initial

      // Nf3
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result should be(Success((Square("g1"), Square("f3"))))
    }

    "parse bishop moves" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5

      // Bc4
      val result = PGNParser.parseMove("Bc4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse queen moves" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5

      // Qh5
      val result = PGNParser.parseMove("Qh5", board, isWhiteToMove = true)
      result should be(Success((Square("d1"), Square("h5"))))
    }

    "parse king moves" in {
      val board = Board.initial
        .move(Square("e2"), Square("e3")) // e3
        .move(Square("e7"), Square("e6")) // e6

      // Ke2
      val result = PGNParser.parseMove("Ke2", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("e2"))))
    }

    "parse kingside castling" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5
        .move(Square("g1"), Square("f3")) // Nf3
        .move(Square("g8"), Square("f6")) // Nf6
        .move(Square("f1"), Square("e2")) // Be2
        .move(Square("f8"), Square("e7")) // Be7

      // O-O
      val result = PGNParser.parseMove("O-O", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("g1"))))
    }

    "parse queenside castling" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5
        .move(Square("d1"), Square("d2")) // Qd2
        .move(Square("d8"), Square("d7")) // Qd7
        .move(Square("c1"), Square("c2")) // Bc2
        .move(Square("c8"), Square("c7")) // Bc7
        .move(Square("b1"), Square("b2")) // Nb2
        .move(Square("b8"), Square("b7")) // Nb7
        .move(Square("a1"), Square("a2")) // Ra2
        .move(Square("a8"), Square("a7")) // Ra7

      // O-O-O
      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("c1"))))
    }

    "parse moves with check symbol" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5

      // Qh5+
      val result = PGNParser.parseMove("Qh5+", board, isWhiteToMove = true)
      result should be(Success((Square("d1"), Square("h5"))))
    }

    "parse moves with checkmate symbol" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4")) // e4
        .move(Square("e7"), Square("e5")) // e5

      // Qh5#
      val result = PGNParser.parseMove("Qh5#", board, isWhiteToMove = true)
      result should be(Success((Square("d1"), Square("h5"))))
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
        .move(Square("e2"), Square("e4")) // e4

      // e5 (black's move)
      val result = PGNParser.parseMove("e5", board, isWhiteToMove = false)
      result should be(Success((Square("e7"), Square("e5"))))
    }

    "handle disambiguation with file hint" in {
      val board = Board.initial
        .move(Square("g1"), Square("f3")) // Nf3
        .move(Square("g8"), Square("f6")) // Nf6

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
      result should be(Success((Square("g1"), Square("f3"))))
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
      result should be(Success((Square("e7"), Square("e5"))))
    }

    "parse multiple consecutive moves from FEN" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get

      // First move: Nf3
      val move1 = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      move1.isSuccess shouldBe true

      // Apply move and get new board
      val (from1, to1) = move1.get
      val board2 = board.move(from1, to1)

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
      result should be(Success((Square("e1"), Square("g1"))))
    }

    "parse castling from FEN: queenside castling available" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R"
      val board = FENParser.parseFEN(fen).get

      // O-O-O (queenside castling)
      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("c1"))))
    }

    "fail to parse invalid move from FEN position" in {
      val fen = "8/8/8/8/8/8/8/4K3"
      val board = FENParser.parseFEN(fen).get

      // Try to move a piece that doesn't exist
      val result = PGNParser.parseMove("Nf3", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "parse black kingside castling" in {
      val fen = "rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("O-O", board, isWhiteToMove = false)
      result should be(Success((Square("e8"), Square("g8"))))
    }

    "parse black queenside castling" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = false)
      result should be(Success((Square("e8"), Square("c8"))))
    }

    "parse castling with numeric notation 0-0" in {
      val fen = "rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("0-0", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("g1"))))
    }

    "parse castling with numeric notation 0-0-0" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("0-0-0", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("c1"))))
    }

    "parse pawn capture move" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/3P4/8/PPP1PPPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get

      // dxe5
      val result = PGNParser.parseMove("dxe5", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse move with rank hint disambiguation" in {
      // Position with two rooks on same file - need rank hint
      val fen = "4k3/8/8/8/8/R7/8/R3K3"
      val board = FENParser.parseFEN(fen).get

      // R1a2 - rook from rank 1 to a2
      val result = PGNParser.parseMove("R1a2", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse rook move along rank with path blocked" in {
      // Rook on a1, knight on b1 blocking path to c1
      val fen = "4k3/8/8/8/8/8/8/RN2K3"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("Rc1", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "parse queen diagonal move from FEN" in {
      val fen = "4k3/8/8/8/8/8/8/3QK3"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("Qa4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse queen straight move from FEN" in {
      val fen = "4k3/8/8/8/8/8/8/3QK3"
      val board = FENParser.parseFEN(fen).get

      val result = PGNParser.parseMove("Qd5", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse black pawn double move" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get

      // d5 (black pawn double move)
      val result = PGNParser.parseMove("d5", board, isWhiteToMove = false)
      result.isSuccess shouldBe true
    }

    "parse single pawn forward move" in {
      val board = Board.initial
      // e3 is a single-square pawn move
      val result = PGNParser.parseMove("e3", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "reject rook move to square occupied by own piece" in {
      // White rook on a1, white rook on a3, nothing between - Rook can't capture own piece
      val fen = "4k3/8/8/8/8/R7/8/R3K3"
      val board = FENParser.parseFEN(fen).get
      // Ra3 targets a square with own rook - should fail
      val result = PGNParser.parseMove("Ra3", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }
  }
