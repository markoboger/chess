package chess.persistence.memory

import cats.effect.unsafe.implicits.global
import chess.application.opening.OpeningParser
import chess.controller.io.pgn.PGNParser
import chess.controller.strategy.OpeningContinuationStrategy
import chess.model.{Color, Square}
import chess.model.Opening
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InMemoryOpeningRepositorySpec extends AnyWordSpec with Matchers {

  private def opening(eco: String, name: String, moves: String = "1. e4", moveCount: Int = 1) =
    Opening.unsafe(eco, name, moves, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", moveCount)

  private def freshRepo(openings: Opening*) = new InMemoryOpeningRepository(openings.toList)

  "constructor defaults" should {
    "start empty when no initial openings are provided" in {
      new InMemoryOpeningRepository().count().unsafeRunSync() shouldBe 0L
    }
  }

  "save" should {
    "insert a new opening" in {
      val repo = freshRepo()
      val o = opening("C50", "Italian Game")
      repo.save(o).unsafeRunSync() shouldBe o
      repo.count().unsafeRunSync() shouldBe 1
    }

    "overwrite an existing opening with the same (eco, name)" in {
      val o1 = opening("C50", "Italian Game", "1. e4")
      val o2 = opening("C50", "Italian Game", "1. e4 e5")
      val repo = freshRepo(o1)
      repo.save(o2).unsafeRunSync()
      val found = repo.findByEcoAndName("C50", "Italian Game").unsafeRunSync()
      found.get.moves shouldBe "1. e4 e5"
    }
  }

  "saveAll" should {
    "insert multiple openings and return count" in {
      val repo = freshRepo()
      val openings = List(opening("A00", "Polish Opening"), opening("B00", "King's Pawn"))
      repo.saveAll(openings).unsafeRunSync() shouldBe 2
      repo.count().unsafeRunSync() shouldBe 2
    }

    "deduplicate by (eco, name) on repeated saveAll" in {
      val repo = freshRepo()
      repo.saveAll(List(opening("A00", "Polish Opening"))).unsafeRunSync()
      repo.saveAll(List(opening("A00", "Polish Opening", "1. b4 e5"))).unsafeRunSync()
      repo.count().unsafeRunSync() shouldBe 1
    }
  }

  "findByEco" should {
    "return all openings for the given ECO code sorted by name" in {
      val a = opening("C50", "Italian Game")
      val b = opening("C50", "Giuoco Piano")
      val repo = freshRepo(a, b)
      val result = repo.findByEco("C50").unsafeRunSync()
      result.map(_.name) shouldBe List("Giuoco Piano", "Italian Game")
    }

    "return empty list for unknown ECO" in {
      freshRepo().findByEco("Z99").unsafeRunSync() shouldBe empty
    }
  }

  "findByEcoAndName" should {
    "return Some for an existing opening" in {
      val o = opening("B12", "Caro-Kann Defense")
      freshRepo(o).findByEcoAndName("B12", "Caro-Kann Defense").unsafeRunSync() shouldBe Some(o)
    }

    "return None for unknown (eco, name)" in {
      freshRepo().findByEcoAndName("B12", "Unknown").unsafeRunSync() shouldBe None
    }
  }

  "findByName" should {
    "match case-insensitively" in {
      val repo = freshRepo(opening("C50", "Italian Game"), opening("D00", "Queen's Gambit"))
      repo.findByName("italian").unsafeRunSync().map(_.name) shouldBe List("Italian Game")
    }

    "return all matches up to limit" in {
      val repo = freshRepo(
        opening("A00", "Polish Opening"),
        opening("A01", "Polish Opening Variation"),
        opening("D00", "Queen's Gambit")
      )
      val results = repo.findByName("polish", limit = 1).unsafeRunSync()
      results.length shouldBe 1
    }

    "return empty list when nothing matches" in {
      freshRepo(opening("C50", "Italian Game")).findByName("sicilian").unsafeRunSync() shouldBe empty
    }
  }

  "findAll" should {
    "return openings sorted by (eco, name)" in {
      val repo = freshRepo(
        opening("C50", "Italian Game"),
        opening("A00", "Polish Opening"),
        opening("B12", "Caro-Kann Defense")
      )
      val result = repo.findAll(100).unsafeRunSync()
      result.map(_.eco) shouldBe List("A00", "B12", "C50")
    }

    "respect limit and offset for pagination" in {
      val repo = freshRepo(
        opening("A00", "Polish Opening"),
        opening("B12", "Caro-Kann Defense"),
        opening("C50", "Italian Game")
      )
      val page = repo.findAll(limit = 2, offset = 1).unsafeRunSync()
      page.length shouldBe 2
      page.head.eco shouldBe "B12"
    }

    "return empty list when offset exceeds size" in {
      freshRepo(opening("A00", "Polish Opening")).findAll(100, offset = 5).unsafeRunSync() shouldBe empty
    }

    "use default limit and offset when called with no arguments" in {
      val repo = freshRepo(opening("A00", "Polish Opening"), opening("B00", "King's Pawn"))
      repo.findAll().unsafeRunSync().length shouldBe 2
    }
  }

  "findByMoveCount" should {
    "return openings with moveCount <= maxMoves" in {
      val repo = freshRepo(
        opening("A00", "Polish Opening", moveCount = 1),
        opening("C50", "Italian Game", moveCount = 5),
        opening("D00", "Queen's Gambit", moveCount = 3)
      )
      val result = repo.findByMoveCount(maxMoves = 3).unsafeRunSync()
      result.map(_.name) should contain allOf ("Polish Opening", "Queen's Gambit")
      result.map(_.name) should not contain "Italian Game"
    }

    "respect limit" in {
      val repo = freshRepo(
        opening("A00", "Polish Opening", moveCount = 1),
        opening("B00", "King's Pawn", moveCount = 1)
      )
      repo.findByMoveCount(5, limit = 1).unsafeRunSync().length shouldBe 1
    }
  }

  "findRandom" should {
    "return None when empty" in {
      freshRepo().findRandom().unsafeRunSync() shouldBe None
    }

    "return Some when non-empty" in {
      freshRepo(opening("A00", "Polish Opening")).findRandom().unsafeRunSync() shouldBe defined
    }
  }

  "findByFen" should {
    "return the matching opening for an indexed FEN" in {
      val o = opening("C50", "Italian Game")
      freshRepo(o).findByFen(o.fen).unsafeRunSync() shouldBe Some(o)
    }

    "return None when the FEN is unknown" in {
      freshRepo().findByFen("missing-fen").unsafeRunSync() shouldBe None
    }
  }

  "count" should {
    "return 0 for empty repository" in {
      freshRepo().count().unsafeRunSync() shouldBe 0L
    }

    "return correct count" in {
      freshRepo(opening("A00", "Polish Opening"), opening("C50", "Italian Game")).count().unsafeRunSync() shouldBe 2L
    }
  }

  "deleteAll" should {
    "remove all openings and return count" in {
      val repo = freshRepo(opening("A00", "Polish Opening"), opening("C50", "Italian Game"))
      repo.deleteAll().unsafeRunSync() shouldBe 2L
      repo.count().unsafeRunSync() shouldBe 0L
    }

    "return 0 for empty repository" in {
      freshRepo().deleteAll().unsafeRunSync() shouldBe 0L
    }
  }

  "fromLichess" should {
    "load all Lichess openings from TSV resources" in {
      given chess.application.opening.OpeningIO = OpeningParser
      val repo = InMemoryOpeningRepository.fromLichess()
      repo.count().unsafeRunSync() should be > 2000L
    }

    "provide documented black opening moves through the strategy" in {
      given chess.application.opening.OpeningIO = OpeningParser
      val repo = InMemoryOpeningRepository.fromLichess()
      val openings = repo.findAll(limit = 3000).unsafeRunSync()
      val strategy = new OpeningContinuationStrategy(openings)
      val boardAfterE4 = chess.model.Board.initial.move(Square("e2"), Square("e4"), None).toOption.get
      val documentedBlackReplies = openings
        .flatMap(_.moves.split("\\s+").toList.filterNot(token => token.isEmpty || token.matches("\\d+\\.+")).lift(1))
        .flatMap(san => PGNParser.parseMove(san, boardAfterE4, false).toOption.map { case (from, to) => (from, to, None) })
        .toSet
      val selectedMove = strategy.selectMove(boardAfterE4, Color.Black)

      documentedBlackReplies should not be empty
      selectedMove shouldBe defined
      documentedBlackReplies should contain(selectedMove.get)
    }
  }
}
