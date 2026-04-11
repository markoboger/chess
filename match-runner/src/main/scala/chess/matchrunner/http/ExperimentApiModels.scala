package chess.matchrunner.http

import chess.matchrunner.application.{DirectionStats, ExperimentRequest, ExperimentSummary}
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import java.time.Instant
import java.util.UUID

given Encoder[Instant] = Encoder.encodeInstant
given Decoder[Instant] = Decoder.decodeInstant

given Encoder[UUID] = Encoder.encodeUUID
given Decoder[UUID] = Decoder.decodeUUID

given Encoder[ExperimentStatus] = Encoder.encodeString.contramap(_.toString)
given Decoder[ExperimentStatus] = Decoder.decodeString.map(ExperimentStatus.fromString)

given Encoder[MatchResult] = Encoder.encodeString.contramap(_.toString)
given Decoder[MatchResult] = Decoder.decodeString.map(MatchResult.fromString)

given Encoder[Experiment]       = deriveEncoder
given Decoder[Experiment]       = deriveDecoder
given Encoder[MatchRun]         = deriveEncoder
given Decoder[MatchRun]         = deriveDecoder
given Encoder[DirectionStats]   = deriveEncoder
given Encoder[ExperimentSummary] = deriveEncoder
given Encoder[ExperimentRequest] = deriveEncoder
given Decoder[ExperimentRequest] = deriveDecoder
