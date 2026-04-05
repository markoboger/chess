workspace "Chess Application" "Scala chess application — one sbt build, multiple runtime processes. Layers are package-level separations within a single compiled JAR." {

    model {

        # ── People ─────────────────────────────────────────────────────────────
        player    = person "Chess Player" "Plays chess via the desktop GUI, terminal console, or REST API."
        developer = person "Developer"    "Operates the system and seeds opening databases."

        # ── External systems ───────────────────────────────────────────────────
        lichessOpenings = softwareSystem "Lichess Openings Dataset" "Five TSV files (a.tsv – e.tsv) with ECO codes, opening names, and PGN move sequences. Bundled as classpath resources." "External"

        # ── Chess Software System ──────────────────────────────────────────────
        chessSystem = softwareSystem "Chess Application" "Provides chess gameplay through a desktop GUI/console (standalone) and through HTTP microservices (game API + gateway)." {

            # ──────────────────────────────────────────────────────────────────
            # CORE LIBRARY
            # Shared domain model, engine, I/O, and persistence code.
            # Compiled into every runtime process (single sbt artifact).
            # ──────────────────────────────────────────────────────────────────
            coreLib = container "Core Library" "Shared Scala code used by all runtime processes: domain model, chess engine, I/O parsers, AI strategies, persistence adapters." "Scala 3 (compiled into all processes)" "Library" {

                # chess.util ──────────────────────────────────────────────────
                group "chess.util" {
                    observerComp = component "Observer / Observable" "Observer[E]: trait with update(event: E). Observable[E]: trait with add/remove/notifyObservers backed by Vector[Observer[E]]. GameController extends Observable[MoveResult]; GUI and ConsoleView extend Observer[MoveResult]." "Observer.scala"
                }

                # chess.model ─────────────────────────────────────────────────
                group "chess.model" {
                    boardComp = component "Board + CastlingRights" "Board: final case class (squares: Vector[Vector[Option[Piece]]], lastMove, castlingRights). Methods: move, legalMoves, isInCheck, isCheckmate, isStalemate, findKing, isAttackedBy, pieceAt. CastlingRights: case class tracking per-color kingside/queenside flags; revokeKing, revokeRook." "Board.scala"

                    pieceComp = component "Piece · Color · Role · PromotableRole" "Color: enum White | Black (fold, opposite). Role: enum King|Queen|Rook|Bishop|Knight|Pawn (whiteSymbol, blackSymbol, isPromotable). PromotableRole: enum Queen|Rook|Bishop|Knight (toRole, fromRole). Piece: final case class (role: Role, color: Color)." "Piece.scala"

                    squareComp = component "Square · File · Rank" "File: enum A–H (index, letter, offset, -). Rank: enum _1–_8 (index, offset, -). Square: final case class (file, rank) with algebraic-notation factory apply(String), fromString, fromCoords, all: Vector[Square]." "Square.scala"

                    moveResultComp = component "MoveResult" "Sealed trait: Moved(board: Board, gameEvent: GameEvent) | Failed(board: Board, error: MoveError). Monadic API: flatMap, map, foreach, movedOption, toOption, get, getOrElse, isSuccess, isFailed, event." "MoveResult.scala"

                    gameEventComp = component "GameEvent" "Enum: Moved | Check | Checkmate | Stalemate | ThreefoldRepetition. Returned inside MoveResult.Moved to signal the game state after each legal move." "GameEvent.scala"

                    moveErrorComp = component "MoveError" "Enum: NoPiece | InvalidMove | LeavesKingInCheck | WrongColor | ParseError(msg) | PromotionRequired. Returned inside MoveResult.Failed." "MoveError.scala"

                    puzzleModelComp = component "Puzzle" "Case class: id, fen, moves: List[String], rating, ratingDeviation, popularity, nbPlays, themes, gameUrl, openingTags. Represents a tactical chess puzzle." "Puzzle.scala"
                }

                # chess.controller ────────────────────────────────────────────
                group "chess.controller" {
                    gameCtrlComp = component "GameController" "Core chess engine. Applies moves via applyMove / applyPgnMove, tracks boardStates: Vector[Board] and pgnMoves history, supports backward/forward navigation, detects game events, loads FEN via loadFromFEN. Extends Observable[MoveResult]." "GameController.scala"

                    computerPlayerComp = component "ComputerPlayer" "Wraps a MoveStrategy (var strategy for runtime swap). move(board, color, wouldRepeat) adds repetition-avoidance when material advantage ≥ 150 cp. Finds non-repeating alternative; falls back to candidate if all repeat." "ComputerPlayer.scala"

                    moveStrategyComp = component "MoveStrategy" "Trait: name: String, selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])]. Companion object: promotionFor — returns Some(Queen) when pawn reaches back rank." "MoveStrategy.scala"

                    # chess.controller.strategy ───────────────────────────────
                    group "chess.controller.strategy" {
                        evaluatorComp = component "Evaluator" "materialValue(role): Int (Pawn=100 … King=20000). pstBonus(role, square, color): Int using piece-square tables. evaluate(board, color): Int — symmetric material+PST score." "Evaluator.scala"

                        randomStratComp = component "RandomStrategy" "Selects a uniformly random legal move from board.legalMoves(color). Returns None when no moves exist (stalemate/checkmate)." "RandomStrategy.scala"

                        greedyStratComp = component "GreedyStrategy" "Prefers captures, ranked by pieceValue (Q=9, R=5, B=3, N=3, P=1, K=0). Ties broken randomly. Falls back to random quiet move if no captures available." "GreedyStrategy.scala"

                        materialStratComp = component "MaterialBalanceStrategy" "For each legal move applies the move and evaluates the resulting material balance. Picks the highest-scoring move, random tiebreak." "MaterialBalanceStrategy.scala"

                        pstStratComp = component "PieceSquareStrategy" "Same as MaterialBalance but scores with Evaluator.evaluate (material + PST bonus). Stronger positional awareness." "PieceSquareStrategy.scala"

                        minimaxStratComp = component "MinimaxStrategy" "Alpha-beta pruning minimax. Configurable depth (default 3 plies). Detects mate/stalemate as terminal nodes. Path-repetition detection via seenInPath: Set[NodeKey] (squares + castlingRights + maximizing)." "MinimaxStrategy.scala"

                        quiescenceStratComp = component "QuiescenceStrategy" "MinimaxStrategy extended with quiescence search: after main search bottoms out, continues searching captures only until quiet. Configurable depth + qDepth (default 3+6)." "QuiescenceStrategy.scala"

                        idStratComp = component "IterativeDeepeningStrategy" "Time-limited iterative deepening over alpha-beta. Searches depth 1 → ∞ until timeLimitMs expires; returns best complete-depth result. timeLimitMs: var (default 2000 ms)." "IterativeDeepeningStrategy.scala"
                    }

                    # chess.controller.io ─────────────────────────────────────
                    group "chess.controller.io" {
                        fenIOTrait = component "FenIO" "Trait: load(s: String): Try[Board], save(b: Board): String. Pluggable FEN serialisation; bound via AppBindings given." "FenIO.scala"

                        pgnIOTrait = component "PgnIO" "Trait: loadFile(path), parseMove(san, board, color), toAlgebraic(from, to, before, after, isWhite), pgnText(moves). Pluggable PGN serialisation." "PgnIO.scala"

                        fileIOTrait = component "FileIO" "Trait: save(board): String, load(s: String): Try[Board]. Abstracts JSON board serialisation for saving/loading game files." "FileIO.scala"

                        # chess.controller.io.fen ─────────────────────────────
                        group "chess.controller.io.fen" {
                            regexFenComp = component "RegexFenParser" "FenIO implementation using Regex + split. Parses board part, active color, castling availability, en-passant square. Default binding in AppBindings." "RegexFenParser.scala"

                            combinatorFenComp = component "CombinatorFenParser" "FenIO implementation using scala-parser-combinators. Alternative to RegexFenParser." "CombinatorFenParser.scala"

                            fastParseFenComp = component "FastParseFenParser" "FenIO implementation using fastparse. Alternative to RegexFenParser." "FastParseFenParser.scala"
                        }

                        # chess.controller.io.pgn ─────────────────────────────
                        group "chess.controller.io.pgn" {
                            pgnParserComp = component "PGNParser" "Full algebraic notation parser and formatter. Handles pawn push/capture, piece moves, disambiguation (file/rank/both), castling O-O/O-O-O, promotion, check +, checkmate #. toAlgebraic and parseMove." "PGNParser.scala"

                            pgnFileIOComp = component "PgnFileIO" "Default PgnIO implementation. Reads .pgn files from disk; delegates move parsing and formatting to PGNParser." "PgnFileIO.scala"

                            combinatorPgnComp = component "CombinatorPgnParser" "PgnIO implementation using scala-parser-combinators. Alternative to PgnFileIO." "CombinatorPgnParser.scala"

                            fastParsePgnComp = component "FastParsePgnParser" "PgnIO implementation using fastparse. Alternative to PgnFileIO." "FastParsePgnParser.scala"
                        }

                        # chess.controller.io.json ────────────────────────────
                        group "chess.controller.io.json" {
                            circeJsonComp = component "CirceJsonFileIO + BoardCodecs (Circe)" "FileIO implementation using Circe. BoardCodecs provides given Encoder/Decoder for Board (8×8 nullable array + lastMove), Piece, Square. JSON is cross-compatible with uPickle output." "circe/CirceJsonFileIO.scala + circe/BoardCodecs.scala"

                            upickleJsonComp = component "UPickleJsonFileIO + BoardCodecs (uPickle)" "FileIO implementation using uPickle/ujson. BoardCodecs provides given ReadWriter instances. Produces identical JSON structure to Circe variant." "upickle/UPickleJsonFileIO.scala + upickle/BoardCodecs.scala"
                        }
                    }

                    # chess.controller.clock ──────────────────────────────────
                    group "chess.controller.clock" {
                        clockComp = component "ClockActor" "Apache Pekko (ex-Akka) typed actor implementing a chess clock with configurable time controls per color. Sends Tick messages; reports TimeUp." "ClockActor.scala"
                    }

                    # chess.controller.puzzle ─────────────────────────────────
                    group "chess.controller.puzzle" {
                        puzzleParserComp = component "PuzzleParser" "Parses Lichess puzzle CSV lines into Puzzle model objects. Handles field mapping and type conversion." "PuzzleParser.scala"
                    }
                }

                # chess.persistence ───────────────────────────────────────────
                group "chess.persistence.model" {
                    openingModelComp = component "Opening" "Case class: eco: String, name: String, moves: String, fen: String, moveCount: Int. Composite key (eco, name). Factory: Opening.unsafe(eco, name, moves, fen, moveCount)." "Opening.scala"

                    persistedGameComp = component "PersistedGame" "Case class: id: UUID, fen: String, pgn: String, status: String, createdAt: Instant, updatedAt: Instant. Factory: PersistedGame.create(fen?, pgn?, status?)." "PersistedGame.scala"
                }

                group "chess.persistence.repository" {
                    openingRepoTrait = component "OpeningRepository[F[_]]" "Trait (higher-kinded): save, saveAll, findByEcoAndName, findByName(query, limit), findAll(limit, offset), findByMoveCount(maxMoves), count. Bound via AppBindings given." "OpeningRepository.scala"

                    gameRepoTrait = component "GameRepository[F[_]]" "Trait (higher-kinded): save(game), findById(id), findAll(limit, offset), findByStatus(status), delete(id)." "GameRepository.scala"
                }

                group "chess.persistence.memory" {
                    inMemoryOpeningComp = component "InMemoryOpeningRepository" "Cats Effect IO + concurrent Map. Composite key (eco, name). fromLichess(): loads all 5 Lichess TSV files via OpeningSeeder at startup. Default OpeningRepository binding in AppBindings." "InMemoryOpeningRepository.scala"
                }

                group "chess.persistence.postgres" {
                    postgresGameComp    = component "PostgresGameRepository"    "GameRepository[IO] backed by Doobie HikariCP Transactor. SQL table: games (id, fen, pgn, status, created_at, updated_at). Upsert on save." "PostgresGameRepository.scala"

                    postgresOpeningComp = component "PostgresOpeningRepository" "OpeningRepository[IO] backed by Doobie. SQL table: openings (eco, name, moves, fen, move_count). Composite PK (eco, name). Bulk insert with conflict-ignore." "PostgresOpeningRepository.scala"
                }

                group "chess.persistence.mongodb" {
                    mongoGameComp    = component "MongoGameRepository"    "GameRepository[IO] backed by mongo4cats. Collection: games. Filter/Sort/Skip/Limit fluent API. UUID stored as String." "MongoGameRepository.scala"

                    mongoOpeningComp = component "MongoOpeningRepository" "OpeningRepository[IO] backed by mongo4cats. Collection: openings. Regex name search via Filter.regex." "MongoOpeningRepository.scala"
                }

                group "chess.persistence.util" {
                    openingSeederComp = component "OpeningSeeder" "Loads Lichess TSV files (a–e) from classpath or CSV resources. parseTsvOpenings / parseCsvOpenings, deduplicates by (eco, name), bulk-saves to any OpeningRepository[IO]. seedFromCsvResource returns IO[Int]." "OpeningSeeder.scala"

                    seedAppComp = component "SeedOpeningsApp" "IOApp entry-point for one-shot database seeding. Connects to Postgres or MongoDB, calls OpeningSeeder, logs count." "SeedOpeningsApp.scala"
                }
            }

            # ──────────────────────────────────────────────────────────────────
            # STANDALONE APP
            # Desktop process: chess.ChessApp (sbt mainClass)
            # ──────────────────────────────────────────────────────────────────
            standaloneApp = container "Standalone App" "Desktop process launched via chess.ChessApp. Starts either the JavaFX GUI or the terminal ConsoleView." "Scala 3 / ScalaFX · JVM process" "Application" {

                group "chess  (root package)" {
                    appBindingsComp = component "AppBindings" "Wires given instances at startup: FenIO → RegexFenParser, PgnIO → PgnFileIO, OpeningRepository[IO] → InMemoryOpeningRepository.fromLichess(). Swap implementations by changing one line." "AppBindings.scala"

                    chessAppComp = component "ChessApp" "IOApp entry-point. Parses --gui / --console CLI arg. Creates GameController, ComputerPlayer, OpeningRepository, then launches ChessGUI or ConsoleView." "ChessApp.scala"
                }

                group "chess.aview" {
                    chessGUIComp = component "ChessGUI" "JavaFX / ScalaFX board UI. Features: drag-and-drop or text-field move input, back/forward history buttons, FEN display, PGN text area, opening name label, New Game button. Extends Observer[MoveResult] — self-registers on GameController." "ChessGUI.scala"

                    consoleViewComp = component "ConsoleView" "ANSI terminal renderer. Prints board with Unicode pieces, active color, FEN, and game events after each move. Reads PGN notation from stdin. Extends Observer[MoveResult] — self-registers on GameController." "ConsoleView.scala"
                }
            }

            # ──────────────────────────────────────────────────────────────────
            # API GATEWAY
            # chess.microservices.gateway.GatewayServer (port 8080)
            # ──────────────────────────────────────────────────────────────────
            gateway = container "API Gateway" "Single HTTP entry-point. Proxies /api/games/* to the Game Service and /* to the UI Service. No business logic." "Scala 3 / http4s Ember · port 8080" "Microservice" {

                group "chess.microservices.gateway" {
                    gatewayServerComp = component "GatewayServer" "IOApp.Simple. Builds EmberClient and EmberServer; mounts GatewayRoutes." "GatewayServer.scala"

                    gatewayConfigComp = component "GatewayConfig" "Reads GAME_SERVICE_URL (default http://localhost:8081), UI_SERVICE_URL (default http://localhost:8082), PORT (default 8080) from environment variables." "GatewayConfig.scala"

                    gatewayRoutesComp = component "GatewayRoutes" "HttpRoutes: GET /health → HealthResponse. /api/games/* → proxy to Game Service. /* → proxy to UI Service." "GatewayRoutes.scala"

                    serviceProxyComp = component "ServiceProxy" "Transparent HTTP proxy using EmberClient. Rewrites URI to target base URL, preserves method/headers/body, streams response back." "ServiceProxy.scala"
                }
            }

            # ──────────────────────────────────────────────────────────────────
            # GAME SERVICE
            # chess.microservices.game.GameServer (port 8081)
            # ──────────────────────────────────────────────────────────────────
            gameService = container "Game Service" "Stateful chess game microservice. Manages concurrent game sessions; applies moves; detects game events." "Scala 3 / http4s Ember · Cats Effect IO · port 8081" "Microservice" {

                group "chess.microservices.game" {
                    gameServerComp = component "GameServer" "IOApp. Builds EmberServer, wires GameService with a fresh GameController per game and a GameRepository (Postgres or MongoDB)." "GameServer.scala"

                    gameRoutesComp = component "GameRoutes" "REST: POST /api/games (create), GET /api/games/:id (state), POST /api/games/:id/moves (make move), DELETE /api/games/:id, GET /health. Encodes/decodes via ApiModels." "GameRoutes.scala"

                    gameSvcComp = component "GameService" "IO-based service: createGame(startFen?), getGame(id), makeMove(id, san) → Right((fen, eventName?)) | Left(error). Detects check, checkmate, stalemate, threefold_repetition. Stores sessions in Ref[IO, Map[UUID, GameController]]." "GameService.scala"
                }

                group "chess.microservices.shared" {
                    apiModelsComp = component "ApiModels" "Circe-encoded DTOs: CreateGameRequest(startFen?), CreateGameResponse(gameId, fen), GameStateResponse(gameId, fen, pgn, status), MakeMoveRequest(move), MakeMoveResponse(success, fen, event?), MoveHistoryResponse(moves), FenResponse, LoadFenRequest/Response, ErrorResponse(error, details?), HealthResponse(status, service)." "ApiModels.scala"
                }
            }

            # ──────────────────────────────────────────────────────────────────
            # UI SERVICE
            # chess.microservices.ui.UIServer (port 8082)
            # ──────────────────────────────────────────────────────────────────
            uiService = container "UI Service" "Serves static web front-end assets to browser clients." "Scala 3 / http4s Ember · port 8082" "Microservice" {

                group "chess.microservices.ui" {
                    uiServerComp = component "UIServer" "IOApp. Serves files from classpath /static, returns 404 for unknown paths, GET /health." "UIServer.scala"
                }
            }

            # ── Databases ─────────────────────────────────────────────────────
            postgres = container "PostgreSQL" "Relational database. Tables: games, openings. Accessed via Doobie HikariCP." "PostgreSQL 15" "Database"
            mongodb  = container "MongoDB"    "Document database. Collections: games, openings. Accessed via mongo4cats." "MongoDB 7"    "Database"
        }

        # ── People → System ────────────────────────────────────────────────────
        player    -> chessSystem "Plays chess"
        developer -> chessSystem "Operates, seeds data"
        chessSystem -> lichessOpenings "Reads TSV opening files at startup"

        # ── People → Containers ────────────────────────────────────────────────
        player    -> standaloneApp "Launches desktop app"
        player    -> gateway       "HTTP requests (browser / REST client)"
        developer -> postgres      "Seeds openings (SeedOpeningsApp)"
        developer -> mongodb       "Seeds openings (SeedOpeningsApp)"

        # ── Container → Container ──────────────────────────────────────────────
        standaloneApp -> coreLib "Uses (in-process)"
        gateway       -> coreLib "Uses (GatewayConfig, in-process)"
        gameService   -> coreLib "Uses (GameController, repositories, in-process)"
        uiService     -> coreLib "Uses (in-process)"

        gateway     -> gameService "Proxies /api/games/*" "HTTP/JSON"
        gateway     -> uiService   "Proxies /*"           "HTTP"

        gameService -> postgres "Reads / writes games and openings" "JDBC / Doobie"
        gameService -> mongodb  "Reads / writes games and openings" "mongo4cats"

        standaloneApp -> lichessOpenings "Loads TSV files from classpath"

        # ── Core Library: internal component relationships ─────────────────────
        # chess.model
        boardComp      -> pieceComp     "Contains 64 Option[Piece] cells"
        boardComp      -> squareComp    "Addresses pieces via Square"
        boardComp      -> gameEventComp "Produces on move (detectGameEvent)"
        moveResultComp -> boardComp     "Wraps resulting Board"
        moveResultComp -> gameEventComp "Carries on Moved"
        moveResultComp -> moveErrorComp "Carries on Failed"

        # chess.controller
        gameCtrlComp -> boardComp      "Creates, reads, applies moves to"
        gameCtrlComp -> moveResultComp "Produces and notifies observers with"
        gameCtrlComp -> fenIOTrait     "Parses / serialises FEN via"
        gameCtrlComp -> pgnIOTrait     "Parses / serialises PGN via"
        gameCtrlComp -> openingRepoTrait "Looks up opening name via"
        gameCtrlComp -> observerComp   "Extends Observable[MoveResult]"

        computerPlayerComp -> moveStrategyComp  "Delegates selectMove to"
        computerPlayerComp -> gameCtrlComp      "Reads board; applies chosen move"
        computerPlayerComp -> evaluatorComp     "Reads material balance for repetition guard"

        minimaxStratComp    -> evaluatorComp    "Evaluates positions via"
        quiescenceStratComp -> evaluatorComp    "Evaluates positions via"
        idStratComp         -> evaluatorComp    "Evaluates positions via"
        materialStratComp   -> evaluatorComp    "Evaluates material balance via"
        pstStratComp        -> evaluatorComp    "Evaluates PST score via"

        # chess.controller.io
        fenIOTrait -> regexFenComp      "implemented by (default)"
        fenIOTrait -> combinatorFenComp "implemented by (alternative)"
        fenIOTrait -> fastParseFenComp  "implemented by (alternative)"

        pgnIOTrait -> pgnFileIOComp     "implemented by (default)"
        pgnIOTrait -> combinatorPgnComp "implemented by (alternative)"
        pgnIOTrait -> fastParsePgnComp  "implemented by (alternative)"

        pgnFileIOComp -> pgnParserComp  "Delegates parsing / formatting to"

        # chess.persistence
        openingRepoTrait -> inMemoryOpeningComp  "implemented by"
        openingRepoTrait -> postgresOpeningComp  "implemented by"
        openingRepoTrait -> mongoOpeningComp     "implemented by"

        gameRepoTrait -> postgresGameComp "implemented by"
        gameRepoTrait -> mongoGameComp    "implemented by"

        postgresGameComp    -> postgres "JDBC / Doobie"
        postgresOpeningComp -> postgres "JDBC / Doobie"
        mongoGameComp       -> mongodb  "mongo4cats"
        mongoOpeningComp    -> mongodb  "mongo4cats"

        inMemoryOpeningComp -> openingSeederComp "Seeded by at startup"
        openingSeederComp   -> openingRepoTrait  "Bulk-saves openings via"

        # ── Standalone App: internal component relationships ───────────────────
        chessAppComp    -> appBindingsComp  "Imports given instances from"
        chessAppComp    -> chessGUIComp     "Creates and launches"
        chessAppComp    -> consoleViewComp  "Creates and launches"
        chessGUIComp    -> gameCtrlComp     "applyMove / applyPgnMove; reads board, pgnText"
        consoleViewComp -> gameCtrlComp     "applyMove / applyPgnMove; reads board"
        chessGUIComp    -> observerComp     "Extends Observer[MoveResult]"
        consoleViewComp -> observerComp     "Extends Observer[MoveResult]"
        chessGUIComp    -> openingRepoTrait "Displays opening name for current position"

        # ── Game Service: internal component relationships ─────────────────────
        gameServerComp -> gameRoutesComp "Mounts routes"
        gameRoutesComp -> gameSvcComp    "Delegates business logic to"
        gameRoutesComp -> apiModelsComp  "Encodes / decodes JSON with"
        gameSvcComp    -> gameCtrlComp   "Creates and manages per-game instances"
        gameSvcComp    -> gameRepoTrait  "Persists / retrieves game state via"

        # ── API Gateway: internal component relationships ──────────────────────
        gatewayServerComp -> gatewayRoutesComp "Mounts routes"
        gatewayRoutesComp -> serviceProxyComp  "Delegates proxy to"
        gatewayRoutesComp -> gatewayConfigComp "Reads service URLs from"
        serviceProxyComp  -> gatewayConfigComp "Reads target base URLs from"
    }

    views {

        # ── Level 1: System Context ────────────────────────────────────────────
        systemContext chessSystem "SystemContext" {
            include *
            autoLayout
            title "Level 1 – System Context"
        }

        # ── Level 2: Containers ────────────────────────────────────────────────
        container chessSystem "Containers" {
            include *
            autoLayout
            title "Level 2 – Containers"
        }

        # ── Level 3: Core Library ──────────────────────────────────────────────
        component coreLib "CoreLibrary" {
            include *
            autoLayout
            title "Level 3 – Core Library (chess.model · chess.controller · chess.persistence · chess.util)"
        }

        # ── Level 3: Standalone App ────────────────────────────────────────────
        component standaloneApp "StandaloneApp" {
            include *
            autoLayout
            title "Level 3 – Standalone App (chess · chess.aview)"
        }

        # ── Level 3: API Gateway ───────────────────────────────────────────────
        component gateway "Gateway" {
            include *
            autoLayout
            title "Level 3 – API Gateway (chess.microservices.gateway)"
        }

        # ── Level 3: Game Service ──────────────────────────────────────────────
        component gameService "GameService" {
            include *
            autoLayout
            title "Level 3 – Game Service (chess.microservices.game · chess.microservices.shared)"
        }

        # ── Level 3: UI Service ────────────────────────────────────────────────
        component uiService "UIService" {
            include *
            autoLayout
            title "Level 3 – UI Service (chess.microservices.ui)"
        }

        # ── Styles ────────────────────────────────────────────────────────────
        styles {
            element "Person" {
                shape Person
                background #08427B
                color #ffffff
            }
            element "Software System" {
                background #1168BD
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Library" {
                background #1D70B8
                color #ffffff
            }
            element "Application" {
                background #1168BD
                color #ffffff
            }
            element "Microservice" {
                background #2E7D32
                color #ffffff
                shape Hexagon
            }
            element "Database" {
                shape Cylinder
                background #6B3A9C
                color #ffffff
            }
            element "Component" {
                background #85BBF0
                color #000000
            }
        }
    }
}
