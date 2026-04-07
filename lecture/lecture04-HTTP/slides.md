---
marp: true
title: "Lecture 04: HTTP Interface"
description: "Expose the monolithic chess application through a REST API"
paginate: true
theme: htwg
---

<!-- _class: title -->
# Lecture 04
## HTTP Interface for the Monolith

<span class="eyebrow">Software Architecture with AI</span>

Expose the `GameController` through a REST API before we split anything into services

<p class="small">Prof. Dr. Marko Boger</p>

---

# Today’s Goal

We still have:

- one application
- one codebase
- one process

But now we add:

- an HTTP interface
- machine-readable requests and responses
- the possibility to control the chess game remotely

**This is still a monolith. It just speaks HTTP now.**

---

# Why Add HTTP at All?

- the GUI is no longer the only interface
- the game can be controlled from other programs
- we get a clean contract around the application layer
- later this becomes the basis for web UI and microservices
- HTTP is the standard interface language of modern systems

---

# What Is HTTP?

HTTP is an application protocol for communication between client and server.

Main ideas:

- request
- response
- method
- path
- headers
- body
- status code

In short:

**A client asks for something. A server answers in a standardized form.**

---

# Anatomy of an HTTP Request

```http
POST /games/abc123/moves HTTP/1.1
Host: localhost:8081
Content-Type: application/json

{ "move": "e4" }
```

Important parts:

- method: `POST`
- path: `/games/abc123/moves`
- header: `Content-Type`
- body: JSON payload

---

# Anatomy of an HTTP Response

```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "success": true, "fen": "...", "event": null }
```

Important parts:

- status code: `200`
- headers
- response body

The status code already communicates a lot of meaning.

---

# Main HTTP Methods

- `GET`
  read data
- `POST`
  create something or trigger an action
- `PUT`
  replace a resource
- `PATCH`
  partially update a resource
- `DELETE`
  remove a resource

For this lecture, `GET`, `POST`, and `DELETE` are enough.

---

# Important Status Codes

- `200 OK`
  request succeeded
- `201 Created`
  new resource created
- `204 No Content`
  successful request, no response body
- `400 Bad Request`
  client sent invalid input
- `404 Not Found`
  requested resource does not exist
- `500 Internal Server Error`
  server failed unexpectedly

These codes are part of the API contract.

---

# What Is a REST API?

REST is an architectural style for exposing resources through HTTP.

Typical principles:

- identify resources with URLs
- use HTTP methods consistently
- transfer representations, often JSON
- keep requests self-contained
- use status codes meaningfully

REST is not a library.
It is a way of modeling an interface.

---

# Resources in the Chess Domain

Good resource candidates:

- `games`
- `moves`
- `fen`
- `openings`

Examples:

- `POST /games`
- `GET /games/{id}`
- `POST /games/{id}/moves`
- `GET /games/{id}/fen`

So we do not expose random methods.
We expose domain resources.

---

# From Controller to REST

Inside the monolith we may have operations like:

- `createGame`
- `makeMove`
- `getFen`
- `loadFen`

The REST layer wraps these operations and gives them:

- URLs
- HTTP methods
- JSON request and response formats
- proper error codes

---

# Layering With HTTP

```text
┌──────────────────────────────┐
│ GUI / curl / Postman / Browser │
├──────────────────────────────┤
│ HTTP Routes / JSON DTOs      │
├──────────────────────────────┤
│ GameController / App Logic   │
├──────────────────────────────┤
│ Chess Model                  │
└──────────────────────────────┘
```

The HTTP layer is an adapter around the existing monolith.

---

# Design Questions Before Coding

- What are the resources?
- Which routes do we need?
- What JSON should requests contain?
- What JSON should responses return?
- Which status code do we use on success?
- Which status code do we use on failure?

This design work matters more than typing route code.

---

# Chess API Design

Suggested routes for the monolithic chess app:

- `GET /health`
- `POST /games`
- `GET /games/{id}`
- `DELETE /games/{id}`
- `POST /games/{id}/moves`
- `GET /games/{id}/moves`
- `GET /games/{id}/fen`
- `POST /games/{id}/fen`

This already gives us a complete remote interface for the game.

---

# Example Request Models

```json
{ "move": "e4" }
```

```json
{ "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1" }
```

```json
{ "startFen": null }
```

These are not domain entities.
They are API data-transfer objects.

---

# Example Response Models

```json
{ "gameId": "abc123", "fen": "...", "settings": { ... } }
```

```json
{ "success": true, "fen": "...", "event": null }
```

```json
{ "error": "Game not found" }
```

The API should be pleasant for machines to consume.

---

# How Routes Are Represented in Code

At the code level, a route usually combines:

- HTTP method
- path pattern
- extraction of parameters
- parsing of request body
- call into application logic
- conversion into a response

This pattern appears in all Scala web frameworks.

---

# Play Framework Example

In Play, routes are often declared in a routes file:

```text
GET     /games/:id         controllers.GameController.get(id: String)
POST    /games             controllers.GameController.create
POST    /games/:id/moves   controllers.GameController.makeMove(id: String)
```

And then implemented in a controller class.

This is declarative and easy to read.

---

# Simplified Play Controller Example

```scala
def makeMove(id: String): Action[JsValue] = Action(parse.json) { request =>
  (request.body \ "move").asOpt[String] match
    case Some(move) =>
      Ok(Json.obj("success" -> true))
    case None =>
      BadRequest(Json.obj("error" -> "Missing move"))
}
```

This is useful as a reference point:

- routes file outside the Scala code
- controller methods return HTTP results

---

# Why We Use http4s Here

- functional style
- type-safe request and response handling
- good fit with `cats-effect`
- lightweight, explicit, and composable
- no heavy framework magic

For this course, that makes architectural boundaries easier to see.

---

# Main http4s Concepts

- `HttpRoutes[F]`
  a partial function from request to effectful response
- `Request[F]`
  incoming HTTP request
- `Response[F]`
  outgoing HTTP response
- route DSL
  pattern matching for methods and paths
- entity codecs
  encode and decode JSON bodies

---

# A Small http4s Example

```scala
import org.http4s.*
import org.http4s.dsl.io.*

val routes = HttpRoutes.of[IO] {
  case GET -> Root / "health" =>
    Ok("""{ "status": "ok" }""")
}
```

This reads almost like a specification:

- if the request is `GET /health`
- return `200 OK`

---

# Decoding JSON in http4s

```scala
case class MakeMoveRequest(move: String)

case req @ POST -> Root / "games" / gameId / "moves" =>
  req.asJsonDecode[MakeMoveRequest].flatMap { body =>
    Ok(...)
  }
```

The framework:

- reads the request body
- decodes JSON into Scala
- lets our logic work with typed data

---

# Route Pattern Matching

From the chess application:

```scala
case GET -> Root / "games" / gameId =>
  ...

case req @ POST -> Root / "games" / gameId / "moves" =>
  ...
```

The route itself already documents the API:

- method
- URL structure
- path variables

---

# Example From the Chess App

```scala
case req @ POST -> Root / "games" / gameId / "moves" =>
  req.asJsonDecode[MakeMoveRequest].flatMap { request =>
    gameSessions.makeMove(gameId, request.move).flatMap {
      case Right((fen, event)) =>
        Ok(MakeMoveResponse(success = true, fen, event))
      case Left(error) =>
        BadRequest(ErrorResponse(error))
    }
  }
```

---

# Reading This Code

1. match `POST /games/{id}/moves`
2. decode JSON into `MakeMoveRequest`
3. call application logic
4. convert domain result into HTTP response
5. choose `200` or `400`

This is exactly the job of an HTTP adapter.

---

# API DTOs

In the chess application we also define explicit request and response types:

- `CreateGameRequest`
- `CreateGameResponse`
- `MakeMoveRequest`
- `MakeMoveResponse`
- `FenResponse`
- `ErrorResponse`

These make the API:

- explicit
- typed
- testable

---

# Server Bootstrap

In http4s, we still need a server process:

```scala
EmberServerBuilder
  .default[IO]
  .withPort(port"8081")
  .withHttpApp(routes.orNotFound)
  .build
```

This turns our route definitions into a running HTTP server.

---

# Important Architectural Point

At this stage:

- the chess logic is still local
- the controller is still local
- the model is still local
- there is no distributed system yet

We only added an HTTP boundary around the monolith.

This distinction matters.

---

# How to Test an HTTP API

We do not need a browser to test it.

Useful tools:

- `curl`
- Postman
- Bruno
- Insomnia
- browser dev tools
- Scala tests against routes

Each of these is useful for a different stage of development.

---

# Example With curl

Create a game:

```bash
curl -X POST http://localhost:8081/games \
  -H "Content-Type: application/json" \
  -d '{ "startFen": null }'
```

Make a move:

```bash
curl -X POST http://localhost:8081/games/abc123/moves \
  -H "Content-Type: application/json" \
  -d '{ "move": "e4" }'
```

---

# More Useful curl Commands

Get game state:

```bash
curl http://localhost:8081/games/abc123
```

Get FEN:

```bash
curl http://localhost:8081/games/abc123/fen
```

Load a FEN:

```bash
curl -X POST http://localhost:8081/games/abc123/fen \
  -H "Content-Type: application/json" \
  -d '{ "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1" }'
```

---

# Testing From Scala

The chess project also tests routes directly:

```scala
val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
resp.status shouldBe Status.Ok
```

This is a nice property of http4s:

- routes are plain values
- they are easy to test
- we do not always need a running external server

---

# What Students Should Build

Expose the functionality of the game controller through REST.

Minimum scope:

- create a game
- read a game state
- make a move
- get move history
- get FEN
- load FEN
- health endpoint

Optional:

- delete a game
- AI move endpoint

---

# Suggested Work Plan

1. Identify the resources.
2. Define JSON DTOs.
3. Decide status codes.
4. Write route definitions.
5. Connect them to `GameController`.
6. Start the server.
7. Test with `curl` and Postman.
8. Add route-level tests.

---

# Common Mistakes

- exposing internal classes directly as API models
- returning `200` for every outcome
- mixing HTTP logic into the domain layer
- designing routes around method names instead of resources
- forgetting error cases
- thinking "HTTP interface" already means "microservice"

---

# Student Assignment

## Build a REST interface for the monolithic chess application

Implement an HTTP API around the game controller.

Required:

- at least 6 useful routes
- JSON request and response models
- meaningful HTTP status codes
- manual testing with `curl` or Postman
- at least 3 automated route tests

Deliver:

- source code
- route overview
- example requests and responses
- short explanation of how the HTTP layer maps to the controller

---

# Key Message

HTTP is not yet microservices.

It is the first step toward:

- a programmable interface
- UI decoupling
- remote access
- clear contracts

First we teach the monolith to speak HTTP.
Only later do we distribute it.
