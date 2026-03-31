package chess.api.pekko

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.server.Route
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chess.api.model.*
import chess.api.pekko.server.ChessRoutesPekko
import chess.AppBindings.given
import io.circe.syntax.*
import io.circe.parser.*
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}

class ChessRoutesPekkoSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest:

  val routes: Route = ChessRoutesPekko.routes

  // Helper to create JSON entity
  def jsonEntity[A: io.circe.Encoder](value: A): HttpEntity.Strict =
    HttpEntity(ContentTypes.`application/json`, value.asJson.noSpaces)

  // Helper to parse JSON response
  def parseJson[A: io.circe.Decoder](json: String): A =
    decode[A](json) match
      case Right(value) => value
      case Left(error)  => fail(s"JSON decode error: $error")

  "POST /games" should "create a new game with default starting position" in {
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[CreateGameResponse](responseAs[String])
      response.gameId should not be empty
      response.fen should include("rnbqkbnr")
    }
  }

  it should "create a new game with custom starting position" in {
    val customFen = "8/8/8/8/8/8/8/4K2R w K - 0 1"
    Post("/games", jsonEntity(CreateGameRequest(Some(customFen)))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[CreateGameResponse](responseAs[String])
      response.fen should startWith("8/8/8/8/8/8/8/4K2R")
    }
  }

  it should "return error for invalid FEN" in {
    val invalidFen = "invalid-fen-string"
    Post("/games", jsonEntity(CreateGameRequest(Some(invalidFen)))) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      val response = parseJson[ErrorResponse](responseAs[String])
      response.error should include("Invalid FEN")
    }
  }

  "GET /games/:id" should "return game state for existing game" in {
    // First create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Then get the game state
    Get(s"/games/$gameId") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[GameStateResponse](responseAs[String])
      response.gameId shouldBe gameId
      response.fen should include("rnbqkbnr")
      response.status should not be empty
    }
  }

  it should "return 404 for non-existent game" in {
    Get("/games/non-existent-id") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  "DELETE /games/:id" should "delete an existing game" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Delete the game
    Delete(s"/games/$gameId") ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    // Verify game is deleted
    Get(s"/games/$gameId") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "return 404 when deleting non-existent game" in {
    Delete("/games/non-existent-id") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  "POST /games/:id/moves" should "apply a valid move" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Make a move
    Post(s"/games/$gameId/moves", jsonEntity(MakeMoveRequest("e4"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[MakeMoveResponse](responseAs[String])
      response.success shouldBe true
      response.fen should include("4P3")
    }
  }

  it should "return error for invalid move" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Try an invalid move
    Post(s"/games/$gameId/moves", jsonEntity(MakeMoveRequest("e9"))) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "return error for non-existent game" in {
    Post("/games/non-existent-id/moves", jsonEntity(MakeMoveRequest("e4"))) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  "GET /games/:id/moves" should "return move history" in {
    // Create a game and make some moves
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Make moves
    val moves = List("e4", "e5", "Nf3")
    moves.foreach { move =>
      Post(s"/games/$gameId/moves", jsonEntity(MakeMoveRequest(move))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    // Get move history
    Get(s"/games/$gameId/moves") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[MoveHistoryResponse](responseAs[String])
      response.moves should contain allOf ("e4", "e5", "Nf3")
    }
  }

  it should "return 404 for non-existent game" in {
    Get("/games/non-existent-id/moves") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  "GET /games/:id/fen" should "return current FEN position" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Get FEN
    Get(s"/games/$gameId/fen") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[FenResponse](responseAs[String])
      response.fen should include("rnbqkbnr")
    }
  }

  it should "return 404 for non-existent game" in {
    Get("/games/non-existent-id/fen") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  "POST /games/:id/fen" should "load position from FEN" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Load new position
    val newFen = "8/8/8/8/8/8/8/4K2R w K - 0 1"
    Post(s"/games/$gameId/fen", jsonEntity(LoadFenRequest(newFen))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[LoadFenResponse](responseAs[String])
      response.success shouldBe true
      response.fen should startWith("8/8/8/8/8/8/8/4K2R")
    }
  }

  it should "return error for invalid FEN" in {
    // Create a game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Try to load invalid FEN
    val invalidFen = "invalid-fen"
    Post(s"/games/$gameId/fen", jsonEntity(LoadFenRequest(invalidFen))) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "return 404 for non-existent game" in {
    val validFen = "8/8/8/8/8/8/8/4K2R w K - 0 1"
    Post("/games/non-existent-id/fen", jsonEntity(LoadFenRequest(validFen))) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  "Integration scenario" should "play a complete game sequence" in {
    // Create game
    var gameId: String = ""
    Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[CreateGameResponse](responseAs[String])
      gameId = response.gameId
    }

    // Play Scholar's Mate
    val scholarsMate = List("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7")
    scholarsMate.dropRight(1).foreach { move =>
      Post(s"/games/$gameId/moves", jsonEntity(MakeMoveRequest(move))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    // Final move should result in checkmate
    Post(s"/games/$gameId/moves", jsonEntity(MakeMoveRequest("Qxf7"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[MakeMoveResponse](responseAs[String])
      response.event shouldBe Some("checkmate")
    }

    // Verify game state shows checkmate
    Get(s"/games/$gameId") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = parseJson[GameStateResponse](responseAs[String])
      response.status.toLowerCase should include("checkmate")
    }
  }
