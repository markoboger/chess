package chess.lichess

import chess.model.{Color, PromotableRole, Square}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class UciSpec extends AnyFlatSpec with Matchers:

  "Uci.encode" should "encode e2-e4 and promotion" in {
    Uci.encode(Square("e2"), Square("e4"), None) shouldBe "e2e4"
    Uci.encode(Square("e7"), Square("e8"), Some(PromotableRole.Queen)) shouldBe "e7e8q"
  }

  "Uci.parseSingle" should "round-trip" in {
    Uci.parseSingle("g1f3") shouldBe Right((Square("g1"), Square("f3"), None))
  }

  "Uci.applyMovesFromStart" should "replay 1.e4 e5" in {
    val b = Uci.applyMovesFromStart("e2e4 e7e5").getOrElse(fail())
    b.pieceAt(Square("e4")).map(_.role) shouldBe Some(chess.model.Role.Pawn)
    b.pieceAt(Square("e5")).map(_.role) shouldBe Some(chess.model.Role.Pawn)
  }

  "Uci.sideToMoveAfterPlies" should "alternate" in {
    Uci.sideToMoveAfterPlies(0) shouldBe Color.White
    Uci.sideToMoveAfterPlies(1) shouldBe Color.Black
    Uci.sideToMoveAfterPlies(2) shouldBe Color.White
  }

end UciSpec
