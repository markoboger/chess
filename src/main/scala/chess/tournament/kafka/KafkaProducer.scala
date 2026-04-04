package chess.tournament.kafka

import cats.effect.*
import cats.implicits.*
import fs2.kafka.*
import io.circe.Encoder
import io.circe.syntax.*
import chess.tournament.events.TournamentEvents.TournamentEvent

/** Kafka producer for tournament events */
trait EventProducer[F[_]]:
  /** Publish an event to the appropriate Kafka topic */
  def publish(event: TournamentEvent): F[Unit]

  /** Publish multiple events atomically */
  def publishAll(events: List[TournamentEvent]): F[Unit]

object EventProducer:

  /** Create a Kafka event producer */
  def apply[F[_]: Async](
      bootstrapServers: String = "localhost:9092"
  ): Resource[F, EventProducer[F]] =
    val producerSettings =
      ProducerSettings[F, String, String]
        .withBootstrapServers(bootstrapServers)

    KafkaProducer.resource(producerSettings).map { producer =>
      new EventProducerImpl[F](producer)
    }

  private class EventProducerImpl[F[_]: Async](
      producer: KafkaProducer[F, String, String]
  ) extends EventProducer[F]:

    override def publish(event: TournamentEvent): F[Unit] =
      val topic = topicForEvent(event)
      val key = event.tournamentId
      val value = eventToJson(event)

      val record = ProducerRecord(topic, key, value)
      producer.produceOne(record).flatten.void

    override def publishAll(events: List[TournamentEvent]): F[Unit] =
      val records = events.map { event =>
        val topic = topicForEvent(event)
        val key = event.tournamentId
        val value = eventToJson(event)
        ProducerRecord(topic, key, value)
      }

      producer.produce(ProducerRecords(records)).flatten.void

    private def topicForEvent(event: TournamentEvent): String =
      import chess.tournament.events.TournamentEvents.*

      event match
        case _: TournamentCreated | _: PlayerRegistered | _: TournamentStarted | _: TournamentCompleted =>
          "tournament-events"
        case _: GameScheduled | _: GameStarted =>
          "game-events"
        case _: MoveMade =>
          "move-events"
        case _: GameCompleted =>
          "game-results"
        case _: LeaderboardUpdated =>
          "leaderboard-updates"

    private def eventToJson(event: TournamentEvent): String =
      import chess.tournament.events.TournamentEvents.*

      val json = event match
        case e: TournamentCreated => e.asJson
        case e: PlayerRegistered => e.asJson
        case e: TournamentStarted => e.asJson
        case e: TournamentCompleted => e.asJson
        case e: GameScheduled => e.asJson
        case e: GameStarted => e.asJson
        case e: MoveMade => e.asJson
        case e: GameCompleted => e.asJson
        case e: LeaderboardUpdated => e.asJson

      json.noSpaces
