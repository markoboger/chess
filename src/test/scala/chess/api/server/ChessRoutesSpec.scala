package chess.api.server

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import chess.api.model.*
import chess.AppBindings.given

class ChessRoutesSpec extends AnyFlatSpec with Matchers:

  val routes = ChessRoutes.routes.orNotFound

  "POST /games" should "create a new game with default starting position" in {
    val request = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))

    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.Ok

    val createResp = response.as[CreateGameResponse].unsafeRunSync()
    createResp.gameId should not be empty
    createResp.fen should include("rnbqkbnr")
  }

  it should "create a new game with custom starting position" in {
    val customFen = "8/8/8/8/8/8/8/4K2R w K - 0 1"
    val request = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(Some(customFen)))

    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.Ok

    val createResp = response.as[CreateGameResponse].unsafeRunSync()
    createResp.fen should startWith("8/8/8/8/8/8/8/4K2R")
  }

  it should "return error for invalid FEN" in {
    val invalidFen = "invalid-fen-string"
    val request = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(Some(invalidFen)))

    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.BadRequest

    val errorResp = response.as[ErrorResponse].unsafeRunSync()
    errorResp.error should include("Invalid FEN")
  }

  "GET /games/:id" should "return game state for existing game" in {
    // First create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Then get the game state
    val getRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/games/$gameId"))
    val response = routes.run(getRequest).unsafeRunSync()
    response.status shouldBe Status.Ok

    val stateResp = response.as[GameStateResponse].unsafeRunSync()
    stateResp.gameId shouldBe gameId
    stateResp.fen should include("rnbqkbnr")
    stateResp.status should not be empty
  }

  it should "return 404 for non-existent game" in {
    val request = Request[IO](Method.GET, uri"/games/non-existent-id")
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.NotFound
  }

  "DELETE /games/:id" should "delete an existing game" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Delete the game
    val deleteRequest = Request[IO](Method.DELETE, Uri.unsafeFromString(s"/games/$gameId"))
    val response = routes.run(deleteRequest).unsafeRunSync()
    response.status shouldBe Status.NoContent

    // Verify game is deleted
    val getRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/games/$gameId"))
    val getResponse = routes.run(getRequest).unsafeRunSync()
    getResponse.status shouldBe Status.NotFound
  }

  it should "return 404 when deleting non-existent game" in {
    val request = Request[IO](Method.DELETE, uri"/games/non-existent-id")
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.NotFound
  }

  "POST /games/:id/moves" should "apply a valid move" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Make a move
    val moveRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/moves"))
      .withEntity(MakeMoveRequest("e4"))
    val response = routes.run(moveRequest).unsafeRunSync()
    response.status shouldBe Status.Ok

    val moveResp = response.as[MakeMoveResponse].unsafeRunSync()
    moveResp.success shouldBe true
    moveResp.fen should include("4P3")
  }

  it should "return error for invalid move" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Try an invalid move
    val moveRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/moves"))
      .withEntity(MakeMoveRequest("e9"))
    val response = routes.run(moveRequest).unsafeRunSync()
    response.status shouldBe Status.BadRequest
  }

  it should "return 404 for non-existent game" in {
    val request = Request[IO](Method.POST, uri"/games/non-existent-id/moves")
      .withEntity(MakeMoveRequest("e4"))
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.BadRequest
  }

  "GET /games/:id/moves" should "return move history" in {
    // Create a game and make some moves
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Make moves
    val moves = List("e4", "e5", "Nf3")
    moves.foreach { move =>
      val moveRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/moves"))
        .withEntity(MakeMoveRequest(move))
      routes.run(moveRequest).unsafeRunSync()
    }

    // Get move history
    val histRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/games/$gameId/moves"))
    val response = routes.run(histRequest).unsafeRunSync()
    response.status shouldBe Status.Ok

    val histResp = response.as[MoveHistoryResponse].unsafeRunSync()
    histResp.moves shouldBe Vector("e4", "e5", "Nf3")
  }

  it should "return 404 for non-existent game" in {
    val request = Request[IO](Method.GET, uri"/games/non-existent-id/moves")
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.NotFound
  }

  "GET /games/:id/fen" should "return current FEN position" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Get FEN
    val fenRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/games/$gameId/fen"))
    val response = routes.run(fenRequest).unsafeRunSync()
    response.status shouldBe Status.Ok

    val fenResp = response.as[FenResponse].unsafeRunSync()
    fenResp.fen should include("rnbqkbnr")
  }

  it should "return 404 for non-existent game" in {
    val request = Request[IO](Method.GET, uri"/games/non-existent-id/fen")
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.NotFound
  }

  "POST /games/:id/fen" should "load a valid FEN position" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Load FEN
    val customFen = "8/8/8/8/8/8/8/4K2R w K - 0 1"
    val fenRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/fen"))
      .withEntity(LoadFenRequest(customFen))
    val response = routes.run(fenRequest).unsafeRunSync()
    response.status shouldBe Status.Ok

    val fenResp = response.as[LoadFenResponse].unsafeRunSync()
    fenResp.success shouldBe true
    fenResp.fen should startWith("8/8/8/8/8/8/8/4K2R")
  }

  it should "return error for invalid FEN" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Try to load invalid FEN
    val fenRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/fen"))
      .withEntity(LoadFenRequest("invalid-fen"))
    val response = routes.run(fenRequest).unsafeRunSync()
    response.status shouldBe Status.BadRequest
  }

  it should "return 404 for non-existent game" in {
    val request = Request[IO](Method.POST, uri"/games/non-existent-id/fen")
      .withEntity(LoadFenRequest("8/8/8/8/8/8/8/4K2R w K - 0 1"))
    val response = routes.run(request).unsafeRunSync()
    response.status shouldBe Status.BadRequest
  }

  "Integration scenario" should "play a complete game sequence" in {
    // Create a game
    val createRequest = Request[IO](Method.POST, uri"/games")
      .withEntity(CreateGameRequest(None))
    val createResp = routes.run(createRequest).unsafeRunSync().as[CreateGameResponse].unsafeRunSync()
    val gameId = createResp.gameId

    // Play Scholar's Mate sequence
    val moves = List("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
    moves.foreach { move =>
      val moveRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/moves"))
        .withEntity(MakeMoveRequest(move))
      val response = routes.run(moveRequest).unsafeRunSync()
      response.status shouldBe Status.Ok
    }

    // Checkmate move
    val checkmateRequest = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/$gameId/moves"))
      .withEntity(MakeMoveRequest("Qxf7"))
    val checkmateResp = routes.run(checkmateRequest).unsafeRunSync()
    checkmateResp.status shouldBe Status.Ok

    val moveResp = checkmateResp.as[MakeMoveResponse].unsafeRunSync()
    moveResp.event shouldBe Some("checkmate")

    // Verify final game state
    val stateRequest = Request[IO](Method.GET, Uri.unsafeFromString(s"/games/$gameId"))
    val stateResp = routes.run(stateRequest).unsafeRunSync().as[GameStateResponse].unsafeRunSync()
    stateResp.status should include("Checkmate")
  }
