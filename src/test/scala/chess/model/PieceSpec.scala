package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class PieceSpec extends AnyWordSpec with Matchers:
  "Piece.toString" should {
    "use unicode glyphs for all white pieces" in {
      val expected = Map(
        PieceType.King -> "♔",
        PieceType.Queen -> "♕",
        PieceType.Rook -> "♖",
        PieceType.Bishop -> "♗",
        PieceType.Knight -> "♘",
        PieceType.Pawn -> "♙"
      )

      expected.foreach { (pieceType, glyph) =>
        Piece(pieceType, PieceColor.White).toString shouldBe glyph
      }
    }

    "use unicode glyphs for all black pieces" in {
      val expected = Map(
        PieceType.King -> "♚",
        PieceType.Queen -> "♛",
        PieceType.Rook -> "♜",
        PieceType.Bishop -> "♝",
        PieceType.Knight -> "♞",
        PieceType.Pawn -> "♟"
      )

      expected.foreach { (pieceType, glyph) =>
        Piece(pieceType, PieceColor.Black).toString shouldBe glyph
      }
    }

    "differ between white and black glyphs for each piece type" in {
      PieceType.values.foreach { pieceType =>
        val whiteGlyph = Piece(pieceType, PieceColor.White).toString
        val blackGlyph = Piece(pieceType, PieceColor.Black).toString

        whiteGlyph should not equal blackGlyph
      }
    }

    "always produce a single-character glyph" in {
      PieceType.values.foreach { pieceType =>
        Piece(pieceType, PieceColor.White).toString.length shouldBe 1
        Piece(pieceType, PieceColor.Black).toString.length shouldBe 1
      }
    }
  }
