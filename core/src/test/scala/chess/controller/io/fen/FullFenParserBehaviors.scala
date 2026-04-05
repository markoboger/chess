package chess.controller.io.fen

import chess.model.{Color, File, Piece, Rank, Role, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.Success

/** Shared test behaviours exercising the full-FEN capabilities of any parser that exposes parseFEN and parseFullFEN. */
trait FullFenParserBehaviors extends AnyWordSpecLike with Matchers:

  /** A parser object that exposes parseFEN and parseFullFEN. */
  type Parser = {
    def parseFEN(input: String): scala.util.Try[chess.model.Board]
    def parseFullFEN(input: String): scala.util.Try[FullFenState]
  }

  def fullFenParser(parserName: String, parser: Parser): Unit =

    s"$parserName.parseFEN with full FEN" should {

      "parse castling rights from a full FEN" in {
        val board = parser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").get
        board.castlingRights.whiteKingside shouldBe true
        board.castlingRights.whiteQueenside shouldBe true
        board.castlingRights.blackKingside shouldBe true
        board.castlingRights.blackQueenside shouldBe true
      }

      "parse partial castling rights" in {
        val board = parser.parseFEN("r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R b KQ - 2 7").get
        board.castlingRights.whiteKingside shouldBe true
        board.castlingRights.whiteQueenside shouldBe true
        board.castlingRights.blackKingside shouldBe false
        board.castlingRights.blackQueenside shouldBe false
      }

      "parse no castling rights (dash)" in {
        val board = parser.parseFEN("8/8/8/8/8/8/8/4K2k w - - 0 1").get
        board.castlingRights.whiteKingside shouldBe false
        board.castlingRights.whiteQueenside shouldBe false
        board.castlingRights.blackKingside shouldBe false
        board.castlingRights.blackQueenside shouldBe false
      }

      "set lastMove from en passant field (white to move)" in {
        // Black just pushed e7→e5, en passant target is e6
        val board = parser.parseFEN("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2").get
        board.lastMove shouldBe Some((Square(File.E, Rank._7), Square(File.E, Rank._5)))
      }

      "set lastMove from en passant field (black to move)" in {
        // White just pushed e2→e4, en passant target is e3
        val board = parser.parseFEN("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1").get
        board.lastMove shouldBe Some((Square(File.E, Rank._2), Square(File.E, Rank._4)))
      }

      "leave lastMove as None when en passant field is dash" in {
        val board = parser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").get
        board.lastMove shouldBe None
      }

      "succeed on board-only FEN (no trailing fields)" in {
        val result = parser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
        result shouldBe a[Success[?]]
        result.get.castlingRights.whiteKingside shouldBe true // default
      }

      "succeed when castling and en passant fields are absent" in {
        val result = parser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w")
        result shouldBe a[Success[?]]
      }

      "fail on completely invalid input" in {
        parser.parseFEN("not valid at all").isFailure shouldBe true
      }
    }

    s"$parserName.parseFullFEN" should {

      "parse the starting position" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").get
        state.whiteToMove shouldBe true
        state.halfmoveClock shouldBe 0
        state.fullmoveNumber shouldBe 1
        state.board.pieceAt(Square("e1")) should contain(Piece(Role.King, Color.White))
        state.board.pieceAt(Square("e8")) should contain(Piece(Role.King, Color.Black))
      }

      "parse black to move" in {
        val state = parser.parseFullFEN("r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R b KQ - 2 7").get
        state.whiteToMove shouldBe false
      }

      "parse halfmove clock and fullmove number" in {
        val state = parser.parseFullFEN("r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R b KQ - 2 7").get
        state.halfmoveClock shouldBe 2
        state.fullmoveNumber shouldBe 7
      }

      "default to white to move when side field is missing" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").get
        state.whiteToMove shouldBe true
      }

      "default halfmove clock to 0 when missing" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -").get
        state.halfmoveClock shouldBe 0
      }

      "default fullmove number to 1 when missing" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -").get
        state.fullmoveNumber shouldBe 1
      }

      "clamp negative halfmove clock to 0" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -3 1").get
        state.halfmoveClock shouldBe 0
      }

      "clamp fullmove number below 1 to 1" in {
        val state = parser.parseFullFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0").get
        state.fullmoveNumber shouldBe 1
      }

      "produce a round-trippable state via FullFen.render" in {
        val fen = "r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R b KQ - 2 7"
        val state = parser.parseFullFEN(fen).get
        val rendered = FullFen.render(state.board, state.whiteToMove, state.halfmoveClock, state.fullmoveNumber)
        val reparsed = parser.parseFullFEN(rendered).get
        reparsed.whiteToMove shouldBe state.whiteToMove
        reparsed.halfmoveClock shouldBe state.halfmoveClock
        reparsed.fullmoveNumber shouldBe state.fullmoveNumber
        reparsed.board.castlingRights shouldBe state.board.castlingRights
      }

      "fail on completely invalid input" in {
        parser.parseFullFEN("not a fen string at all").isFailure shouldBe true
      }
    }
