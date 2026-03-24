package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class PieceSpec extends AnyWordSpec with Matchers:
  "Piece.toString" should {
    "use unicode glyphs for all white pieces" in {
      val expected = Map(
        Role.King -> "♔",
        Role.Queen -> "♕",
        Role.Rook -> "♖",
        Role.Bishop -> "♗",
        Role.Knight -> "♘",
        Role.Pawn -> "♙"
      )

      expected.foreach { (pieceType, glyph) =>
        Piece(pieceType, Color.White).toString shouldBe glyph
      }
    }

    "use unicode glyphs for all black pieces" in {
      val expected = Map(
        Role.King -> "♚",
        Role.Queen -> "♛",
        Role.Rook -> "♜",
        Role.Bishop -> "♝",
        Role.Knight -> "♞",
        Role.Pawn -> "♟"
      )

      expected.foreach { (pieceType, glyph) =>
        Piece(pieceType, Color.Black).toString shouldBe glyph
      }
    }

    "differ between white and black glyphs for each piece type" in {
      Role.values.foreach { pieceType =>
        val whiteGlyph = Piece(pieceType, Color.White).toString
        val blackGlyph = Piece(pieceType, Color.Black).toString

        whiteGlyph should not equal blackGlyph
      }
    }

    "always produce a single-character glyph" in {
      Role.values.foreach { pieceType =>
        Piece(pieceType, Color.White).toString.length shouldBe 1
        Piece(pieceType, Color.Black).toString.length shouldBe 1
      }
    }
  }

  "Color" should {
    "have exactly two values" in {
      Color.values.length shouldBe 2
    }

    "fold to the correct value for White" in {
      Color.White.fold("w", "b") shouldBe "w"
    }

    "fold to the correct value for Black" in {
      Color.Black.fold("w", "b") shouldBe "b"
    }

    "return the opposite color" in {
      Color.White.opposite shouldBe Color.Black
      Color.Black.opposite shouldBe Color.White
    }

    "be its own double opposite" in {
      Color.values.foreach { c =>
        c.opposite.opposite shouldBe c
      }
    }
  }

  "Role" should {
    "have exactly six values" in {
      Role.values.length shouldBe 6
    }

    "provide all roles via Role.all" in {
      Role.all should have length 6
      Role.all should contain allOf (Role.King, Role.Queen, Role.Rook, Role.Bishop, Role.Knight, Role.Pawn)
    }

    "report King and Pawn as non-promotable" in {
      Role.King.isPromotable shouldBe false
      Role.Pawn.isPromotable shouldBe false
    }

    "report Queen, Rook, Bishop, Knight as promotable" in {
      Role.Queen.isPromotable shouldBe true
      Role.Rook.isPromotable shouldBe true
      Role.Bishop.isPromotable shouldBe true
      Role.Knight.isPromotable shouldBe true
    }

    "have distinct white and black symbols for each role" in {
      Role.values.foreach { role =>
        role.whiteSymbol should not equal role.blackSymbol
      }
    }
  }

  "PromotableRole" should {
    "have exactly four values" in {
      PromotableRole.values.length shouldBe 4
    }

    "provide all via PromotableRole.all" in {
      PromotableRole.all should have length 4
      PromotableRole.all should contain allOf (
        PromotableRole.Queen, PromotableRole.Rook,
        PromotableRole.Bishop, PromotableRole.Knight
      )
    }

    "convert to the corresponding Role" in {
      PromotableRole.Queen.toRole shouldBe Role.Queen
      PromotableRole.Rook.toRole shouldBe Role.Rook
      PromotableRole.Bishop.toRole shouldBe Role.Bishop
      PromotableRole.Knight.toRole shouldBe Role.Knight
    }

    "round-trip from Role and back" in {
      PromotableRole.values.foreach { pr =>
        PromotableRole.fromRole(pr.toRole) shouldBe Some(pr)
      }
    }

    "return None for non-promotable roles" in {
      PromotableRole.fromRole(Role.King) shouldBe None
      PromotableRole.fromRole(Role.Pawn) shouldBe None
    }
  }
