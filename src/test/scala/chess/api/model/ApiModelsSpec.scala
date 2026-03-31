package chess.api.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class ApiModelsSpec extends AnyFlatSpec with Matchers:

  "CreateGameRequest" should "serialize to JSON" in {
    val request = CreateGameRequest(Some("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
    val json = request.asJson.noSpaces
    json should include("startFen")
  }

  it should "deserialize from JSON" in {
    val json = """{"startFen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}"""
    val result = decode[CreateGameRequest](json)
    result shouldBe Right(CreateGameRequest(Some("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")))
  }

  it should "handle optional startFen as None" in {
    val json = """{}"""
    val result = decode[CreateGameRequest](json)
    result shouldBe Right(CreateGameRequest(None))
  }

  "CreateGameResponse" should "serialize to JSON" in {
    val response = CreateGameResponse("game-123", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    val json = response.asJson.noSpaces
    json should include("gameId")
    json should include("game-123")
    json should include("fen")
  }

  it should "deserialize from JSON" in {
    val json = """{"gameId":"game-123","fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}"""
    val result = decode[CreateGameResponse](json)
    result shouldBe Right(CreateGameResponse("game-123", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
  }

  "GameStateResponse" should "serialize to JSON" in {
    val response = GameStateResponse("game-123", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", "e4", "Black to move")
    val json = response.asJson.noSpaces
    json should include("gameId")
    json should include("fen")
    json should include("pgn")
    json should include("status")
  }

  it should "deserialize from JSON" in {
    val json = """{"gameId":"game-123","fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1","pgn":"e4","status":"Black to move"}"""
    val result = decode[GameStateResponse](json)
    result shouldBe Right(GameStateResponse("game-123", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", "e4", "Black to move"))
  }

  "MakeMoveRequest" should "serialize to JSON" in {
    val request = MakeMoveRequest("e4")
    val json = request.asJson.noSpaces
    json shouldBe """{"move":"e4"}"""
  }

  it should "deserialize from JSON" in {
    val json = """{"move":"Nf3"}"""
    val result = decode[MakeMoveRequest](json)
    result shouldBe Right(MakeMoveRequest("Nf3"))
  }

  "MakeMoveResponse" should "serialize with event" in {
    val response = MakeMoveResponse(success = true, "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", Some("check"))
    val json = response.asJson.noSpaces
    json should include("success")
    json should include("fen")
    json should include("event")
  }

  it should "serialize without event" in {
    val response = MakeMoveResponse(success = true, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", None)
    val json = response.asJson.noSpaces
    json should include("success")
    json should include("fen")
  }

  it should "deserialize from JSON" in {
    val json = """{"success":true,"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1","event":"check"}"""
    val result = decode[MakeMoveResponse](json)
    result shouldBe Right(MakeMoveResponse(true, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", Some("check")))
  }

  "MoveHistoryResponse" should "serialize to JSON" in {
    val response = MoveHistoryResponse(Vector("e4", "e5", "Nf3"))
    val json = response.asJson.noSpaces
    json should include("moves")
    json should include("e4")
    json should include("e5")
    json should include("Nf3")
  }

  it should "deserialize from JSON" in {
    val json = """{"moves":["e4","e5","Nf3","Nc6"]}"""
    val result = decode[MoveHistoryResponse](json)
    result shouldBe Right(MoveHistoryResponse(Vector("e4", "e5", "Nf3", "Nc6")))
  }

  "FenResponse" should "serialize to JSON" in {
    val response = FenResponse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    val json = response.asJson.noSpaces
    json shouldBe """{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}"""
  }

  it should "deserialize from JSON" in {
    val json = """{"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}"""
    val result = decode[FenResponse](json)
    result shouldBe Right(FenResponse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"))
  }

  "LoadFenRequest" should "serialize to JSON" in {
    val request = LoadFenRequest("8/8/8/8/8/8/8/4K2R w K - 0 1")
    val json = request.asJson.noSpaces
    json shouldBe """{"fen":"8/8/8/8/8/8/8/4K2R w K - 0 1"}"""
  }

  it should "deserialize from JSON" in {
    val json = """{"fen":"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"}"""
    val result = decode[LoadFenRequest](json)
    result shouldBe Right(LoadFenRequest("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
  }

  "LoadFenResponse" should "serialize to JSON" in {
    val response = LoadFenResponse(success = true, "8/8/8/8/8/8/8/4K2R w K - 0 1")
    val json = response.asJson.noSpaces
    json should include("success")
    json should include("fen")
  }

  it should "deserialize from JSON" in {
    val json = """{"success":true,"fen":"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"}"""
    val result = decode[LoadFenResponse](json)
    result shouldBe Right(LoadFenResponse(true, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
  }

  "ErrorResponse" should "serialize to JSON with details" in {
    val response = ErrorResponse("Invalid move", Some("The move e9 is not valid"))
    val json = response.asJson.noSpaces
    json should include("error")
    json should include("Invalid move")
    json should include("details")
  }

  it should "serialize to JSON without details" in {
    val response = ErrorResponse("Game not found")
    val json = response.asJson.noSpaces
    json should include("error")
    json should include("Game not found")
  }

  it should "deserialize from JSON" in {
    val json = """{"error":"Invalid FEN","details":"FEN string is malformed"}"""
    val result = decode[ErrorResponse](json)
    result shouldBe Right(ErrorResponse("Invalid FEN", Some("FEN string is malformed")))
  }
