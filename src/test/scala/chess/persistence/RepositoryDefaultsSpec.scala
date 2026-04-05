package chess.persistence

import chess.model.{Opening, PersistedGame}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class RepositoryDefaultsSpec extends AnyWordSpec with Matchers:

  private val sampleOpening = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 1)
  private val sampleGame = PersistedGame.create()

  "OpeningRepository default arguments" should {
    "dispatch omitted limits and offsets through the trait defaults" in {
      val repo = new OpeningRepository[Option]:
        override def save(opening: Opening): Option[Opening] = Some(opening)
        override def saveAll(openings: List[Opening]): Option[Int] = Some(openings.length)
        override def findByEco(eco: String): Option[List[Opening]] = Some(Nil)
        override def findByEcoAndName(eco: String, name: String): Option[Option[Opening]] = Some(None)
        override def findByName(nameQuery: String, limit: Int): Option[List[Opening]] =
          Some(List(sampleOpening.copy(name = s"$nameQuery:$limit")))
        override def findAll(limit: Int, offset: Int): Option[List[Opening]] =
          Some(List(sampleOpening.copy(name = s"$limit:$offset")))
        override def findByMoveCount(maxMoves: Int, limit: Int): Option[List[Opening]] =
          Some(List(sampleOpening.copy(name = s"$maxMoves:$limit")))
        override def findRandom(): Option[Option[Opening]] = Some(Some(sampleOpening))
        override def findByFen(fen: String): Option[Option[Opening]] = Some(None)
        override def count(): Option[Long] = Some(1L)
        override def deleteAll(): Option[Long] = Some(0L)

      repo.findByName("polish") shouldBe Some(List(sampleOpening.copy(name = "polish:50")))
      repo.findAll() shouldBe Some(List(sampleOpening.copy(name = "100:0")))
      repo.findByMoveCount(12) shouldBe Some(List(sampleOpening.copy(name = "12:100")))
    }
  }

  "GameRepository default arguments" should {
    "dispatch omitted limits and offsets through the trait defaults" in {
      val repo = new GameRepository[Option]:
        override def save(game: PersistedGame): Option[PersistedGame] = Some(game)
        override def findById(id: UUID): Option[Option[PersistedGame]] = Some(Some(sampleGame))
        override def findAll(limit: Int, offset: Int): Option[List[PersistedGame]] =
          Some(List(sampleGame.copy(status = s"$limit:$offset")))
        override def findByStatus(status: String, limit: Int): Option[List[PersistedGame]] =
          Some(List(sampleGame.copy(status = s"$status:$limit")))
        override def findByOpening(eco: String, limit: Int): Option[List[PersistedGame]] =
          Some(List(sampleGame.copy(status = s"$eco:$limit")))
        override def delete(id: UUID): Option[Boolean] = Some(true)
        override def count(): Option[Long] = Some(1L)
        override def deleteAll(): Option[Long] = Some(0L)

      repo.findAll() shouldBe Some(List(sampleGame.copy(status = "100:0")))
      repo.findByStatus("InProgress") shouldBe Some(List(sampleGame.copy(status = "InProgress:100")))
      repo.findByOpening("C50") shouldBe Some(List(sampleGame.copy(status = "C50:100")))
    }
  }
