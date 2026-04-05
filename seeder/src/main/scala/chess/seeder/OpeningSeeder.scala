package chess.seeder

import cats.effect.IO
import cats.implicits.*
import chess.persistence.OpeningRepository
import chess.controller.io.opening.OpeningParser

/** Seeds an [[OpeningRepository]] from Lichess TSV or CSV classpath resources.
  *
  * Parsing is delegated to [[OpeningParser]] from the core project. This object handles only the effectful write side:
  * loading parsed openings into a repository.
  */
object OpeningSeeder:

  /** Seeds the repository from all five Lichess TSV resources (a.tsv–e.tsv). */
  def seedLichessOpenings(repository: OpeningRepository[IO]): IO[Int] =
    for
      openings <- IO(OpeningParser.parseLichessOpenings())
      count <- if openings.isEmpty then IO.pure(0) else repository.saveAll(openings)
    yield count

  /** Seeds the repository from a single TSV classpath resource. */
  def seedFromTsvResource(repository: OpeningRepository[IO], resourcePath: String): IO[Int] =
    IO.fromTry(OpeningParser.parseTsvResource(resourcePath)).flatMap { openings =>
      if openings.isEmpty then IO.pure(0) else repository.saveAll(openings)
    }

  /** Seeds the repository from a legacy CSV classpath resource. */
  def seedFromCsvResource(repository: OpeningRepository[IO], resourcePath: String): IO[Int] =
    IO.fromTry(OpeningParser.parseCsvResource(resourcePath)).flatMap { openings =>
      if openings.isEmpty then IO.pure(0) else repository.saveAll(openings)
    }
