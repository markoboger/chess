package chess.microservices.persistence.mongodb

import cats.effect.IO
import cats.syntax.all.*
import chess.microservices.persistence.OpeningRepository
import chess.microservices.persistence.domain.Opening
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Filter

/** MongoDB implementation of OpeningRepository
  *
  * Uses mongo4cats for functional access to MongoDB with Circe JSON serialization.
  *
  * @param collection
  *   MongoDB collection for openings
  */
class MongoOpeningRepository(collection: MongoCollection[IO, Opening]) extends OpeningRepository[IO]:

  override def save(opening: Opening): IO[Opening] =
    for
      exists <- findByEco(opening.eco)
      _ <- exists match
        case Some(_) => collection.replaceOne(Filter.eq("eco", opening.eco), opening)
        case None    => collection.insertOne(opening)
    yield opening

  override def saveAll(openings: Vector[Opening]): IO[Long] =
    openings
      .traverse(save)
      .map(_.size.toLong)

  override def findByEco(eco: String): IO[Option[Opening]] =
    collection.find(Filter.eq("eco", eco)).first

  override def findByName(name: String): IO[Vector[Opening]] =
    // Use case-insensitive regex pattern
    collection.find(Filter.regex("name", s"(?i)$name")).all.map(_.toVector)

  override def findByFen(fen: String): IO[Vector[Opening]] =
    collection.find(Filter.eq("fen", fen)).all.map(_.toVector)

  override def findAll(): IO[Vector[Opening]] =
    collection.find.all.map(_.toVector)

  override def count(): IO[Long] =
    collection.count(Filter.empty)

  override def deleteAll(): IO[Long] =
    collection.deleteMany(Filter.empty).map(_.getDeletedCount)

  override def findByMoveCount(moveCount: Int): IO[Vector[Opening]] =
    // Simple heuristic: count the number of move numbers in the moves string
    // A move like "1. e4 e5 2. Nf3" has 2 full moves
    findAll().map { openings =>
      openings.filter { opening =>
        val fullMoves = opening.moves.split('.').length - 1
        fullMoves <= moveCount
      }
    }

object MongoOpeningRepository:
  /** Circe codecs for Opening
    */
  given Encoder[Opening] = deriveEncoder[Opening]
  given Decoder[Opening] = deriveDecoder[Opening]

  /** Create a new MongoOpeningRepository from a database
    *
    * @param database
    *   MongoDB database instance
    * @param collectionName
    *   Name of the collection (default: "openings")
    * @return
    *   New MongoOpeningRepository instance
    */
  def create(
      database: mongo4cats.database.MongoDatabase[IO],
      collectionName: String = "openings"
  ): IO[MongoOpeningRepository] =
    database.getCollectionWithCodec[Opening](collectionName).map(new MongoOpeningRepository(_))
