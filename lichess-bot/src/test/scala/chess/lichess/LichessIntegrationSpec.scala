package chess.lichess

import cats.effect.IO
import cats.effect.syntax.temporal.*
import cats.effect.unsafe.implicits.global
import org.scalatest.Assertions.assume
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

/** Live Lichess HTTP checks. Skipped unless `LICHESS_TOKEN` is set (never commit tokens). */
final class LichessIntegrationSpec extends AnyFlatSpec with Matchers:

  private def tokenPresent: Boolean =
    Option(System.getenv("LICHESS_TOKEN")).exists(_.trim.nonEmpty)

  private def withClient[A](f: LichessClient => IO[A]): A =
    val cfg = LichessBotConfig.load match
      case Right(c) => c
      case Left(e)   => fail(s"config: $e")
    LichessClient.resource(cfg).use(f).unsafeRunSync()

  "Lichess Bot API (integration)" should "connect and return a non-empty account id from GET /api/account" in {
    assume(tokenPresent, "Set LICHESS_TOKEN to run Lichess integration tests")
    val id = withClient(_.fetchAccountId.timeout(30.seconds))
    id should not be empty
    id.trim shouldBe id
  }

  it should "return JSON from GET /api/account/playing (auth + quick JSON endpoint)" in {
    assume(tokenPresent, "Set LICHESS_TOKEN to run Lichess integration tests")
    val n = withClient(_.fetchNowPlayingCount.timeout(30.seconds))
    n should be >= 0
  }

end LichessIntegrationSpec
