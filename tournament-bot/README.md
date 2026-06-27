# Tournament bot

Headless client for the [NowChess tournament server](https://github.com/maichess/tournament-server).
Registers a bot, joins a tournament, and plays assigned games via UCI moves.

## Run

```bash
export TOURNAMENT_API_URL=http://141.37.123.132:8086
export TOURNAMENT_BOT_NAME=maichess-bot
export TOURNAMENT_ID=<tournament-id>   # or TOURNAMENT_AUTO_JOIN=1

sbt "TournamentBot/runMain chess.tournament.TournamentBotMain"
```

## Environment

| Variable | Description |
|----------|-------------|
| `TOURNAMENT_API_URL` | Base URL (default `http://localhost:8086`) |
| `TOURNAMENT_TOKEN` | JWT from `POST /api/auth/register` |
| `TOURNAMENT_BOT_NAME` | Register on startup if no token |
| `TOURNAMENT_ID` | Tournament to join and stream |
| `TOURNAMENT_AUTO_JOIN` | `1` / `true` — join first `created` tournament |
| `TOURNAMENT_STRATEGY` | Engine strategy id (default `deepening-opening-endgame`) |
| `TOURNAMENT_MIN_THINK_MS` | Min search budget (default 100) |
| `TOURNAMENT_MAX_THINK_MS` | Max search budget (default 5000) |

## Vue UI

Browse tournaments and replay games: **Tournaments → Browse…** in the web UI.
Set `VITE_TOURNAMENT_API_URL` to the same base URL (VPN required for the campus server).

- **Replay on board** — finished or in-progress games (static UCI replay)
- **Watch live** — NDJSON game stream with paced move updates (auto-registers a spectator JWT)
- **Participate as bot** — register + join from the dialog when a tournament is `created`
- Deep link: `?tournament=<id>&game=<gameId>`
