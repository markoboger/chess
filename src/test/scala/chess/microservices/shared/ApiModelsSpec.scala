package chess.microservices.shared

import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApiModelsSpec extends AnyWordSpec with Matchers {

  "CreateGameRequest" should {
    "round-trip through JSON with None" in {
      val req = CreateGameRequest(None)
      decode[CreateGameRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
    "round-trip through JSON with Some FEN" in {
      val req = CreateGameRequest(Some("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
      decode[CreateGameRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "CreateGameResponse" should {
    "round-trip through JSON" in {
      val resp = CreateGameResponse("abc-123", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      decode[CreateGameResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "GameStateResponse" should {
    "round-trip through JSON" in {
      val resp = GameStateResponse("abc-123", "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", "1. e4 e5", "in_progress")
      decode[GameStateResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "MakeMoveRequest" should {
    "round-trip through JSON" in {
      val req = MakeMoveRequest("e4")
      decode[MakeMoveRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "MakeMoveResponse" should {
    "round-trip through JSON with no event" in {
      val resp = MakeMoveResponse(success = true, "some-fen", None)
      decode[MakeMoveResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip through JSON with an event" in {
      val resp = MakeMoveResponse(success = true, "some-fen", Some("check"))
      decode[MakeMoveResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip with success=false" in {
      val resp = MakeMoveResponse(success = false, "", None)
      decode[MakeMoveResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "MoveHistoryResponse" should {
    "round-trip through JSON with moves" in {
      val resp = MoveHistoryResponse(Vector("e4", "e5", "Nf3"))
      decode[MoveHistoryResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip through JSON with empty moves" in {
      val resp = MoveHistoryResponse(Vector.empty)
      decode[MoveHistoryResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "FenResponse" should {
    "round-trip through JSON" in {
      val resp = FenResponse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      decode[FenResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "LoadFenRequest" should {
    "round-trip through JSON" in {
      val req = LoadFenRequest("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      decode[LoadFenRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "LoadFenResponse" should {
    "round-trip through JSON success=true" in {
      val resp = LoadFenResponse(success = true, "some-fen")
      decode[LoadFenResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip through JSON success=false" in {
      val resp = LoadFenResponse(success = false, "")
      decode[LoadFenResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "ErrorResponse" should {
    "round-trip through JSON with no details" in {
      val resp = ErrorResponse("something went wrong")
      decode[ErrorResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip through JSON with details" in {
      val resp = ErrorResponse("bad input", Some("field 'fen' is invalid"))
      decode[ErrorResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "HealthResponse" should {
    "round-trip through JSON" in {
      val resp = HealthResponse("ok", "game-service")
      decode[HealthResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }
}
