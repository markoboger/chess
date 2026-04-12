package chess.microservices.shared

import chess.model.GameSettings
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApiModelsSpec extends AnyWordSpec with Matchers {
  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  private val e4Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
  private val e4e5Fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"

  "CreateGameRequest" should {
    "use None as the default start FEN" in {
      CreateGameRequest().startFen shouldBe None
    }

    "decode empty JSON object (Vue client default body)" in {
      decode[CreateGameRequest]("{}") shouldBe Right(CreateGameRequest(None, GameSettings()))
    }

    "round-trip through JSON with None" in {
      val req = CreateGameRequest(None)
      decode[CreateGameRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
    "round-trip through JSON with Some FEN" in {
      val req = CreateGameRequest(Some(startFen))
      decode[CreateGameRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "CreateGameResponse" should {
    "round-trip through JSON" in {
      val resp = CreateGameResponse("abc-123", startFen, GameSettings())
      decode[CreateGameResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "GameStateResponse" should {
    "round-trip through JSON" in {
      val resp = GameStateResponse("abc-123", e4e5Fen, "1. e4 e5", "in_progress", GameSettings())
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
      val resp = FenResponse(startFen)
      decode[FenResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "LoadFenRequest" should {
    "round-trip through JSON" in {
      val req = LoadFenRequest(e4Fen)
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
    "use None as the default details value" in {
      ErrorResponse("something went wrong").details shouldBe None
    }

    "round-trip through JSON with no details" in {
      val resp = ErrorResponse("something went wrong")
      decode[ErrorResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
    "round-trip through JSON with details" in {
      val resp = ErrorResponse("bad input", Some("field 'fen' is invalid"))
      decode[ErrorResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "OpeningLookupResponse" should {
    "round-trip through JSON" in {
      val resp = OpeningLookupResponse("B20", "Sicilian Defence", "1. e4 c5")
      decode[OpeningLookupResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "AiMoveRequest" should {
    "round-trip through JSON" in {
      val req = AiMoveRequest("opening-intelligence")
      decode[AiMoveRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "AiMoveResponse" should {
    "round-trip through JSON with a move" in {
      val resp = AiMoveResponse(Some("Nf3"))
      decode[AiMoveResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }

    "round-trip through JSON without a move" in {
      val resp = AiMoveResponse(None)
      decode[AiMoveResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "GameSummary" should {
    "round-trip through JSON" in {
      val summary = GameSummary("abc-123", "White to move", GameSettings())
      decode[GameSummary](summary.asJson.noSpaces) shouldBe Right(summary)
    }
  }

  "ListGamesResponse" should {
    "round-trip through JSON" in {
      val resp = ListGamesResponse(List(GameSummary("abc-123", "White to move", GameSettings())))
      decode[ListGamesResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "HealthResponse" should {
    "round-trip through JSON" in {
      val resp = HealthResponse("ok", "game-service")
      decode[HealthResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }

  "LoadPgnRequest" should {
    "round-trip through JSON" in {
      val req = LoadPgnRequest("1. e4 e5 2. Nf3 Nc6")
      decode[LoadPgnRequest](req.asJson.noSpaces) shouldBe Right(req)
    }
  }

  "LoadPgnResponse" should {
    "round-trip through JSON" in {
      val resp = LoadPgnResponse(success = true, fen = "some-fen", moves = 4)
      decode[LoadPgnResponse](resp.asJson.noSpaces) shouldBe Right(resp)
    }
  }
}
