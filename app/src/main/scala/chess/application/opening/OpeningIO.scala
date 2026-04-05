package chess.application.opening

import chess.model.Opening

import scala.util.Try

/** Abstraction for loading chess openings from external resources (TSV/CSV files).
  *
  * Implementations read opening data from classpath resources and produce [[Opening]] values.
  * The trait intentionally has no dependency on any effect type or repository — it is pure data loading.
  *
  * @see [[chess.application.opening.OpeningParser]]
  */
trait OpeningIO:

  /** Parses all five Lichess TSV resources (a.tsv – e.tsv) into [[Opening]] values. */
  def parseLichessOpenings(): List[Opening]

  /** Parses a single TSV classpath resource into [[Opening]] values. */
  def parseTsvResource(resourcePath: String): Try[List[Opening]]

  /** Parses a legacy CSV classpath resource into [[Opening]] values. */
  def parseCsvResource(resourcePath: String): Try[List[Opening]]
