package chess.application.game

import cats.effect.unsafe.implicits.global
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameSessionServiceSpec extends AnyWordSpec with Matchers {
  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()
  private val validFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

  private def service = new GameSessionService()

  "createGame" should {
    "create a new game with initial position by default" in {
      val result = service.createGame().unsafeRunSync()
      result shouldBe a[Right[?, ?]]
      val (gameId, fen) = result.toOption.get
      gameId should not be empty
      fen should include("rnbqkbnr")
    }

    "create a game from a valid FEN" in {
      val result = service.createGame(Some(validFen)).unsafeRunSync()
      result shouldBe a[Right[?, ?]]
      result.toOption.get._2 should include("PPPP1PPP")
    }

    "return Left for an invalid FEN" in {
      val result = service.createGame(Some("not-a-fen")).unsafeRunSync()
      result shouldBe a[Left[?, ?]]
    }

    "generate unique IDs for multiple games" in {
      val svc = service
      val id1 = svc.createGame().unsafeRunSync().toOption.get._1
      val id2 = svc.createGame().unsafeRunSync().toOption.get._1
      id1 should not be id2
    }

    "autonomously advance a CvC game when backendAutoplay is enabled" in {
      val svc = service
      val settings = chess.model.GameSettings(
        whiteIsHuman = false,
        blackIsHuman = false,
        whiteStrategy = "random",
        blackStrategy = "random",
        backendAutoplay = true
      )
      val (gameId, _) = svc.createGame(settings = settings).unsafeRunSync().toOption.get

      Thread.sleep(300)

      val history = svc.getMoveHistory(gameId).unsafeRunSync().get
      history should not be empty
    }
  }

  "getGame" should {
    "return Some for an existing game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      svc.getGame(gameId).unsafeRunSync() shouldBe defined
    }

    "return None for an unknown game ID" in {
      service.getGame("no-such-id").unsafeRunSync() shouldBe None
    }
  }

  "listGames" should {
    "return active sessions with their status and settings" in {
      val svc = service
      val settings = chess.model.GameSettings(whiteIsHuman = false)
      val (gameId, _) = svc.createGame(settings = settings).unsafeRunSync().toOption.get
      val games = svc.listGames.unsafeRunSync()

      games should have size 1
      games.head._1 shouldBe gameId
      games.head._2 should not be empty
      (games.head._3 == settings) shouldBe true
    }
  }

  "deleteAllGames" should {
    "remove every active game session" in {
      val svc = service
      svc.createGame().unsafeRunSync()
      svc.createGame().unsafeRunSync()

      svc.deleteAllGames.unsafeRunSync()

      svc.listGames.unsafeRunSync() shouldBe empty
    }
  }

  "getSettings" should {
    "return the saved game settings" in {
      val svc = service
      val settings = chess.model.GameSettings(whiteStrategy = "random", blackStrategy = "greedy")
      val (gameId, _) = svc.createGame(settings = settings).unsafeRunSync().toOption.get

      svc.getSettings(gameId).unsafeRunSync() shouldBe Some(settings)
    }

    "return None for an unknown game" in {
      service.getSettings("no-such-id").unsafeRunSync() shouldBe None
    }
  }

  "deleteGame" should {
    "return true and remove the game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      svc.deleteGame(gameId).unsafeRunSync() shouldBe true
      svc.getGame(gameId).unsafeRunSync() shouldBe None
    }

    "return false for an unknown game ID" in {
      service.deleteGame("no-such-id").unsafeRunSync() shouldBe false
    }
  }

  "getGameState" should {
    "return FEN, PGN, and status for an existing game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      val state = svc.getGameState(gameId).unsafeRunSync()
      state shouldBe defined
      val (fen, pgn, status, settings) = state.get
      fen should not be empty
      status should not be empty
    }

    "return None for an unknown game" in {
      service.getGameState("no-such-id").unsafeRunSync() shouldBe None
    }
  }

  "makeMove" should {
    "apply a valid move and return new FEN" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      val result = svc.makeMove(gameId, "e4").unsafeRunSync()
      result shouldBe a[Right[?, ?]]
      val (fen, _) = result.toOption.get
      fen should include("PPPP1PPP")
    }

    "return Left for an invalid move" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      val result = svc.makeMove(gameId, "e9").unsafeRunSync()
      result shouldBe a[Left[?, ?]]
    }

    "return Left for an unknown game" in {
      service.makeMove("no-such-id", "e4").unsafeRunSync() shouldBe Left(GameSessionService.GameNotFound)
    }

    "report check event" in {
      // e4 e5 Qh5 Nc6 Qxe5+: Queen on e5 checks Black King on e8 (not checkmate)
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      List("e4", "e5", "Qh5", "Nc6").foreach(m => svc.makeMove(gameId, m).unsafeRunSync())
      val result = svc.makeMove(gameId, "Qxe5").unsafeRunSync()
      result.toOption.map(_._2) shouldBe Some(Some("check"))
    }

    "report checkmate event" in {
      // Scholar's mate: e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      List("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6").foreach(m => svc.makeMove(gameId, m).unsafeRunSync())
      val result = svc.makeMove(gameId, "Qxf7").unsafeRunSync()
      result.toOption.map(_._2) shouldBe Some(Some("checkmate"))
    }

    "report stalemate event" in {
      // Black king a8, White king c7, White queen b1 — Qb6 delivers stalemate
      val svc = service
      val (gameId, _) = svc
        .createGame(
          Some("k7/2K5/8/8/8/8/8/1Q6 w - - 0 1")
        )
        .unsafeRunSync()
        .toOption
        .get
      val result = svc.makeMove(gameId, "Qb6").unsafeRunSync()
      result.toOption.map(_._2) shouldBe Some(Some("stalemate"))
    }

    "report threefold_repetition event" in {
      // Kings-only: White Ke1, Black Kh1.
      // First cycle changes castling rights (Kings move off squares).
      // 2nd and 3rd cycle produce identical "no-castling" position keys.
      // 3rd occurrence is on the 12th move (Kh1 the 3rd time with noCastlingRights).
      val svc = service
      val (gameId, _) = svc
        .createGame(
          Some("8/8/8/8/8/8/8/4K2k w - - 0 1")
        )
        .unsafeRunSync()
        .toOption
        .get
      val cycle = List("Kd1", "Kh2", "Ke1", "Kh1")
      cycle.foreach(m => svc.makeMove(gameId, m).unsafeRunSync()) // moves 1-4
      cycle.foreach(m => svc.makeMove(gameId, m).unsafeRunSync()) // moves 5-8 (2nd occurrence)
      List("Kd1", "Kh2", "Ke1").foreach(m => svc.makeMove(gameId, m).unsafeRunSync()) // moves 9-11
      // Move 12: 3rd occurrence of (Ke1, Kh1, White, noCastling) → ThreefoldRepetition
      val result = svc.makeMove(gameId, "Kh1").unsafeRunSync()
      result.toOption.map(_._2) shouldBe Some(Some("threefold_repetition"))
    }

    "autonomously reply for the AI side when backendAutoplay is enabled" in {
      val svc = service
      val settings = chess.model.GameSettings(
        whiteIsHuman = true,
        blackIsHuman = false,
        blackStrategy = "random",
        backendAutoplay = true
      )
      val (gameId, _) = svc.createGame(settings = settings).unsafeRunSync().toOption.get

      svc.makeMove(gameId, "e4").unsafeRunSync() shouldBe a[Right[?, ?]]
      Thread.sleep(300)

      val history = svc.getMoveHistory(gameId).unsafeRunSync().get
      history.length should be >= 2
    }
  }

  "getMoveHistory" should {
    "return empty vector for a new game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      svc.getMoveHistory(gameId).unsafeRunSync() shouldBe Some(Vector.empty)
    }

    "return moves after playing" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      svc.makeMove(gameId, "e4").unsafeRunSync()
      val history = svc.getMoveHistory(gameId).unsafeRunSync()
      history shouldBe defined
      history.get should not be empty
    }

    "return None for unknown game" in {
      service.getMoveHistory("no-such-id").unsafeRunSync() shouldBe None
    }
  }

  "getFen" should {
    "return FEN for existing game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      svc.getFen(gameId).unsafeRunSync() shouldBe defined
    }

    "return None for unknown game" in {
      service.getFen("no-such-id").unsafeRunSync() shouldBe None
    }
  }

  "loadFen" should {
    "load a valid FEN into an existing game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      val result = svc.loadFen(gameId, validFen).unsafeRunSync()
      result shouldBe a[Right[?, ?]]
    }

    "return Left for invalid FEN" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get
      service.loadFen(gameId, "garbage").unsafeRunSync() shouldBe a[Left[?, ?]]
    }

    "return Left for unknown game" in {
      service.loadFen("no-such-id", "anything").unsafeRunSync() shouldBe Left(GameSessionService.GameNotFound)
    }

    "autonomously continue from a loaded CvC position when backendAutoplay is enabled" in {
      val svc = service
      val settings = chess.model.GameSettings(
        whiteIsHuman = false,
        blackIsHuman = false,
        whiteStrategy = "random",
        blackStrategy = "random",
        backendAutoplay = true
      )
      val (gameId, _) = svc.createGame(settings = settings).unsafeRunSync().toOption.get

      svc.loadFen(gameId, validFen).unsafeRunSync() shouldBe a[Right[?, ?]]
      Thread.sleep(300)

      val history = svc.getMoveHistory(gameId).unsafeRunSync().get
      history should not be empty
    }
  }

  "computeAiMove" should {
    "return a move for a valid game" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get

      val result = svc.computeAiMove(gameId, "random").unsafeRunSync()

      result shouldBe a[Right[?, ?]]
      result.toOption.flatten shouldBe defined
    }

    "fall back to the default strategy for an unknown strategy id" in {
      val svc = service
      val (gameId, _) = svc.createGame().unsafeRunSync().toOption.get

      svc.computeAiMove(gameId, "unknown-strategy").unsafeRunSync() shouldBe a[Right[?, ?]]
    }

    "return Left for an unknown game" in {
      service.computeAiMove("no-such-id", "random").unsafeRunSync() shouldBe Left(GameSessionService.GameNotFound)
    }
  }
}
