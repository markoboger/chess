package chess.persistence.mongodb

import cats.effect.IO
import cats.implicits.*
import chess.model.PersistedGame
import chess.persistence.GameRepository
import io.circe.generic.auto.*
import io.circe.syntax.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.{Filter, Sort}
import org.bson.types.ObjectId

import java.time.Instant
import java.util.UUID

/** MongoDB implementation of GameRepository using mongo4cats and Circe.
  *
  * @param collection
  *   MongoDB collection for games
  */
class MongoGameRepository(collection: MongoCollection[IO, PersistedGame]) extends GameRepository[IO]:

  override def save(game: PersistedGame): IO[PersistedGame] =
    import mongo4cats.models.collection.FindOneAndReplaceOptions
    val updatedGame = game.copy(updatedAt = Instant.now())
    collection
      .findOneAndReplace(
        Filter.eq("id", game.id.toString),
        updatedGame,
        FindOneAndReplaceOptions().upsert(true)
      )
      .as(updatedGame)

  override def findById(id: UUID): IO[Option[PersistedGame]] =
    collection.find(Filter.eq("id", id.toString)).first

  override def findAll(limit: Int = 100, offset: Int = 0): IO[List[PersistedGame]] =
    collection
      .find(Filter.empty)
      .sort(Sort.desc("createdAt"))
      .skip(offset)
      .limit(limit)
      .all
      .map(_.toList)

  override def findByStatus(status: String, limit: Int = 100): IO[List[PersistedGame]] =
    collection
      .find(Filter.eq("status", status))
      .sort(Sort.desc("createdAt"))
      .limit(limit)
      .all
      .map(_.toList)

  override def findByOpening(eco: String, limit: Int = 100): IO[List[PersistedGame]] =
    collection
      .find(Filter.eq("openingEco", eco))
      .sort(Sort.desc("createdAt"))
      .limit(limit)
      .all
      .map(_.toList)

  override def delete(id: UUID): IO[Boolean] =
    collection
      .deleteOne(Filter.eq("id", id.toString))
      .map(_.getDeletedCount > 0)

  override def count(): IO[Long] =
    collection.count(Filter.empty)

  override def deleteAll(): IO[Long] =
    collection.deleteMany(Filter.empty).map(_.getDeletedCount)
