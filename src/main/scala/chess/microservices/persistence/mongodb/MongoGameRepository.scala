package chess.microservices.persistence.mongodb

import cats.effect.IO
import cats.syntax.all.*
import chess.microservices.persistence.GameRepository
import chess.microservices.persistence.domain.PersistedGame
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Filter
import org.bson.types.ObjectId

import java.time.Instant

/** MongoDB implementation of GameRepository
  *
  * Uses mongo4cats for functional access to MongoDB with Circe JSON serialization.
  *
  * @param collection
  *   MongoDB collection for games
  */
class MongoGameRepository(collection: MongoCollection[IO, PersistedGame]) extends GameRepository[IO]:

  override def save(game: PersistedGame): IO[PersistedGame] =
    for
      exists <- findById(game.gameId)
      _ <- exists match
        case Some(_) => collection.replaceOne(Filter.eq("gameId", game.gameId), game)
        case None    => collection.insertOne(game)
    yield game

  override def findById(gameId: String): IO[Option[PersistedGame]] =
    collection.find(Filter.eq("gameId", gameId)).first

  override def delete(gameId: String): IO[Boolean] =
    collection
      .deleteOne(Filter.eq("gameId", gameId))
      .map(_.getDeletedCount > 0)

  override def findAll(): IO[Vector[PersistedGame]] =
    collection.find.all.map(_.toVector)

  override def findByStatus(status: String): IO[Vector[PersistedGame]] =
    collection.find(Filter.eq("status", status)).all.map(_.toVector)

  override def exists(gameId: String): IO[Boolean] =
    collection.count(Filter.eq("gameId", gameId)).map(_ > 0)

  override def deleteAll(): IO[Long] =
    collection.deleteMany(Filter.empty).map(_.getDeletedCount)

object MongoGameRepository:
  /** Circe codecs for PersistedGame
    *
    * These enable automatic JSON serialization/deserialization for MongoDB storage.
    */
  given Encoder[Instant] = Encoder.encodeLong.contramap[Instant](_.toEpochMilli)
  given Decoder[Instant] = Decoder.decodeLong.map(Instant.ofEpochMilli)

  given Encoder[PersistedGame] = deriveEncoder[PersistedGame]
  given Decoder[PersistedGame] = deriveDecoder[PersistedGame]

  /** Create a new MongoGameRepository from a database
    *
    * @param database
    *   MongoDB database instance
    * @param collectionName
    *   Name of the collection (default: "games")
    * @return
    *   New MongoGameRepository instance
    */
  def create(
      database: mongo4cats.database.MongoDatabase[IO],
      collectionName: String = "games"
  ): IO[MongoGameRepository] =
    database.getCollectionWithCodec[PersistedGame](collectionName).map(new MongoGameRepository(_))
