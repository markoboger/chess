workspace "Chess Application" "Chess platform with a docker-compose runtime and an sbt-modularised backend." {

    model {

        player = person "Chess Player" "Plays chess through the web frontend or the desktop app."
        developer = person "Developer" "Builds, runs, and seeds the system."

        lichessOpenings = softwareSystem "Lichess Openings Dataset" "Bundled TSV opening files used to seed and enrich the application." "External"

        chessSystem = softwareSystem "Chess Application" "A chess platform whose deployed runtime is defined by docker-compose and whose backend code is split into the sbt modules core, app, data, realtime, and the root src runtime layer." {

            vueUi = container "Vue UI" "Browser frontend built with Vue 3 and served from nginx. This is the `vue-ui` service in docker-compose." "Vue 3 / TypeScript / Pinia / nginx" "WebApp"

            apiGateway = container "API Gateway" "Public HTTP entrypoint. Proxies frontend API calls to the backend game service. This is the `api-gateway` service in docker-compose." "Scala 3 / http4s Ember" "Microservice"

            gameService = container "Game Service" "Authoritative backend for chess games. This is the `game-service` service in docker-compose. It is one JVM process built from the repository's modular backend rather than a set of HTTP-connected internal services." "Scala 3 / Cats Effect / http4s Ember" "Microservice" {

                srcModule = component "src" "Root runtime layer. Contains service entrypoints, HTTP routes, gateway-facing contracts, desktop wiring, and composition roots such as AppBindings and the microservice servers." "root project / src"

                appModule = component "app" "Application-service layer. Holds GameSessionService, GameController, strategies, clocks, opening logic, and puzzle logic." "sbt subproject / app"

                coreModule = component "core" "Pure chess domain and notation layer. Holds immutable rules, board logic, pieces, move results, FEN/PGN parsing, and reusable chess primitives." "sbt subproject / core"

                dataModule = component "data" "Persistence layer. Holds repository traits plus in-memory, PostgreSQL, and MongoDB implementations." "sbt subproject / data"

                realtimeModule = component "realtime" "Realtime/event-delivery module. Provides event ingestion contracts and the HTTP publisher used by the backend. It currently lives in the same JVM as the rest of game-service unless deployed separately later." "sbt subproject / realtime"
            }

            postgres = container "PostgreSQL" "Relational database used by the backend through the data module. This is the `postgres` service in docker-compose." "PostgreSQL 16" "Database"

            mongodb = container "MongoDB" "Document database used by the backend through the data module. This is the `mongodb` service in docker-compose." "MongoDB 7" "Database"

            desktopApp = container "Chess Desktop App" "Local JVM desktop application for GUI/console play. Part of the repository, but not part of docker-compose." "Scala 3 / ScalaFX / local JVM" "Application"

            seeder = container "Seeder" "One-shot CLI that loads opening data into the databases. Part of the repository, but not part of docker-compose." "Scala 3 / Cats Effect / local CLI" "Application"
        }

        player -> chessSystem "Uses"
        developer -> chessSystem "Builds and operates"

        player -> vueUi "Uses in browser"
        player -> desktopApp "Uses locally"
        developer -> seeder "Runs when seeding opening data"

        vueUi -> apiGateway "Calls backend API" "HTTP/JSON"
        apiGateway -> gameService "Proxies game and opening requests" "HTTP/JSON"

        gameService -> postgres "Reads and writes games/openings" "JDBC / Doobie"
        gameService -> mongodb "Reads and writes games/openings" "MongoDB driver / mongo4cats"
        gameService -> lichessOpenings "Loads bundled opening data" "Classpath resources"

        desktopApp -> postgres "Reads and writes games/openings" "JDBC / Doobie"
        desktopApp -> mongodb "Reads and writes games/openings" "MongoDB driver / mongo4cats"
        desktopApp -> lichessOpenings "Loads bundled opening data" "Classpath resources"

        seeder -> postgres "Seeds openings" "JDBC / Doobie"
        seeder -> mongodb "Seeds openings" "MongoDB driver / mongo4cats"
        seeder -> lichessOpenings "Reads bundled opening data" "Classpath resources"

        srcModule -> appModule "Invokes application services and controllers" "In-process calls"
        srcModule -> dataModule "Wires repository implementations through AppBindings" "In-process calls"
        srcModule -> realtimeModule "Uses realtime publisher and event transport code" "In-process calls"

        appModule -> coreModule "Uses chess rules, notation, and immutable domain types" "In-process calls"
        appModule -> dataModule "Uses repositories and persistence-backed data access" "In-process calls"

        dataModule -> coreModule "Persists and reconstructs core domain models" "In-process calls"
        dataModule -> postgres "Stores relational game/opening data"
        dataModule -> mongodb "Stores document game/opening data"

        realtimeModule -> appModule "Implements publishing around app-level game session events" "In-process calls"

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
            include seeder
            include postgres
            include mongodb
            include lichessOpenings
            autoLayout
            title "Level 2 - Local Applications Outside Docker Compose (desktop app and seeder)"
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
