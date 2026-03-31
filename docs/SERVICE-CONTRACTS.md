# Service Contracts - Chess Microservices

This document defines the API contracts between services in the microservices architecture.

## Architecture Overview

```
┌─────────────────────┐
│   API Gateway       │  Port 8080
│   (Entry Point)     │
└──────────┬──────────┘
           │
    ┌──────┴──────┐
    │             │
┌───▼────┐   ┌───▼────┐
│  Game  │   │   UI   │
│Service │   │Service │
│ :8081  │   │ :8082  │
└────────┘   └────────┘
```

## 1. API Gateway Service

**Base URL:** `http://localhost:8080`

### 1.1 Health Check

**Endpoint:** `GET /health`

**Response:**
```json
{
  "status": "ok",
  "service": "api-gateway"
}
```

### 1.2 Game API Proxy

All requests to `/api/games/*` are proxied to the Game Service.

- **Route:** `/api/games/*` → `http://game-service:8081/games/*`
- **Methods:** GET, POST, DELETE
- **Headers:** Preserved from original request
- **Body:** Forwarded as-is

### 1.3 UI Asset Proxy

All other GET requests are proxied to the UI Service for static assets.

- **Route:** `/*` → `http://ui-service:8082/*`
- **Methods:** GET only
- **Content-Type:** Set based on file extension

---

## 2. Game Service

**Base URL:** `http://localhost:8081`

Manages chess game state and logic.

### 2.1 Health Check

**Endpoint:** `GET /health`

**Response:**
```json
{
  "status": "ok",
  "service": "game-service"
}
```

### 2.2 Create New Game

**Endpoint:** `POST /games`

**Request Body:**
```json
{
  "startFen": "optional FEN string"
}
```

**Success Response (200 OK):**
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Invalid FEN: <reason>",
  "details": "optional details"
}
```

### 2.3 Get Game State

**Endpoint:** `GET /games/:gameId`

**Success Response (200 OK):**
```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "pgn": "1. e4",
  "status": "in_progress | checkmate | stalemate"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

### 2.4 Delete Game

**Endpoint:** `DELETE /games/:gameId`

**Success Response:** `204 No Content`

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

### 2.5 Make a Move

**Endpoint:** `POST /games/:gameId/moves`

**Request Body:**
```json
{
  "move": "e4"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "event": "check | checkmate | stalemate | null"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Invalid move: <reason>"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

### 2.6 Get Move History

**Endpoint:** `GET /games/:gameId/moves`

**Success Response (200 OK):**
```json
{
  "moves": ["e4", "e5", "Nf3", "Nc6"]
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

### 2.7 Get Current FEN

**Endpoint:** `GET /games/:gameId/fen`

**Success Response (200 OK):**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

### 2.8 Load FEN Position

**Endpoint:** `POST /games/:gameId/fen`

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Invalid FEN: <reason>"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Game not found"
}
```

---

## 3. UI Service

**Base URL:** `http://localhost:8082`

Serves static frontend assets.

### 3.1 Health Check

**Endpoint:** `GET /health`

**Response:**
```json
{
  "status": "ok",
  "service": "ui-service"
}
```

### 3.2 Serve Index

**Endpoint:** `GET /`

**Response:** HTML content (index.html)

**Content-Type:** `text/html`

### 3.3 Serve Static Assets

**Endpoint:** `GET /<file-path>`

Examples:
- `GET /styles.css` → serves CSS file
- `GET /app.js` → serves JavaScript file

**Content-Type:** Set based on file extension
- `.html` → `text/html`
- `.css` → `text/css`
- `.js` → `text/javascript`
- `.json` → `application/json`

**Error Response (404 Not Found):**
```
File not found: <file-name>
```

---

## 4. Service Discovery

Services discover each other via environment variables:

**API Gateway:**
- `GAME_SERVICE_URL` - URL of Game Service (default: `http://localhost:8081`)
- `UI_SERVICE_URL` - URL of UI Service (default: `http://localhost:8082`)
- `PORT` - Gateway port (default: `8080`)

**Game Service:**
- `PORT` - Service port (default: `8081`)

**UI Service:**
- `PORT` - Service port (default: `8082`)

In Docker Compose, services use DNS names (`game-service`, `ui-service`) for inter-service communication.

---

## 5. Data Models

### CreateGameRequest
```typescript
interface CreateGameRequest {
  startFen?: string;  // Optional custom starting position
}
```

### CreateGameResponse
```typescript
interface CreateGameResponse {
  gameId: string;     // UUID
  fen: string;        // Current position
}
```

### GameStateResponse
```typescript
interface GameStateResponse {
  gameId: string;
  fen: string;
  pgn: string;        // Full move history
  status: string;     // "in_progress" | "checkmate" | "stalemate"
}
```

### MakeMoveRequest
```typescript
interface MakeMoveRequest {
  move: string;       // Algebraic notation, e.g., "e4", "Nf3", "O-O"
}
```

### MakeMoveResponse
```typescript
interface MakeMoveResponse {
  success: boolean;
  fen: string;
  event?: string;     // "check" | "checkmate" | "stalemate"
}
```

### MoveHistoryResponse
```typescript
interface MoveHistoryResponse {
  moves: string[];    // Array of moves in algebraic notation
}
```

### FenResponse
```typescript
interface FenResponse {
  fen: string;
}
```

### LoadFenRequest
```typescript
interface LoadFenRequest {
  fen: string;
}
```

### LoadFenResponse
```typescript
interface LoadFenResponse {
  success: boolean;
  fen: string;
}
```

### ErrorResponse
```typescript
interface ErrorResponse {
  error: string;
  details?: string;
}
```

### HealthResponse
```typescript
interface HealthResponse {
  status: string;     // "ok" | "error"
  service: string;    // Service name
}
```

---

## 6. Error Handling

### HTTP Status Codes

- `200 OK` - Successful request
- `204 No Content` - Successful delete
- `400 Bad Request` - Invalid input (bad FEN, illegal move)
- `404 Not Found` - Game not found, file not found
- `500 Internal Server Error` - Unexpected server error

### Error Response Format

All errors return JSON with at least an `error` field:

```json
{
  "error": "Human-readable error message",
  "details": "Optional additional context"
}
```

---

## 7. Testing the Contracts

### Using curl

```bash
# Create a new game
curl -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -d '{}'

# Make a move
curl -X POST http://localhost:8080/api/games/<game-id>/moves \
  -H "Content-Type: application/json" \
  -d '{"move": "e4"}'

# Get game state
curl http://localhost:8080/api/games/<game-id>

# Get move history
curl http://localhost:8080/api/games/<game-id>/moves

# Health checks
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health
```

### Contract Testing

Contract tests verify that:
1. **Producer (Game Service)** implements what the **Consumer (API Gateway)** expects
2. **Consumer (Frontend)** sends requests that the **Producer (API Gateway)** can handle

See integration tests in `src/test/scala/chess/microservices/`.

---

## 8. Versioning

Currently, all services are version 1 (implicit, no version in URL).

Future versioning strategy:
- URL-based: `/api/v2/games/*`
- Header-based: `Accept: application/vnd.chess.v2+json`

---

## 9. Security Considerations

**Current implementation (educational):**
- No authentication
- No authorization
- No rate limiting
- No input validation beyond game rules

**Production considerations:**
- Add JWT-based authentication
- Implement rate limiting in Gateway
- Add request validation middleware
- Enable HTTPS/TLS
- Add CORS configuration

---

## 10. Performance Characteristics

**Latency:**
- Gateway adds ~1-5ms overhead (proxy latency)
- Game Service operations: < 10ms (in-memory)
- UI Service file serving: < 5ms (static files)

**Throughput:**
- Game Service: ~1000 req/s (single instance)
- API Gateway: ~5000 req/s (proxy only)
- Bottleneck: Game Service (stateful, single instance)

**Scaling:**
- Game Service: Horizontal scaling requires shared state (Redis/DB)
- UI Service: Stateless, trivially scalable
- API Gateway: Stateless, trivially scalable
