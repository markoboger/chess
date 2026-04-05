package chess.persistence.mongodb

import cats.effect.IO
import cats.implicits.*
import chess.persistence.model.Opening
import chess.persistence.repository.OpeningRepository
import io.circe.generic.auto.*
import io.circe.syntax.*
import mongo4cats.circe.*
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.{Aggregate, Filter, Sort}

import scala.util.Random

/** MongoDB implementation of OpeningRepository using mongo4cats and Circe.
  *
  * @param collection
  *   MongoDB collection for openings
  */
class MongoOpeningRepository(collection: MongoCollection[IO, Opening]) extends OpeningRepository[IO]:

  override def save(opening: Opening): IO[Opening] =
    import mongo4cats.models.collection.FindOneAndReplaceOptions
    collection
      .findOneAndReplace(
        Filter.eq("eco", opening.eco) && Filter.eq("name", opening.name),
        opening,
        FindOneAndReplaceOptions().upsert(true)
      )
      .as(opening)

  override def saveAll(openings: List[Opening]): IO[Int] =
    if openings.isEmpty then IO.pure(0)
    else collection.insertMany(openings).map(_.getInsertedIds.size())

  override def findByEco(eco: String): IO[List[Opening]] =
    collection
      .find(Filter.eq("eco", eco))
      .sort(Sort.asc("name"))
      .all
      .map(_.toList)

  override def findByEcoAndName(eco: String, name: String): IO[Option[Opening]] =
    collection.find(Filter.eq("eco", eco) && Filter.eq("name", name)).first

  override def findByName(nameQuery: String, limit: Int = 50): IO[List[Opening]] =
    collection
      .find(Filter.regex("name", s".*$nameQuery.*".r))
      .sort(Sort.asc("eco"))
      .limit(limit)
      .all
      .map(_.toList)

  override def findAll(limit: Int = 100, offset: Int = 0): IO[List[Opening]] =
    collection
      .find(Filter.empty)
      .sort(Sort.asc("eco"))
      .skip(offset)
      .limit(limit)
      .all
      .map(_.toList)

  override def findByMoveCount(maxMoves: Int, limit: Int = 100): IO[List[Opening]] =
    collection
      .find(Filter.lte("moveCount", maxMoves))
      .sort(Sort.asc("moveCount"))
      .limit(limit)
      .all
      .map(_.toList)

  override def findRandom(): IO[Option[Opening]] =
    for
      count <- count()
      randomSkip = if count > 0 then Random.nextInt(count.toInt) else 0
      opening <- collection.find(Filter.empty).skip(randomSkip).first
    yield opening

  override def count(): IO[Long] =
    collection.count(Filter.empty)

  override def deleteAll(): IO[Long] =
    collection.deleteMany(Filter.empty).map(_.getDeletedCount)
