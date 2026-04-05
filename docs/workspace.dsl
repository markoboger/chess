workspace "Chess Application" "A Scala chess application with standalone desktop UI and a microservices backend." {

    model {

        # ── People ─────────────────────────────────────────────────────────────
        player = person "Chess Player" "A human playing chess against another human or the computer."
        developer = person "Developer" "Seeds openings data and operates the system."

        # ── External systems ───────────────────────────────────────────────────
        lichessOpenings = softwareSystem "Lichess Openings Dataset" "TSV files (a–e) containing ECO codes, opening names, and PGN move sequences." "External"

        # ── Chess Software System ──────────────────────────────────────────────
        chessSystem = softwareSystem "Chess Application" "Provides chess gameplay via a desktop GUI/console and via HTTP microservices." {

            # ── Standalone desktop application ─────────────────────────────────
            standaloneApp = container "Standalone App" "Single-process Scala application. Starts either ConsoleView or ChessGUI." "Scala / ScalaFX" "Application" {

                chessGUI      = component "ChessGUI"       "JavaFX board UI with drag-and-drop, move history, FEN/PGN display, New Game, Back/Forward." "ScalaFX / Scala"
                consoleView   = component "ConsoleView"    "ANSI terminal UI. Reads PGN moves from stdin, prints the board and events." "Scala"
                gameCtrl      = component "GameController" "Core chess engine: applies moves, tracks history, detects check/checkmate/stalemate/threefold-repetition, exposes FEN/PGN." "Scala"
                computerPlayer = component "ComputerPlayer" "Wraps a MoveStrategy; adds repetition-avoidance when the engine is ahead in material." "Scala"
                strategies    = component "Move Strategies" "Random · Greedy · MaterialBalance · PieceSquare · Minimax (α-β) · Quiescence · IterativeDeepening" "Scala"
                fenIO         = component "FenIO"          "Pluggable FEN parser/serialiser. Implementations: Regex · Combinator · FastParse." "Scala"
                pgnIO         = component "PgnIO"          "Pluggable PGN parser/serialiser. Implementations: File · Combinator · FastParse." "Scala"
                openingRepo   = component "OpeningRepository" "In-memory opening book loaded from Lichess TSV files at start-up. Supports ECO lookup, name search, move-count filter." "Scala / Cats Effect IO"
                puzzleCtrl    = component "PuzzleController" "Delivers chess puzzles from the opening repository." "Scala"
            }

            # ── API Gateway ────────────────────────────────────────────────────
            gateway = container "API Gateway" "Single entry-point. Routes /api/games/* to the Game Service and /* to the UI Service. Performs transparent HTTP proxy." "Scala / http4s Ember (port 8080)" "Microservice" {
                gatewayRoutes = component "GatewayRoutes"  "http4s HttpRoutes: /health, /api/games/* proxy, /* proxy." "http4s / Scala"
                serviceProxy  = component "ServiceProxy"   "Forwards requests and streams responses using EmberClient." "http4s / Scala"
                gatewayConfig = component "GatewayConfig"  "Reads GAME_SERVICE_URL, UI_SERVICE_URL, PORT from environment." "Scala"
            }

            # ── Game Service ───────────────────────────────────────────────────
            gameService = container "Game Service" "Stateful chess game microservice. Manages multiple concurrent game sessions in memory." "Scala / http4s Ember (port 8081)" "Microservice" {
                gameRoutes  = component "GameRoutes"   "REST endpoints: POST /api/games, GET /api/games/:id, POST /api/games/:id/moves, DELETE /api/games/:id, GET /health." "http4s / Scala"
                gameSvc     = component "GameService"  "Business logic: create/find/delete games, apply PGN moves, detect game events (check, checkmate, stalemate, threefold-repetition)." "Scala / Cats Effect IO"
                gameCtrl2   = component "GameController (instance)" "Per-game chess engine instance reused from the shared controller module." "Scala"
                gameRepo    = component "GameRepository" "Persists game snapshots. Implementations: PostgresGameRepository · MongoGameRepository." "Scala / Cats Effect IO"
                sharedModels = component "API Models"  "CreateGameRequest, CreateGameResponse, MakeMoveRequest, MakeMoveResponse, ErrorResponse encoded with Circe." "Circe / Scala"
            }

            # ── UI Service ─────────────────────────────────────────────────────
            uiService = container "UI Service" "Serves the web front-end assets." "Scala / http4s Ember (port 8082)" "Microservice" {
                uiRoutes = component "UIRoutes" "Serves static files and a health endpoint." "http4s / Scala"
            }

            # ── Persistence ────────────────────────────────────────────────────
            postgres = container "PostgreSQL" "Stores game snapshots and chess openings in relational tables." "PostgreSQL 15" "Database"
            mongodb  = container "MongoDB"    "Stores game snapshots and chess openings as BSON documents." "MongoDB 7" "Database"
        }

        # ── Relationships: people → system ─────────────────────────────────────
        player    -> chessSystem      "Plays chess"
        developer -> chessSystem      "Operates and seeds data"

        # ── Relationships: system → external ───────────────────────────────────
        chessSystem -> lichessOpenings "Reads TSV opening files at startup"

        # ── Relationships: containers ─────────────────────────────────────────
        player      -> standaloneApp  "Launches directly (desktop)"
        player      -> gateway        "HTTP (browser or API client)"

        gateway     -> gameService    "Proxies /api/games/* requests" "HTTP/JSON"
        gateway     -> uiService      "Proxies /* requests"           "HTTP"

        gameService -> postgres       "Reads / writes game & opening data" "JDBC / Doobie"
        gameService -> mongodb        "Reads / writes game & opening data" "mongo4cats"

        developer   -> postgres       "Seeds openings via SeedOpeningsApp"
        developer   -> mongodb        "Seeds openings via SeedOpeningsApp"
        standaloneApp -> lichessOpenings "Loads TSV files from classpath resources"

        # ── Relationships: standalone app components ───────────────────────────
        player       -> chessGUI      "Interacts with board"
        player       -> consoleView   "Types PGN moves"
        chessGUI     -> gameCtrl      "Applies moves, reads board state"
        consoleView  -> gameCtrl      "Applies moves, reads board state"
        gameCtrl     -> fenIO         "Parses / serialises FEN"
        gameCtrl     -> pgnIO         "Parses / serialises PGN"
        gameCtrl     -> openingRepo   "Looks up opening by position"
        computerPlayer -> strategies  "Delegates move selection"
        computerPlayer -> gameCtrl    "Reads board, applies chosen move"
        chessGUI     -> openingRepo   "Displays opening name for current position"
        puzzleCtrl   -> openingRepo   "Fetches puzzles"

        # ── Relationships: gateway components ─────────────────────────────────
        gatewayRoutes -> serviceProxy  "Delegates proxy requests"
        gatewayRoutes -> gatewayConfig "Reads service URLs"
        serviceProxy  -> gatewayConfig "Reads target base URLs"

        # ── Relationships: game service components ────────────────────────────
        gameRoutes -> gameSvc       "Calls service methods"
        gameRoutes -> sharedModels  "Encodes / decodes JSON"
        gameSvc    -> gameCtrl2     "Creates and uses per-game instances"
        gameSvc    -> gameRepo      "Persists game state"
    }

    views {

        # ── Level 1: System Context ────────────────────────────────────────────
        systemContext chessSystem "SystemContext" {
            include *
            autoLayout
            title "Chess Application – System Context"
        }

        # ── Level 2: Container diagram ────────────────────────────────────────
        container chessSystem "Containers" {
            include *
            autoLayout
            title "Chess Application – Containers"
        }

        # ── Level 3: Standalone App components ────────────────────────────────
        component standaloneApp "StandaloneComponents" {
            include *
            autoLayout
            title "Standalone App – Components"
        }

        # ── Level 3: API Gateway components ───────────────────────────────────
        component gateway "GatewayComponents" {
            include *
            autoLayout
            title "API Gateway – Components"
        }

        # ── Level 3: Game Service components ──────────────────────────────────
        component gameService "GameServiceComponents" {
            include *
            autoLayout
            title "Game Service – Components"
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
            element "Container" {
                background #438DD5
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
