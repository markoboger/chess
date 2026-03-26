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
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
      yield b2).get

      // Bc4
      val result = PGNParser.parseMove("Bc4", board, isWhiteToMove = true)
      result.isSuccess shouldBe true
    }

    "parse queen moves" in {
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
      yield b2).get

      // Qh5
      val result = PGNParser.parseMove("Qh5", board, isWhiteToMove = true)
      result should be(Success((Square("d1"), Square("h5"))))
    }

    "parse king moves" in {
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e3"))
        b2 <- b1.move(Square("e7"), Square("e6"))
      yield b2).get

      // Ke2
      val result = PGNParser.parseMove("Ke2", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("e2"))))
    }

    "parse kingside castling" in {
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
        b3 <- b2.move(Square("g1"), Square("f3"))
        b4 <- b3.move(Square("g8"), Square("f6"))
        b5 <- b4.move(Square("f1"), Square("e2"))
        b6 <- b5.move(Square("f8"), Square("e7"))
      yield b6).get

      // O-O
      val result = PGNParser.parseMove("O-O", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("g1"))))
    }

    "parse queenside castling" in {
      // Use FEN to set up a position with queenside castling available
      val board =
        FENParser.parseFEN("r3kbnr/pppppppp/8/8/8/8/PPPPPPPP/R3KBNR").get

      // O-O-O
      val result = PGNParser.parseMove("O-O-O", board, isWhiteToMove = true)
      result should be(Success((Square("e1"), Square("c1"))))
    }

    "parse moves with check symbol" in {
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
      yield b2).get

      // Qh5+
      val result = PGNParser.parseMove("Qh5+", board, isWhiteToMove = true)
      result should be(Success((Square("d1"), Square("h5"))))
    }

    "parse moves with checkmate symbol" in {
      val board = (for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
      yield b2).get

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
      val board = Board.initial.move(Square("e2"), Square("e4")).get

      // e5 (black's move)
      val result = PGNParser.parseMove("e5", board, isWhiteToMove = false)
      result should be(Success((Square("e7"), Square("e5"))))
    }

    "handle disambiguation with file hint" in {
      val board = (for
        b1 <- Board.initial.move(Square("g1"), Square("f3"))
        b2 <- b1.move(Square("g8"), Square("f6"))
      yield b2).get

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
      val board2 = board.move(from1, to1).get

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

    "fail on invalid castling notation" in {
      val board = Board.initial
      val result = PGNParser.parseMove("O-O-O-O", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "fail on invalid piece letter in standard move" in {
      val board = Board.initial
      // 'Z' is not a valid piece
      val result = PGNParser.parseMove("Ze4", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }

    "fail on invalid target square in standard move" in {
      val board = Board.initial
      // 'j9' is not a valid square
      val result = PGNParser.parseMove("Nj9", board, isWhiteToMove = true)
      result.isFailure shouldBe true
    }
  }

  "PGNParser.toAlgebraic" should {
    "convert a pawn move" in {
      val board = Board.initial
      val after = board.move(Square("e2"), Square("e4")).get
      PGNParser.toAlgebraic(
        Square("e2"),
        Square("e4"),
        board,
        after,
        isWhite = true
      ) shouldBe "e4"
    }

    "convert a knight move" in {
      val board = Board.initial
      val after = board.move(Square("g1"), Square("f3")).get
      PGNParser.toAlgebraic(
        Square("g1"),
        Square("f3"),
        board,
        after,
        isWhite = true
      ) shouldBe "Nf3"
    }

    "convert a pawn capture" in {
      // Set up a position where e4 pawn can capture d5 pawn
      val fen = "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("e4"), Square("d5")).get
      PGNParser.toAlgebraic(
        Square("e4"),
        Square("d5"),
        board,
        after,
        isWhite = true
      ) shouldBe "exd5"
    }

    "convert kingside castling" in {
      // Board doesn't implement castling via move(), so construct boards manually
      val before = FENParser.parseFEN("4k3/8/8/8/8/8/8/4K2R").get
      val after = FENParser.parseFEN("4k3/8/8/8/8/8/8/5RK1").get
      PGNParser.toAlgebraic(
        Square("e1"),
        Square("g1"),
        before,
        after,
        isWhite = true
      ) shouldBe "O-O"
    }

    "convert queenside castling" in {
      val before = FENParser.parseFEN("4k3/8/8/8/8/8/8/R3K3").get
      val after = FENParser.parseFEN("4k3/8/8/8/8/8/8/2KR4").get
      PGNParser.toAlgebraic(
        Square("e1"),
        Square("c1"),
        before,
        after,
        isWhite = true
      ) shouldBe "O-O-O"
    }

    "add + for check" in {
      // Rook on a1, king on e1; move rook to e8 checking black king
      val fen = "4k3/8/8/8/8/8/8/R3K3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("a1"), Square("a8")).get
      val pgn = PGNParser.toAlgebraic(
        Square("a1"),
        Square("a8"),
        board,
        after,
        isWhite = true
      )
      pgn shouldBe "Ra8+"
    }

    "add # for checkmate" in {
      // Scholar's mate final move: queen to f7
      val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("f3"), Square("f7")).get
      val pgn = PGNParser.toAlgebraic(
        Square("f3"),
        Square("f7"),
        board,
        after,
        isWhite = true
      )
      pgn shouldBe "Qxf7#"
    }

    "disambiguate with file when two rooks share a rank" in {
      // Two white rooks on a1 and h1, king on b2 (out of the way)
      val fen = "4k3/8/8/8/8/8/1K6/R6R"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("a1"), Square("c1")).get
      val pgn = PGNParser.toAlgebraic(
        Square("a1"),
        Square("c1"),
        board,
        after,
        isWhite = true
      )
      pgn shouldBe "Rac1"
    }

    "disambiguate with rank when two rooks share a file" in {
      // Two white rooks on a1 and a5, king on e1
      val fen = "4k3/8/8/R7/8/8/8/R3K3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("a1"), Square("a3")).get
      val pgn = PGNParser.toAlgebraic(
        Square("a1"),
        Square("a3"),
        board,
        after,
        isWhite = true
      )
      pgn shouldBe "R1a3"
    }

    "convert a king move" in {
      val fen = "4k3/8/8/8/8/8/8/4K3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("e1"), Square("e2")).get
      PGNParser.toAlgebraic(
        Square("e1"),
        Square("e2"),
        board,
        after,
        isWhite = true
      ) shouldBe "Ke2"
    }

    "convert a bishop move" in {
      val fen = "4k3/8/8/8/8/8/8/2B1K3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("c1"), Square("f4")).get
      PGNParser.toAlgebraic(
        Square("c1"),
        Square("f4"),
        board,
        after,
        isWhite = true
      ) shouldBe "Bf4"
    }

    "convert a queen move" in {
      val fen = "4k3/8/8/8/8/8/8/3QK3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("d1"), Square("d5")).get
      PGNParser.toAlgebraic(
        Square("d1"),
        Square("d5"),
        board,
        after,
        isWhite = true
      ) shouldBe "Qd5"
    }

    "add # for castling with checkmate" in {
      // After O-O, rook on f1 checkmates black king on f8
      // f7 empty so rook sees king, e8/g8 blocked by rooks, e7/g7 blocked by pawns
      val before = FENParser.parseFEN("4rkr1/4p1pp/8/8/8/8/8/4K2R").get
      val after = FENParser.parseFEN("4rkr1/4p1pp/8/8/8/8/8/5RK1").get
      val pgn = PGNParser.toAlgebraic(
        Square("e1"),
        Square("g1"),
        before,
        after,
        isWhite = true
      )
      pgn shouldBe "O-O#"
    }

    "add + for castling with check" in {
      // After O-O, rook on f1 gives check to black king on f8
      val before = FENParser.parseFEN("5k2/8/8/8/8/8/8/4K2R").get
      val after = FENParser.parseFEN("5k2/8/8/8/8/8/8/5RK1").get
      val pgn = PGNParser.toAlgebraic(
        Square("e1"),
        Square("g1"),
        before,
        after,
        isWhite = true
      )
      pgn shouldBe "O-O+"
    }

    "disambiguate with file and rank when three queens can reach same square" in {
      // Three white queens: a1, a5, c1 all can reach a3
      // Moving Qa1 to a3: Qa5 shares file a, Qc1 shares rank 1 => need Qa1a3
      val fen = "4k3/8/8/Q7/8/8/8/Q1Q1K3"
      val board = FENParser.parseFEN(fen).get
      val after = board.move(Square("a1"), Square("a3")).get
      val pgn = PGNParser.toAlgebraic(
        Square("a1"),
        Square("a3"),
        board,
        after,
        isWhite = true
      )
      pgn shouldBe "Qa1a3"
    }
  }
