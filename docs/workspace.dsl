workspace "Chess Application" "Scala chess application — one sbt build. Package layers: chess (root), chess.model, chess.controller, chess.persistence, chess.aview, chess.util, chess.microservices.*" {

    model {

        # ── People ──────────────────────────────────────────────────────────────
        player    = person "Chess Player" "Plays chess via the desktop GUI, terminal console, or REST API."
        developer = person "Developer"    "Operates the system and seeds opening databases."

        # ── External systems ─────────────────────────────────────────────────────
        lichessOpenings = softwareSystem "Lichess Openings Dataset" "Five TSV files (a.tsv to e.tsv) with ECO codes, opening names, and PGN move sequences. Bundled as classpath resources." "External"

        # ── Chess Software System ─────────────────────────────────────────────────
        chessSystem = softwareSystem "Chess Application" "Desktop chess app plus HTTP microservices, all sharing one compiled Scala codebase." {

            # ================================================================
            # DESKTOP APP — chess.aview + chess root  (NOT a Docker container)
            # Launched locally via: sbt run --gui  or  sbt run --console
            # ================================================================
            desktopApp = container "Chess Desktop App" "JavaFX/ScalaFX desktop chess application. Runs as a local JVM process; NOT deployed as a Docker container. Launches ChessGUI (graphical board, clock, opening recognition) or ConsoleView (ANSI terminal)." "Scala 3 / JavaFX 21 / ScalaFX · local JVM" "Application" {

                group "chess.aview" {
                    chessGUIComp    = component "ChessGUI"    "class. JavaFX/ScalaFX board UI. Drag-and-drop + text-field input, back/forward history buttons, FEN display, PGN TextArea, opening name label (auto-updated after each move via FEN lookup in openingsByFen map), New Game. Extends Observer[MoveResult]; self-registers on GameController." "class · ChessGUI.scala"
                    consoleViewComp = component "ConsoleView" "class. ANSI terminal renderer. Prints Unicode board, active color, FEN, game events after each move. Reads PGN from stdin. Extends Observer[MoveResult]; self-registers on GameController." "class · ConsoleView.scala"
                }

                group "chess (root)" {
                    appBindingsComp = component "AppBindings" "object. given FenIO = RegexFenParser. given PgnIO = PgnFileIO(). given OpeningIO = OpeningParser. given OpeningRepository[IO] = InMemoryOpeningRepository.fromLichess(). Swap any implementation by changing one line." "object · AppBindings.scala"
                    chessAppComp    = component "ChessApp"    "object (IOApp). Parses --gui/--console CLI arg. Creates GameController + ComputerPlayer + OpeningRepository, then launches ChessGUI or ConsoleView." "object · ChessApp.scala"
                }
            }

            # ================================================================
            # API GATEWAY — chess.microservices.gateway.GatewayServer :8080
            # ================================================================
            gateway = container "API Gateway" "Single HTTP entry-point. Routes /api/games/* to Game Service and /* to UI Service. No business logic." "Scala 3 / http4s Ember · port 8080" "Microservice" {
                group "chess.microservices.gateway" {
                    gatewayServerComp = component "GatewayServer" "object (IOApp.Simple). Builds EmberClient + EmberServer; mounts GatewayRoutes." "object · GatewayServer.scala"
                    gatewayConfigComp = component "GatewayConfig" "object. Reads GAME_SERVICE_URL (default :8081), UI_SERVICE_URL (default :8082), PORT (default 8080) from environment variables." "object · GatewayConfig.scala"
                    gatewayRoutesComp = component "GatewayRoutes" "object. HttpRoutes: GET /health returns HealthResponse. /api/games/* proxied to Game Service. /* proxied to UI Service." "object · GatewayRoutes.scala"
                    serviceProxyComp  = component "ServiceProxy"  "object. Transparent HTTP proxy via EmberClient. Rewrites URI to target base URL; preserves method/headers/body; streams response back." "object · ServiceProxy.scala"
                }
            }

            # ================================================================
            # GAME SERVICE — chess.microservices.game.GameServer :8081
            # Docker container: chess-game-service
            # Bundles the full chess engine (model, controller, persistence)
            # plus the HTTP microservice entry point into one JVM process.
            # ================================================================
            gameService = container "Game Service" "Stateful chess game microservice. Manages concurrent sessions, applies moves, detects game events. Bundles the complete chess engine (model, controller, persistence) into a single JVM process." "Scala 3 / http4s Ember · Cats Effect IO · port 8081" "Microservice" {

                # ── chess.util ────────────────────────────────────────────────
                group "chess.util" {
                    observerComp = component "Observer / Observable" "Observer[E]: trait with update(event: E): Unit. Observable[E]: trait with add/remove/notifyObservers(event) backed by Vector[Observer[E]]. Wires GameController (Observable) to ChessGUI and ConsoleView (both Observer)." "Observer.scala"
                }

                # ── chess.model ───────────────────────────────────────────────
                group "chess.model" {
                    openingClass       = component "Opening"       "case class (eco: String, name: String, moves: String, fen: String, moveCount: Int). Composite key (eco,name). Opening.unsafe(eco,name,moves,fen,moveCount) factory." "case class · Opening.scala"
                    persistedGameClass = component "PersistedGame" "case class (id: UUID, fenHistory: List[String], pgnMoves: List[String], currentTurn: String, status: String, result: Option[String], openingEco: Option[String], openingName: Option[String], createdAt: Instant, updatedAt: Instant). PersistedGame.create(...) factory." "case class · PersistedGame.scala"
                    puzzleClass        = component "Puzzle" "case class (id: String, fen: String, moves: List[String], rating: Int, ratingDeviation: Int, popularity: Int, nbPlays: Int, themes: List[String], gameUrl: String, openingTags: List[String])." "case class · Puzzle.scala"
                    colorEnum          = component "Color" "enum White | Black. fold[A](white: =>A, black: =>A): A. opposite: Color." "enum · Piece.scala"
                    roleEnum           = component "Role" "enum King | Queen | Rook | Bishop | Knight | Pawn. whiteSymbol/blackSymbol: String (unicode). isPromotable: Boolean. Role.all: Vector[Role]." "enum · Piece.scala"
                    promotableRoleEnum = component "PromotableRole" "enum Queen | Rook | Bishop | Knight. toRole: Role. PromotableRole.fromRole(role: Role): Option[PromotableRole]. all: Vector[PromotableRole]." "enum · Piece.scala"
                    pieceClass         = component "Piece" "final case class (role: Role, color: Color). toString: Unicode chess symbol via color.fold(role.whiteSymbol, role.blackSymbol)." "case class · Piece.scala"
                    fileEnum           = component "File" "enum A|B|C|D|E|F|G|H (index: Int, letter: Char). offset(n: Int): Option[File]. -(other: File): Int. File.fromInt, fromChar, all: Vector[File]." "enum · Square.scala"
                    rankEnum           = component "Rank" "enum _1 through _8 (index: Int). offset(n: Int): Option[Rank]. -(other: Rank): Int. Rank.fromInt, all: Vector[Rank]." "enum · Square.scala"
                    squareClass        = component "Square" "final case class (file: File, rank: Rank). toString: algebraic e.g. e4. Square.apply(notation: String), fromString, fromCoords(file,rank). Square.all: Vector[Square] — all 64 squares." "case class · Square.scala"
                    castlingRightsClass = component "CastlingRights" "case class (whiteKingside, whiteQueenside, blackKingside, blackQueenside: Boolean = true). can(color,kingside): Boolean. revokeKing(color): CastlingRights. revokeRook(from: Square): CastlingRights." "case class · Board.scala"
                    boardClass         = component "Board" "final case class (squares: Vector[Vector[Option[Piece]]], lastMove: Option[(Square,Square)], castlingRights: CastlingRights). move(from,to): MoveResult. legalMoves(color): Vector[(Square,Square)]. isInCheck, isCheckmate, isStalemate(color). findKing, isAttackedBy, pieceAt. applyMoveUnchecked. Board.initial." "case class · Board.scala"
                    moveResultTrait    = component "MoveResult" "sealed trait. board: Board. flatMap/map/foreach. movedOption: Option[(Board,GameEvent)]. toOption, get, getOrElse, isSuccess, isFailed, event: Option[GameEvent]." "sealed trait · MoveResult.scala"
                    movedClass         = component "MoveResult.Moved" "final case class (board: Board, gameEvent: GameEvent = GameEvent.Moved). movedOption: Some((board,event))." "case class · MoveResult.scala"
                    failedClass        = component "MoveResult.Failed" "final case class (board: Board, error: MoveError). flatMap/map return this unchanged. movedOption: None." "case class · MoveResult.scala"
                    gameEventEnum      = component "GameEvent" "enum Moved | Check | Checkmate | Stalemate | ThreefoldRepetition. Produced by Board.move; carried inside MoveResult.Moved." "enum · GameEvent.scala"
                    moveErrorEnum      = component "MoveError" "enum NoPiece | InvalidMove | LeavesKingInCheck | WrongColor | ParseError(msg) | PromotionRequired. Carried inside MoveResult.Failed." "enum · MoveError.scala"
                }

                # ── chess.controller ────────────────────────────────────────────
                group "chess.controller" {
                    gameCtrlComp       = component "GameController" "class. boardStates: Vector[Board], pgnMoves: Vector[String], currentIndex: Int, activeColor: Color. applyMove(from,to), applyPgnMove(san). backward/forward navigation. announceInitial, loadFromFEN. isInCheck, isCheckmate, isStalemate, gameStatus. Extends Observable[MoveResult]." "class · GameController.scala"
                    computerPlayerComp = component "ComputerPlayer" "class (var strategy: MoveStrategy). move(board, color, wouldRepeat: Board=>Boolean): Option[(Square,Square,Option[PromotableRole])]. If material advantage >= 150cp avoids repeating positions; falls back to best candidate if all moves repeat." "class · ComputerPlayer.scala"
                    moveStrategyTrait  = component "MoveStrategy" "trait. name: String. selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])]. MoveStrategy.promotionFor(from,to,board): Option[PromotableRole] — returns Some(Queen) when pawn reaches back rank." "trait · MoveStrategy.scala"
                }

                # ── chess.controller.strategy ────────────────────────────────────
                group "chess.controller.strategy" {
                    evaluatorObj          = component "Evaluator" "object. materialValue(role): Int. pstBonus(role,square,color): Int. evaluate(board,color): Int." "object · Evaluator.scala"
                    randomStratClass      = component "RandomStrategy" "class. Picks uniformly random legal move." "class · RandomStrategy.scala"
                    greedyStratClass      = component "GreedyStrategy" "class. Prefers highest-value captures; falls back to random quiet move." "class · GreedyStrategy.scala"
                    materialStratClass    = component "MaterialBalanceStrategy" "class. Scores each legal move with Evaluator.materialValue(); picks max." "class · MaterialBalanceStrategy.scala"
                    pstStratClass         = component "PieceSquareStrategy" "class. Scores with Evaluator.evaluate() (material + PST bonus)." "class · PieceSquareStrategy.scala"
                    minimaxStratClass     = component "MinimaxStrategy" "class (depth: Int = 3). Alpha-beta minimax. Terminal: checkmate=+/-20000, stalemate=0. Path-level repetition guard." "class · MinimaxStrategy.scala"
                    quiescenceStratClass  = component "QuiescenceStrategy" "class (depth=3, qDepth=6). Extends minimax with capture-only quiescence search." "class · QuiescenceStrategy.scala"
                    idStratClass          = component "IterativeDeepeningStrategy" "class (timeLimitMs=2000). Iterates depth 1..∞; stops when time budget expires; returns best completed-depth result." "class · IterativeDeepeningStrategy.scala"
                    openingBookStratClass = component "OpeningBookStrategy" "class (openings, fallback=QuiescenceStrategy). Picks one opening randomly on White's first move; follows book by ply index; abandons if opponent deviates; delegates to fallback thereafter. reset() re-picks for next game." "class · OpeningBookStrategy.scala"
                }

                # ── chess.controller.io ───────────────────────────────────────────
                group "chess.controller.io" {
                    fenIOTrait     = component "FenIO"     "trait. load(s: String): Try[Board]. save(b: Board): String. Pluggable FEN serialisation; default bound via AppBindings given." "trait · FenIO.scala"
                    pgnIOTrait     = component "PgnIO"     "trait. loadFile(path), parseMove(san,board,color), toAlgebraic(from,to,before,after,isWhite), pgnText(moves). Pluggable PGN serialisation." "trait · PgnIO.scala"
                    fileIOTrait    = component "FileIO"    "trait. save(board): String. load(s: String): Try[Board]. Abstracts JSON board file persistence." "trait · FileIO.scala"
                    openingIOTrait = component "OpeningIO" "trait. parseLichessOpenings(): List[Opening], parseTsvResource(path): Try[List[Opening]], parseCsvResource(path): Try[List[Opening]]. Bound via AppBindings given." "trait · OpeningIO.scala"
                }
                group "chess.controller.io.fen" {
                    regexFenComp      = component "RegexFenParser"      "object (FenIO). Regex + split parser. DEFAULT AppBindings binding." "object · RegexFenParser.scala"
                    combinatorFenComp = component "CombinatorFenParser" "object (FenIO). scala-parser-combinators grammar. Alternative." "object · CombinatorFenParser.scala"
                    fastParseFenComp  = component "FastParseFenParser"  "object (FenIO). fastparse grammar. Alternative." "object · FastParseFenParser.scala"
                }
                group "chess.controller.io.pgn" {
                    pgnParserComp     = component "PGNParser"           "object. toAlgebraic + parseMove. Pawn/piece moves, disambiguation, O-O/O-O-O, promotion, check +, checkmate #." "object · PGNParser.scala"
                    pgnFileIOComp     = component "PgnFileIO"           "class (PgnIO, DEFAULT). Reads .pgn files from disk; delegates parse/format to PGNParser." "class · PgnFileIO.scala"
                    combinatorPgnComp = component "CombinatorPgnParser" "object (PgnIO). scala-parser-combinators. Alternative." "object · CombinatorPgnParser.scala"
                    fastParsePgnComp  = component "FastParsePgnParser"  "object (PgnIO). fastparse. Alternative." "object · FastParsePgnParser.scala"
                }
                group "chess.controller.io.json" {
                    circeJsonComp   = component "CirceJsonFileIO + BoardCodecs (Circe)"    "object (FileIO) + given Encoder/Decoder for Board, Piece, Square. Cross-compatible JSON with uPickle." "object · circe/"
                    upickleJsonComp = component "UPickleJsonFileIO + BoardCodecs (uPickle)" "object (FileIO) + given ReadWriter. Identical JSON structure to Circe variant." "object · upickle/"
                }
                group "chess.controller.io.opening" {
                    openingParserComp = component "OpeningParser" "object (OpeningIO). Classpath parser for Lichess TSV and legacy CSV files. parseLichessOpenings, parseTsvLine, parseCsvOpenings, computeFenAndMoveCount, deduplicate, validateOpenings, printStatistics. DEFAULT OpeningIO binding in AppBindings." "object · OpeningParser.scala"
                }
                group "chess.controller.clock" {
                    clockComp = component "ClockActor" "class. Apache Pekko typed actor. Configurable time controls per Color. Sends Tick; reports TimeUp event." "class · ClockActor.scala"
                }
                group "chess.controller.puzzle" {
                    puzzleParserComp = component "PuzzleParser" "object. Parses Lichess puzzle CSV rows into Puzzle case class instances." "object · PuzzleParser.scala"
                }

                # ── chess.persistence ────────────────────────────────────────────
                group "chess.persistence" {
                    openingRepoTrait = component "OpeningRepository[F[_]]" "trait (higher-kinded). save, saveAll, findByEco, findByEcoAndName, findByName(query,limit), findAll(limit,offset), findByMoveCount(maxMoves), findByFen(fen): F[Option[Opening]], count, deleteAll. Bound via AppBindings given." "trait · OpeningRepository.scala"
                    gameRepoTrait    = component "GameRepository[F[_]]"    "trait (higher-kinded). save(game), findById(id), findAll(limit,offset), findByStatus(status), delete(id): F[Boolean]." "trait · GameRepository.scala"
                }
                group "chess.persistence.memory" {
                    inMemoryOpeningComp = component "InMemoryOpeningRepository" "class. Cats Effect IO + mutable Map[(eco,name), Opening] + Map[fen, Opening]. findByFen is O(1) via FEN index. fromLichess()(using OpeningIO): loads all 5 TSV files at startup. DEFAULT OpeningRepository[IO] binding in AppBindings." "class · InMemoryOpeningRepository.scala"
                }
                group "chess.persistence.postgres" {
                    postgresGameComp    = component "PostgresGameRepository"    "class (GameRepository[IO]). Doobie HikariCP Transactor. Table: games(id UUID, fen, pgn, status, created_at, updated_at). ON CONFLICT upsert." "class · PostgresGameRepository.scala"
                    postgresOpeningComp = component "PostgresOpeningRepository" "class (OpeningRepository[IO]). Doobie. Table: openings(eco,name,moves,fen,move_count). Composite PK (eco,name). Bulk INSERT ON CONFLICT DO NOTHING." "class · PostgresOpeningRepository.scala"
                }
                group "chess.persistence.mongodb" {
                    mongoGameComp    = component "MongoGameRepository"    "class (GameRepository[IO]). mongo4cats. Collection: games. Filter/Sort/Skip/Limit fluent API. UUID stored as String." "class · MongoGameRepository.scala"
                    mongoOpeningComp = component "MongoOpeningRepository" "class (OpeningRepository[IO]). mongo4cats. Collection: openings. Filter.regex for name search." "class · MongoOpeningRepository.scala"
                }

                # ── chess.microservices.game (HTTP entry point) ──────────────────
                group "chess.microservices.game" {
                    gameServerComp = component "GameServer"  "object (IOApp). Builds EmberServer; wires GameService with per-game GameController instances and a GameRepository (Postgres or MongoDB)." "object · GameServer.scala"
                    gameRoutesComp = component "GameRoutes"  "object. REST: POST /api/games, GET /api/games/:id, POST /api/games/:id/moves, DELETE /api/games/:id, GET /health. JSON via ApiModels." "object · GameRoutes.scala"
                    gameSvcComp    = component "GameService" "class. createGame(startFen?), getGame(id), makeMove(id,san): IO[Either[String,(String,Option[String])]]. Detects check/checkmate/stalemate/threefold_repetition. Sessions in Ref[IO, Map[UUID,GameController]]." "class · GameService.scala"
                }
                group "chess.microservices.shared" {
                    apiModelsComp = component "ApiModels" "Circe case classes: CreateGameRequest(startFen?), CreateGameResponse(gameId,fen), GameStateResponse(gameId,fen,pgn,status), MakeMoveRequest(move), MakeMoveResponse(success,fen,event?), MoveHistoryResponse(moves), FenResponse, LoadFenRequest/Response, ErrorResponse(error,details?), HealthResponse(status,service)." "case classes · ApiModels.scala"
                }
            }

            # ================================================================
            # UI SERVICE — chess.microservices.ui.UIServer :8082
            # NOTE: Has Dockerfile.ui-service but is NOT deployed in docker-compose.yml.
            # The Vue UI container (chess-vue-ui) fulfils the same role in the compose stack.
            # ================================================================
            uiService = container "UI Service" "Serves static web front-end assets to browser clients. Has Dockerfile.ui-service but is NOT included in docker-compose.yml — the Vue UI container replaces it in the compose stack." "Scala 3 / http4s Ember · port 8082" "Microservice" {
                group "chess.microservices.ui" {
                    uiServerComp = component "UIServer" "object (IOApp). Serves classpath /static files. Returns 404 for unknown paths. GET /health." "object · UIServer.scala"
                }
            }

            # ================================================================
            # VUE UI — Vue.js 3 SPA served by nginx  (docker-compose: chess-vue-ui)
            # Deployed as a Docker container; replaces the Scala UI Service in the compose stack.
            # ================================================================
            vueUI = container "Vue UI" "Vue.js 3 single-page application built with Vite and TypeScript. Manages game state with Pinia; renders the chess board using chess.js; calls the API Gateway for all game operations. Built with 'npm run build' and served by nginx with SPA fallback routing." "Vue 3 / TypeScript / Pinia / Tailwind CSS / chess.js / nginx:alpine · host port 3000" "WebApp"

            # ================================================================
            # SEEDER — chess.seeder subproject (one-shot CLI tool)
            # NOTE: Has no Docker container — run locally via sbt.
            # ================================================================
            seeder = container "Seeder" "One-shot CLI tool that seeds the Lichess opening library into PostgreSQL and MongoDB. Run via: sbt 'seeder/runMain chess.seeder.SeedOpeningsApp'." "Scala 3 / Cats Effect IO · sbt subproject" "Application" {
                group "chess.seeder" {
                    openingSeederComp = component "OpeningSeeder" "object. seedLichessOpenings(repo): IO[Int], seedFromTsvResource(repo,path): IO[Int], seedFromCsvResource(repo,path): IO[Int]. Delegates parsing to OpeningParser from the Chess container." "object · OpeningSeeder.scala"
                    seedAppComp       = component "SeedOpeningsApp" "object (IOApp). Parses all five Lichess TSV files via OpeningParser, writes to Postgres + MongoDB, prints timing comparison table." "object · SeedOpeningsApp.scala"
                }
            }

            # ── Databases ────────────────────────────────────────────────────
            postgres = container "PostgreSQL" "Tables: games, openings. Accessed via Doobie HikariCP Transactor." "PostgreSQL 16" "Database"
            mongodb  = container "MongoDB"    "Collections: games, openings. Accessed via mongo4cats."            "MongoDB 7"    "Database"
        }

        # ── People → System ────────────────────────────────────────────────────
        player    -> chessSystem       "Plays chess"
        developer -> chessSystem       "Operates, seeds data"
        chessSystem -> lichessOpenings "Reads TSV opening files at startup"

        # ── People → Containers ────────────────────────────────────────────────
        player    -> desktopApp "Launches desktop app"
        player    -> gateway    "HTTP requests (REST client)"
        player    -> vueUI      "Opens web chess app" "HTTP browser"
        developer -> seeder     "Runs one-shot seeding CLI"

        # ── Container → Container ──────────────────────────────────────────────
        gateway     -> gameService "Proxies /api/games/*" "HTTP/JSON"
        gateway     -> uiService   "Proxies /* (when ui-service is deployed)" "HTTP"
        vueUI       -> gateway     "REST API calls via VITE_API_URL" "HTTP/JSON"
        gameService -> postgres    "Reads / writes games and openings" "JDBC / Doobie"
        gameService -> mongodb     "Reads / writes games and openings" "mongo4cats"
        gameService -> lichessOpenings "Loads TSV opening files from classpath at startup"
        desktopApp  -> postgres    "Reads / writes games and openings" "JDBC / Doobie"
        desktopApp  -> mongodb     "Reads / writes games and openings" "mongo4cats"
        desktopApp  -> lichessOpenings "Loads TSV opening files from classpath at startup"
        seeder      -> gameService "Uses OpeningParser (in-process via sbt dependsOn)"
        seeder      -> postgres    "Bulk-inserts openings" "JDBC / Doobie"
        seeder      -> mongodb     "Bulk-inserts openings" "mongo4cats"
        seeder      -> lichessOpenings "Reads TSV files from classpath"

        # ── chess.model — Level 4 class relationships ─────────────────────────
        pieceClass          -> roleEnum            "role: Role"
        pieceClass          -> colorEnum           "color: Color"
        squareClass         -> fileEnum            "file: File"
        squareClass         -> rankEnum            "rank: Rank"
        castlingRightsClass -> colorEnum           "revokeKing(color: Color)"
        castlingRightsClass -> squareClass         "revokeRook(from: Square)"
        boardClass          -> pieceClass          "squares: Vector[Vector[Option[Piece]]]"
        boardClass          -> squareClass         "lastMove: Option[(Square,Square)]"
        boardClass          -> castlingRightsClass "castlingRights: CastlingRights"
        boardClass          -> moveResultTrait     "move(from,to): MoveResult"
        boardClass          -> gameEventEnum       "Produces GameEvent on move"
        movedClass          -> boardClass          "board: Board"
        movedClass          -> gameEventEnum       "gameEvent: GameEvent"
        failedClass         -> boardClass          "board: Board"
        failedClass         -> moveErrorEnum       "error: MoveError"
        moveResultTrait     -> boardClass          "contains board: Board"
        promotableRoleEnum  -> roleEnum            "toRole: Role"

        # ── chess.controller — relationships ──────────────────────────────────
        gameCtrlComp       -> boardClass        "Creates, tracks, applies moves"
        gameCtrlComp       -> moveResultTrait   "Produces; notifies observers with"
        gameCtrlComp       -> fenIOTrait        "Parses / serialises FEN"
        gameCtrlComp       -> pgnIOTrait        "Parses / serialises PGN"
        gameCtrlComp       -> openingRepoTrait  "Looks up opening name"
        gameCtrlComp       -> observerComp      "Extends Observable[MoveResult]"
        computerPlayerComp -> moveStrategyTrait "Delegates selectMove to"
        computerPlayerComp -> gameCtrlComp      "Reads board; applies chosen move"
        computerPlayerComp -> evaluatorObj      "Reads material balance for repetition guard"

        minimaxStratClass    -> evaluatorObj    "evaluate(board, color)"
        quiescenceStratClass   -> evaluatorObj          "evaluate(board, color)"
        idStratClass           -> evaluatorObj          "evaluate(board, color)"
        openingBookStratClass  -> openingParserComp     "parseLichessOpenings() at construction"
        openingBookStratClass  -> quiescenceStratClass  "delegates to fallback after opening"
        materialStratClass   -> evaluatorObj    "materialValue(role)"
        pstStratClass        -> evaluatorObj    "evaluate(board, color)"

        # chess.controller.io
        fenIOTrait    -> regexFenComp       "implemented by (default)"
        fenIOTrait    -> combinatorFenComp  "implemented by (alternative)"
        fenIOTrait    -> fastParseFenComp   "implemented by (alternative)"
        pgnIOTrait    -> pgnFileIOComp      "implemented by (default)"
        pgnIOTrait    -> combinatorPgnComp  "implemented by (alternative)"
        pgnIOTrait    -> fastParsePgnComp   "implemented by (alternative)"
        pgnFileIOComp -> pgnParserComp      "Delegates parsing / formatting"

        # chess.persistence
        openingRepoTrait -> inMemoryOpeningComp  "implemented by"
        openingRepoTrait -> postgresOpeningComp  "implemented by"
        openingRepoTrait -> mongoOpeningComp     "implemented by"
        gameRepoTrait    -> postgresGameComp     "implemented by"
        gameRepoTrait    -> mongoGameComp        "implemented by"
        postgresGameComp    -> postgres          "JDBC / Doobie"
        postgresOpeningComp -> postgres          "JDBC / Doobie"
        mongoGameComp       -> mongodb           "mongo4cats"
        mongoOpeningComp    -> mongodb           "mongo4cats"
        openingIOTrait      -> openingParserComp  "implemented by (DEFAULT)"
        inMemoryOpeningComp -> openingIOTrait     "Seeded at startup via (using OpeningIO)"
        openingSeederComp   -> openingParserComp  "Delegates parsing to"
        openingSeederComp   -> openingRepoTrait   "Bulk-saves via"
        seedAppComp         -> openingSeederComp  "Calls seed methods"
        seedAppComp         -> openingParserComp  "Calls parseLichessOpenings + printStatistics"

        # chess.aview
        chessAppComp    -> appBindingsComp  "Imports given instances from"
        chessAppComp    -> chessGUIComp     "Creates and launches"
        chessAppComp    -> consoleViewComp  "Creates and launches"
        chessGUIComp    -> gameCtrlComp     "applyMove / applyPgnMove; reads board, pgnText"
        consoleViewComp -> gameCtrlComp     "applyMove / applyPgnMove; reads board"
        chessGUIComp    -> observerComp     "Extends Observer[MoveResult]"
        consoleViewComp -> observerComp     "Extends Observer[MoveResult]"
        chessGUIComp    -> openingRepoTrait "Displays opening name for position"

        # Cross-container: Game Service uses Chess components
        gameSvcComp    -> gameCtrlComp   "Creates per-game GameController instances"
        gameSvcComp    -> gameRepoTrait  "Persists / retrieves game state"
        gameRoutesComp -> gameSvcComp    "Delegates business logic"
        gameRoutesComp -> apiModelsComp  "Encodes / decodes JSON"
        gameServerComp -> gameRoutesComp "Mounts routes"

        # Cross-container: API Gateway internal
        gatewayServerComp -> gatewayRoutesComp "Mounts routes"
        gatewayRoutesComp -> serviceProxyComp  "Delegates proxy"
        gatewayRoutesComp -> gatewayConfigComp "Reads service URLs"
        serviceProxyComp  -> gatewayConfigComp "Reads target base URLs"

        # ── Deployment environments ─────────────────────────────────────────
        # docker-compose.yml: five containers on the chess-network bridge
        # Not containerised: desktopApp, seeder (CLI), uiService (Dockerfile.ui-service exists but not in compose).
        deploymentEnvironment "Docker Compose" {

            deploymentNode "chess-network" "Docker bridge network shared by all compose services" "Docker bridge" {

                deploymentNode "chess-mongodb" "MongoDB database container" "Image: mongo:7.0 · port 27017 · volume: mongodb_data · healthcheck: mongosh ping" {
                    containerInstance mongodb
                }

                deploymentNode "chess-postgres" "PostgreSQL database container" "Image: postgres:16-alpine · port 5432 · volume: postgres_data · healthcheck: pg_isready" {
                    containerInstance postgres
                }

                deploymentNode "chess-game-service" "Game Service JVM container" "Image: eclipse-temurin:21-jre · port 8081 · Built from Dockerfile.game-service · depends_on: mongodb (healthy), postgres (healthy)" {
                    containerInstance gameService
                }

                deploymentNode "chess-api-gateway" "API Gateway JVM container" "Image: eclipse-temurin:21-jre · port 8080 · Built from Dockerfile.gateway · depends_on: game-service (healthy)" {
                    containerInstance gateway
                }

                deploymentNode "chess-vue-ui" "Vue.js frontend nginx container" "Image: nginx:alpine · host port 3000 → container port 80 · Built from Dockerfile.vue-ui · depends_on: api-gateway" {
                    containerInstance vueUI
                }
            }
        }
    }

    views {

        # ── Level 1 ──────────────────────────────────────────────────────────
        systemContext chessSystem "L1_SystemContext" {
            include *
            autoLayout
            title "Level 1 - System Context"
        }

        # ── Level 2 ──────────────────────────────────────────────────────────
        container chessSystem "L2_Containers" {
            include *
            autoLayout
            title "Level 2 - Containers (Desktop App, API Gateway, Game Service, Vue UI, UI Service, Seeder, PostgreSQL, MongoDB)"
        }

        # ── Level 3 — per-container component views ────────────────────────────────
        component desktopApp "L3_DesktopApp" {
            include *
            autoLayout
            title "Level 3 - Chess Desktop App (chess.aview + chess root)"
        }

        component gameService "L3_GameService" {
            include *
            autoLayout
            title "Level 3 - Game Service (chess engine: model, controller, persistence + microservice HTTP layer)"
        }

        component gateway "L3_Gateway" {
            include *
            autoLayout
            title "Level 3 - API Gateway (chess.microservices.gateway)"
        }

        component uiService "L3_UIService" {
            include *
            autoLayout
            title "Level 3 - UI Service (chess.microservices.ui)"
        }

        # ── Level 4 — chess.model: individual classes, enums, sealed traits ────
        component gameService "L4_Model" {
            include colorEnum roleEnum promotableRoleEnum pieceClass
            include fileEnum rankEnum squareClass
            include castlingRightsClass boardClass
            include moveResultTrait movedClass failedClass
            include gameEventEnum moveErrorEnum puzzleClass
            include openingClass persistedGameClass
            autoLayout
            title "Level 4 - chess.model (all classes, enums, sealed traits)"
        }

        # ── Level 4 — chess.controller: engine and AI strategy classes ─────────
        component gameService "L4_Controller" {
            include gameCtrlComp computerPlayerComp moveStrategyTrait observerComp
            include evaluatorObj randomStratClass greedyStratClass materialStratClass
            include pstStratClass minimaxStratClass quiescenceStratClass idStratClass
            include openingBookStratClass
            autoLayout
            title "Level 4 - chess.controller + chess.controller.strategy (classes, objects, traits)"
        }

        # ── Level 4 — chess.controller.io: I/O traits and implementations ──────
        component gameService "L4_IO" {
            include fenIOTrait regexFenComp combinatorFenComp fastParseFenComp
            include pgnIOTrait pgnParserComp pgnFileIOComp combinatorPgnComp fastParsePgnComp
            include fileIOTrait circeJsonComp upickleJsonComp
            include openingIOTrait openingParserComp
            autoLayout
            title "Level 4 - chess.controller.io (FEN, PGN, JSON, Opening I/O traits and implementations)"
        }

        # ── Level 4 — chess.persistence: models, traits, adapters ──────────────
        component gameService "L4_Persistence" {
            include openingRepoTrait gameRepoTrait
            include inMemoryOpeningComp
            include postgresGameComp postgresOpeningComp
            include mongoGameComp mongoOpeningComp
            autoLayout
            title "Level 4 - chess.persistence (repository traits and adapters)"
        }

        # ── Level 4 — chess.aview: views and entry points ──────────────────────
        component desktopApp "L4_AView" {
            include chessGUIComp consoleViewComp appBindingsComp chessAppComp
            autoLayout
            title "Level 4 - chess.aview + chess root (views, wiring, entry points)"
        }

        # ── Deployment — Docker Compose stack ────────────────────────────────────
        deployment chessSystem "Docker Compose" "Deployment_DockerCompose" {
            include *
            autoLayout
            title "Deployment - Docker Compose (docker-compose.yml): chess-mongodb, chess-postgres, chess-game-service, chess-api-gateway, chess-vue-ui"
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
            element "WebApp" {
                shape WebBrowser
                background #E65100
                color #ffffff
            }
            element "Component" {
                background #85BBF0
                color #000000
            }
        }
    }
}
