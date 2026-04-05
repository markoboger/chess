package chess.persistence.memory

import cats.effect.IO
import chess.persistence.model.Opening
import chess.persistence.repository.OpeningRepository
import chess.persistence.util.OpeningSeeder

import scala.util.Random

/** In-memory implementation of OpeningRepository.
  *
  * Pre-loads the Lichess openings from TSV resources on first access. No database required.
  */
class InMemoryOpeningRepository(initial: List[Opening] = Nil) extends OpeningRepository[IO]:

  private var store: Map[(String, String), Opening] =
    initial.map(o => (o.eco, o.name) -> o).toMap

  override def save(opening: Opening): IO[Opening] = IO {
    store = store + ((opening.eco, opening.name) -> opening)
    opening
  }

  override def saveAll(openings: List[Opening]): IO[Int] = IO {
    val entries = openings.map(o => (o.eco, o.name) -> o)
    store = store ++ entries
    entries.length
  }

  override def findByEco(eco: String): IO[List[Opening]] = IO {
    store.values.filter(_.eco == eco).toList.sortBy(_.name)
  }

  override def findByEcoAndName(eco: String, name: String): IO[Option[Opening]] = IO {
    store.get((eco, name))
  }

  override def findByName(nameQuery: String, limit: Int = 50): IO[List[Opening]] = IO {
    val q = nameQuery.toLowerCase
    store.values.filter(_.name.toLowerCase.contains(q)).toList.sortBy(_.eco).take(limit)
  }

  override def findAll(limit: Int = 100, offset: Int = 0): IO[List[Opening]] = IO {
    store.values.toList.sortBy(o => (o.eco, o.name)).slice(offset, offset + limit)
  }

  override def findByMoveCount(maxMoves: Int, limit: Int = 100): IO[List[Opening]] = IO {
    store.values.filter(_.moveCount <= maxMoves).toList.sortBy(_.moveCount).take(limit)
  }

  override def findRandom(): IO[Option[Opening]] = IO {
    val values = store.values.toVector
    if values.isEmpty then None
    else Some(values(Random.nextInt(values.length)))
  }

  override def count(): IO[Long] = IO(store.size.toLong)

  override def deleteAll(): IO[Long] = IO {
    val n = store.size.toLong
    store = Map.empty
    n
  }

object InMemoryOpeningRepository:
  /** Creates a repository pre-loaded with all Lichess openings from TSV resources. */
  def fromLichess(): InMemoryOpeningRepository =
    new InMemoryOpeningRepository(OpeningSeeder.parseLichessOpenings())
