workspace "Chess Application" "Chess platform with a docker-compose runtime, session-aware frontend clients, and an sbt-modularised backend." {

    model {

        player = person "Chess Player" "Plays chess through the web frontend or the desktop app."
        developer = person "Developer" "Builds, runs, and seeds the system."

        lichessOpenings = softwareSystem "Lichess Openings Dataset" "Bundled TSV opening files used to seed and enrich the application." "External"

        chessSystem = softwareSystem "Chess Application" "A chess platform whose deployed runtime is defined by docker-compose and whose backend code is split into the sbt modules core, app, data, realtime, and the root src runtime layer." {

            vueUi = container "Vue UI" "Browser frontend built with Vue 3 and served from nginx. It creates and joins game sessions through the gateway, then subscribes to live move updates over WebSocket. This is the `vue-ui` service in docker-compose." "Vue 3 / TypeScript / Pinia / nginx" "WebApp"

            apiGateway = container "API Gateway" "Public HTTP entrypoint. Proxies frontend API calls for game creation, session listing, state reads, moves, and opening lookup to the backend game service. This is the `api-gateway` service in docker-compose." "Scala 3 / http4s Ember" "Microservice"

            gameService = container "Game Service" "Authoritative backend for chess games and sessions. This is the `game-service` service in docker-compose. It is one JVM process built from the repository's modular backend rather than a set of HTTP-connected internal services." "Scala 3 / Cats Effect / http4s Ember" "Microservice" {

                srcModule = component "src" "Root runtime layer. Contains service entrypoints, session HTTP routes, gateway-facing contracts, desktop wiring, and composition roots such as AppBindings and the microservice servers." "root project / src"

                appModule = component "app" "Application-service layer. Holds GameSessionService, GameController, strategies, clocks, opening logic, puzzle logic, and the event-emitting session orchestration used by both the HTTP API and the desktop GUI." "sbt subproject / app"

                coreModule = component "core" "Pure chess domain and notation layer. Holds immutable rules, board logic, pieces, move results, FEN/PGN parsing, and reusable chess primitives." "sbt subproject / core"

                dataModule = component "data" "Persistence layer. Holds repository traits plus in-memory, PostgreSQL, and MongoDB implementations." "sbt subproject / data"

                realtimeModule = component "realtime" "Realtime/event-delivery module. Provides event ingestion routes, heartbeat-enabled WebSocket fan-out, and the HTTP publisher used when game events are forwarded to a standalone realtime server." "sbt subproject / realtime"
            }

            postgres = container "PostgreSQL" "Relational database used by the backend through the data module. This is the `postgres` service in docker-compose." "PostgreSQL 16" "Database"

            mongodb = container "MongoDB" "Document database used by the backend through the data module. This is the `mongodb` service in docker-compose." "MongoDB 7" "Database"

            realtimeService = container "Realtime Service" "Standalone realtime/WebSocket server used for live session updates in local and session-enabled deployments. It is implemented from the `realtime` module but is not currently part of docker-compose.yml." "Scala 3 / Cats Effect / http4s Ember WebSockets" "Microservice"

            desktopApp = container "Chess Desktop App" "Local JVM desktop application for GUI/console play. It can also join backend game sessions, publish moves over HTTP, and subscribe to live updates over WebSocket. Part of the repository, but not part of docker-compose." "Scala 3 / ScalaFX / local JVM" "Application"

            seeder = container "Seeder" "One-shot CLI that loads opening data into the databases. Part of the repository, but not part of docker-compose." "Scala 3 / Cats Effect / local CLI" "Application"

            matchRunner = container "Match Runner" "Automated experiment harness that runs batches of computer-vs-computer games, records results in PostgreSQL, and exposes a REST API plus an interactive TUI for analysis. Not part of docker-compose." "Scala 3 / Cats Effect / http4s Ember / Doobie" "Microservice"
        }

        player -> chessSystem "Uses"
        developer -> chessSystem "Builds and operates"

        player -> vueUi "Uses in browser"
        player -> desktopApp "Uses locally"
        developer -> seeder "Runs when seeding opening data"

        vueUi -> apiGateway "Creates games, joins sessions, reads state, posts moves, and looks up openings" "HTTP/JSON"
        apiGateway -> gameService "Proxies session and opening requests" "HTTP/JSON"
        vueUi -> realtimeService "Subscribes to live session updates after joining a game" "WebSocket /ws/:gameId"

        gameService -> postgres "Reads and writes games/openings" "JDBC / Doobie"
        gameService -> mongodb "Reads and writes games/openings" "MongoDB driver / mongo4cats"
        gameService -> realtimeService "Publishes session events such as move_applied, fen_loaded, and game_deleted" "HTTP/JSON POST /events"
        gameService -> lichessOpenings "Loads bundled opening data" "Classpath resources"

        desktopApp -> gameService "Creates or joins sessions and posts local moves" "HTTP/JSON"
        desktopApp -> realtimeService "Subscribes to live session updates when in a shared game" "WebSocket /ws/:gameId"
        desktopApp -> postgres "Reads and writes games/openings" "JDBC / Doobie"
        desktopApp -> mongodb "Reads and writes games/openings" "MongoDB driver / mongo4cats"
        desktopApp -> lichessOpenings "Loads bundled opening data" "Classpath resources"

        seeder -> postgres "Seeds openings" "JDBC / Doobie"
        seeder -> mongodb "Seeds openings" "MongoDB driver / mongo4cats"
        seeder -> lichessOpenings "Reads bundled opening data" "Classpath resources"

        matchRunner -> gameService "Creates CvC game sessions, polls state, and loads PGN replays" "HTTP/JSON"
        matchRunner -> postgres "Stores experiment and match-run records" "JDBC / Doobie"
        developer -> matchRunner "Launches experiments and browses results via TUI"
        desktopApp -> matchRunner "Fetches experiment and run lists for in-GUI game browser" "HTTP/JSON :8084"

        srcModule -> appModule "Invokes application services and controllers" "In-process calls"
        srcModule -> dataModule "Wires repository implementations through AppBindings" "In-process calls"
        srcModule -> realtimeModule "Uses realtime publisher and event transport code" "In-process calls"

        appModule -> coreModule "Uses chess rules, notation, and immutable domain types" "In-process calls"
        appModule -> dataModule "Uses repositories and persistence-backed data access" "In-process calls"

        dataModule -> coreModule "Persists and reconstructs core domain models" "In-process calls"
        dataModule -> postgres "Stores relational game/opening data"
        dataModule -> mongodb "Stores document game/opening data"

        realtimeModule -> appModule "Implements publishing around app-level game session events" "In-process calls"
        realtimeService -> realtimeModule "Runs the standalone realtime server from the realtime module" "Process entrypoint"
        gameService -> srcModule "Runs the root microservice entrypoints and route wiring" "Process entrypoint"
        desktopApp -> srcModule "Runs the desktop GUI from the root runtime layer" "Process entrypoint"

        deploymentEnvironment "Docker Compose" {
            deploymentNode "chess-network" "Docker bridge network defined in docker-compose.yml" "Docker bridge network" {

                deploymentNode "vue-ui" "Frontend container" "Built from Dockerfile.vue-ui" {
                    containerInstance vueUi
                }

                deploymentNode "api-gateway" "Gateway container" "Built from Dockerfile.api-gateway" {
                    containerInstance apiGateway
                }

                deploymentNode "game-service" "Backend container" "Built from Dockerfile.game-service" {
                    containerInstance gameService
                }

                deploymentNode "postgres" "PostgreSQL container" "Image: postgres:16-alpine" {
                    containerInstance postgres
                }

                deploymentNode "mongodb" "MongoDB container" "Image: mongo:7.0" {
                    containerInstance mongodb
                }
            }
        }
    }

    views {

        systemContext chessSystem "L1_SystemContext" {
            include player
            include developer
            include chessSystem
            include lichessOpenings
            autoLayout
            title "Level 1 - System Context"
        }

        container chessSystem "L2_DockerCompose" {
            include player
            include developer
            include vueUi
            include apiGateway
            include gameService
            include postgres
            include mongodb
            include lichessOpenings
            autoLayout
            title "Level 2 - Docker Compose Runtime (vue-ui, api-gateway, game-service, postgres, mongodb)"
        }

        container chessSystem "L2_SessionRuntime" {
            include player
            include vueUi
            include desktopApp
            include apiGateway
            include gameService
            include realtimeService
            include postgres
            include mongodb
            autoLayout
            title "Level 2 - Session Runtime (HTTP commands plus WebSocket live updates)"
        }

        component gameService "L3_GameServiceModules" {
            include srcModule
            include appModule
            include coreModule
            include dataModule
            include realtimeModule
            include postgres
            include mongodb
            include lichessOpenings
            autoLayout
            title "Level 3 - Game Service Internal Modules (src, app, core, data, realtime)"
        }

        container chessSystem "L2_LocalTools" {
            include player
            include developer
            include desktopApp
            include realtimeService
            include seeder
            include matchRunner
            include postgres
            include mongodb
            include lichessOpenings
            autoLayout
            title "Level 2 - Local Applications and Standalone Services Outside Docker Compose"
        }

        deployment chessSystem "Docker Compose" "Deployment_DockerCompose" {
            include vueUi
            include apiGateway
            include gameService
            include postgres
            include mongodb
            autoLayout
            title "Deployment - Docker Compose (matches docker-compose.yml exactly)"
        }

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
                shape Hexagon
                background #2E7D32
                color #ffffff
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
