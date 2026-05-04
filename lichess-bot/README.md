# Lichess Bot API client

Long-running process that connects to [Lichess Bot API](https://lichess.org/api#tag/Bot) streams, optionally auto-accepts challenges, and plays moves using the same engine strategies as the rest of the project (`BotStrategy` / `ComputerPlayer`).

## Prerequisite: bot account

1. Create a dedicated Lichess account for the bot.
2. Upgrade it to a **bot account** (one-way) via the Lichess API or site settings, as documented by Lichess.
3. Create a **personal access token** with scopes that allow bot play (see Lichess token UI).

## Run (from repo root)

```bash
export LICHESS_TOKEN='lip_xxxxxxxx'
# optional:
# export LICHESS_STRATEGY=iterative-deepening   # default; see BotStrategy for ids
# export LICHESS_AUTO_ACCEPT=1                # default accept challenges (set to 0 to disable)
# export LICHESS_ONLY_BOT_CHALLENGERS=1        # only accept challenges from bot accounts
# export LICHESS_MIN_THINK_MS=100
# export LICHESS_MAX_THINK_MS=5000

/opt/homebrew/bin/sbt "LichessBot/runMain chess.lichess.LichessBotMain"
```

If your shell’s `sbt` comes from Coursier and is broken, use Homebrew’s binary as above, or run `cs install --force sbt`.

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `LICHESS_TOKEN` | **yes** | Bearer token (`lip_…`). |
| `LICHESS_API_BASE` | no | Default `https://lichess.org`. |
| `LICHESS_STRATEGY` | no | Strategy id (same names as game service, e.g. `iterative-deepening`, `opening-intelligence`). |
| `LICHESS_AUTO_ACCEPT` | no | If `0`, incoming challenges are ignored (not declined). Default: accept. |
| `LICHESS_ONLY_BOT_CHALLENGERS` | no | If `1`, accept only bot challengers; humans are declined. |
| `LICHESS_USER_AGENT` | no | Sent on every request (Lichess prefers a descriptive UA). |
| `LICHESS_MIN_THINK_MS` / `LICHESS_MAX_THINK_MS` | no | Bounds for search time derived from Lichess clocks. |
| `LICHESS_CHALLENGE_USERNAMES` | no | Comma-separated Lichess **usernames** to open a seek against (`POST /api/challenge/{username}`) once at startup. |
| `LICHESS_CHALLENGE_RATED` | no | Set to `1` for rated outgoing challenges (default unrated). |
| `LICHESS_CHALLENGE_CLOCK_SEC` | no | Initial clock seconds (default `300`). |
| `LICHESS_CHALLENGE_INC_SEC` | no | Increment seconds (default `0`). |
| `LICHESS_CHALLENGE_EVERY_MINUTES` | no | If set with `LICHESS_CHALLENGE_USERNAMES`, repeat those challenges on this interval (first repeat after one full interval). |

REST calls retry on **HTTP 429** (sleep 60s, up to 5 times). NDJSON streams reconnect via the outer event/game loops after errors.

## Scope

- **Standard** chess positions from the usual start; move list is replayed with internal rules.
- Variants (Crazyhouse, etc.) are **not** supported until explicitly mapped.

## Testing

- **Unit tests** (`UciSpec`) always run: `sbt "LichessBot/test"`.
- **Integration tests** (`LichessIntegrationSpec`) need a real token; without `LICHESS_TOKEN` they are **canceled** (suite still green for CI):

```bash
export LICHESS_TOKEN='lip_xxxxxxxx'
sbt "LichessBot/test"
# or only integration:
sbt "testOnly chess.lichess.LichessIntegrationSpec"
```

They assert: authenticated **`GET /api/account`** returns an `id` (JSON entity decode), and **`GET /api/account/playing`** returns JSON with a `nowPlaying` array (fast; unlike `/api/stream/event`, which may stay silent for minutes when nothing happens).

## Packaging

`sbt LichessBot/stage` produces `lichess-bot/target/universal/stage/` with the same layout as other packaged apps.
