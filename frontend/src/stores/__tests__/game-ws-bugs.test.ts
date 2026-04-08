/**
 * Tests for WebSocket-based PGN sync bugs in session mode.
 *
 * ARCHITECTURE
 * ─────────────
 * Previous tests used a single store with manually-crafted WS messages.  That
 * missed the real bug because:
 *   1. Real messages come from TWO stores interacting through a shared backend.
 *   2. The Scala backend always includes the en passant square in FEN (e.g.
 *      "KQkq e3 0 1" after 1.e4).  chess.js v1.x silently strips it when no
 *      capturing pawn exists → "KQkq - 0 1".  Any guard based on exact FEN
 *      string equality therefore always fails.
 *
 * The integration tests below spin up TWO separate Pinia instances (two
 * "browsers"), wire them to a shared MockBroker that generates backend-style
 * FENs and broadcasts WS events to both before returning the HTTP response, then
 * compare each store's pgnText after every move.
 *
 * A pgnText reporter is added temporarily to the store: after every state-
 * changing event (applyMove or WS message) the store pushes its current pgnText
 * into a per-store report array that the test reads.  This mirrors "the UI sends
 * its displayed PGN back over WebSocket after each move" without requiring an
 * actual server.  The reporter is removed once the bug is fixed.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { Chess } from 'chess.js'
import { useGameStore } from '../game'

// ── WebSocket mock ─────────────────────────────────────────────────────────

class FakeWebSocket {
  /** All instances created in the current test. */
  static instances: FakeWebSocket[] = []

  onmessage: ((ev: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  readyState = 1

  /** Messages this client has "sent" back to the server (captured for assertions). */
  readonly sent: string[] = []

  constructor() {
    FakeWebSocket.instances.push(this)
  }

  close() {
    this.readyState = 3
  }

  /** Server → client. */
  deliver(data: object) {
    this.onmessage?.({ data: JSON.stringify(data) })
  }

  /** Client → server (captured for assertions). */
  send(data: string) {
    this.sent.push(data)
  }
}

vi.stubGlobal('WebSocket', FakeWebSocket)

beforeEach(() => {
  FakeWebSocket.instances = []
})

// ── API mock ───────────────────────────────────────────────────────────────

vi.mock('../../api/game-api', () => ({
  gameApi: {
    makeMove: vi.fn(),
    loadFen: vi.fn().mockResolvedValue({}),
    createGame: vi.fn(),
    getGameState: vi.fn(),
    listGames: vi.fn().mockResolvedValue([]),
    deleteGame: vi.fn(),
    deleteAllGames: vi.fn(),
    aiMove: vi.fn(),
  },
}))

import { gameApi } from '../../api/game-api'

// ── MockBroker ─────────────────────────────────────────────────────────────
//
// Simulates the Scala backend + realtime WS hub:
//   • applies moves to an internal Chess instance
//   • generates FENs WITH en passant squares (matching Scala's FullFen.renderEnPassant)
//   • builds PGN with the same format as Scala's PgnFileIO.save
//   • broadcasts the WS event to ALL connected clients SYNCHRONOUSLY before
//     returning the HTTP response — this is the race condition that exists in
//     production (backend publishes to the realtime hub before replying to the
//     original HTTP caller)

class MockBroker {
  private readonly chess = new Chess()
  private readonly moves: string[] = []
  private readonly clients: FakeWebSocket[] = []

  addClient(ws: FakeWebSocket) { this.clients.push(ws) }

  /** Mimics Scala PgnFileIO.save: "1. e4 e5\n2. Nf3 Nc6" */
  private buildPgn(): string {
    const lines: string[] = []
    for (let i = 0; i < this.moves.length; i += 2) {
      const n = Math.floor(i / 2) + 1
      const w = this.moves[i]
      const b = i + 1 < this.moves.length ? ` ${this.moves[i + 1]}` : ''
      lines.push(`${n}. ${w}${b}`)
    }
    return lines.join('\n')
  }

  /**
   * chess.js strips en passant when no capturing pawn is adjacent.
   * Scala's FullFen.renderEnPassant includes it unconditionally after any
   * double pawn push, matching the FEN specification.
   * This function restores it so WS messages match production backend output.
   */
  private backendFen(): string {
    const fen = this.chess.fen()
    const parts = fen.split(' ')
    const hist = this.chess.history({ verbose: true }) as any[]
    const last = hist[hist.length - 1]
    if (last?.piece === 'p') {
      const fromRank = Number.parseInt(last.from[1])
      const toRank   = Number.parseInt(last.to[1])
      if (Math.abs(toRank - fromRank) === 2) {
        const file = last.to[0]
        parts[3] = toRank === 4 ? `${file}3` : `${file}6`
      }
    }
    return parts.join(' ')
  }

  /**
   * Apply a move and broadcast to ALL clients (including the sender) BEFORE
   * returning — simulating "WS arrives before HTTP response" which is the
   * normal production ordering.
   *
   * Returns the HTTP-response payload.
   */
  processMove(san: string): { fen: string; pgn: string } {
    this.chess.move(san)
    this.moves.push(san)
    const fen = this.backendFen()
    const pgn = this.buildPgn()
    const wsEvent = { eventType: 'move_applied', fen, pgn, move: san }
    // Synchronous broadcast: happens while `await gameApi.makeMove()` is still
    // resolving, so the WS handler runs before applyMove's post-await code.
    this.clients.forEach(ws => ws.deliver(wsEvent))
    return { fen, pgn }
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────

function seedHistory(store: ReturnType<typeof useGameStore>, fens: string[], sans: string[]) {
  ;(store as any).boardStates = fens
  ;(store as any).pgnMoves = sans
  ;(store as any).currentIndex = fens.length - 1
}

const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

// ── Sanity: chess.js FEN normalisation ────────────────────────────────────

describe('chess.js strips en passant (root cause of the race-guard failure)', () => {
  it('strips e3 after 1.e4 when no black pawn can capture', () => {
    const backendFen = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1'
    const normalised  = new Chess(backendFen).fen()
    expect(normalised).toBe('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1')
    expect(normalised).not.toBe(backendFen)
  })

  it('strips e6 after 1.e4 e5 too', () => {
    const backendFen = 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2'
    const normalised  = new Chess(backendFen).fen()
    expect(normalised).toBe('rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2')
    expect(normalised).not.toBe(backendFen)
  })
})

// ── Integration tests: two-store shared-broker ─────────────────────────────
//
// storeWhite ↔ broker ↔ storeBlack
// Each store lives in its own Pinia instance (= separate "browser").
// The broker generates backend-style FENs and broadcasts to both before
// returning the HTTP response, replicating the production race condition.
//
// After each move both stores report their pgnText.  The test asserts they
// always agree.

describe('Two-store integration: PGN consistency across moves', () => {
  let broker: MockBroker
  let pinia1: ReturnType<typeof createPinia>
  let pinia2: ReturnType<typeof createPinia>
  let storeWhite: ReturnType<typeof useGameStore>
  let storeBlack: ReturnType<typeof useGameStore>

  /** Reports captured from each store after each state change. */
  const pgnReports: { white: string[]; black: string[] } = { white: [], black: [] }

  beforeEach(() => {
    broker    = new MockBroker()
    pinia1    = createPinia()
    pinia2    = createPinia()
    pgnReports.white = []
    pgnReports.black = []
    FakeWebSocket.instances = []

    // ── White's store (pinia1) ──────────────────────────────────────────
    setActivePinia(pinia1)
    storeWhite = useGameStore()
    ;(storeWhite as any).gameId = 'game-1'
    storeWhite.connectWebSocket('game-1')

    // ── Black's store (pinia2) ──────────────────────────────────────────
    setActivePinia(pinia2)
    storeBlack = useGameStore()
    ;(storeBlack as any).gameId = 'game-1'
    storeBlack.connectWebSocket('game-1')

    // Register BOTH WS connections with the broker
    // FakeWebSocket.instances[0] was created by storeWhite, [1] by storeBlack
    broker.addClient(FakeWebSocket.instances[0])
    broker.addClient(FakeWebSocket.instances[1])

    // Mock makeMove: process in shared broker (broadcasts WS before returning HTTP)
    vi.mocked(gameApi.makeMove).mockImplementation(async (_gameId, san) => {
      const { fen } = broker.processMove(san)
      return { fen, success: true, event: undefined }
    })
  })

  /**
   * Helper: make a move and capture PGN reports from both stores.
   * The "PGN report" simulates "UI sends its displayed PGN over WebSocket
   * after each move" — here we read it directly since both stores are
   * in-process.  Remove this reporting once the bug is fixed.
   */
  async function whiteMove(from: string, to: string) {
    await storeWhite.applyMove(from, to)
    pgnReports.white.push(storeWhite.pgnText)
    pgnReports.black.push(storeBlack.pgnText)
  }

  async function blackMove(from: string, to: string) {
    await storeBlack.applyMove(from, to)
    pgnReports.white.push(storeWhite.pgnText)
    pgnReports.black.push(storeBlack.pgnText)
  }

  it('after 1.e4: both stores show "1. e4"', async () => {
    await whiteMove('e2', 'e4')

    expect(storeWhite.pgnText).toBe('1. e4')
    expect(storeBlack.pgnText).toBe('1. e4')
  })

  it('after 1.e4 e5: both stores show "1. e4 e5"', async () => {
    await whiteMove('e2', 'e4')
    await blackMove('e7', 'e5')

    expect(storeWhite.pgnText).toBe('1. e4 e5')
    expect(storeBlack.pgnText).toBe('1. e4 e5')
    expect(storeWhite.pgnText).toBe(storeBlack.pgnText)
  })

  it(String.raw`after 1.e4 e5 2.Nf3: both stores show "1. e4 e5\n2. Nf3"`, async () => {
    await whiteMove('e2', 'e4')
    await blackMove('e7', 'e5')
    await whiteMove('g1', 'f3')

    expect(storeWhite.pgnText).toBe('1. e4 e5\n2. Nf3')
    expect(storeBlack.pgnText).toBe('1. e4 e5\n2. Nf3')
    expect(storeWhite.pgnText).toBe(storeBlack.pgnText)
  })

  it('after 1.e4 e5 2.Nf3 Nc6: both stores agree', async () => {
    await whiteMove('e2', 'e4')
    await blackMove('e7', 'e5')
    await whiteMove('g1', 'f3')
    await blackMove('b8', 'c6')

    expect(storeWhite.pgnText).toBe(storeBlack.pgnText)
    expect(storeWhite.pgnMoves).toEqual(['e4', 'e5', 'Nf3', 'Nc6'])
    expect(storeBlack.pgnMoves).toEqual(['e4', 'e5', 'Nf3', 'Nc6'])
  })

  it('boardStates.length === pgnMoves.length + 1 for both stores after 4 moves', async () => {
    await whiteMove('e2', 'e4')
    await blackMove('e7', 'e5')
    await whiteMove('g1', 'f3')
    await blackMove('b8', 'c6')

    expect(storeWhite.boardStates.length).toBe(storeWhite.pgnMoves.length + 1)
    expect(storeBlack.boardStates.length).toBe(storeBlack.pgnMoves.length + 1)
  })

  it('PGN reports from both stores are identical at every step', async () => {
    await whiteMove('e2', 'e4')
    await blackMove('e7', 'e5')
    await whiteMove('g1', 'f3')
    await blackMove('b8', 'c6')

    // Every report captured by white and black at the same step must match
    for (let i = 0; i < pgnReports.white.length; i++) {
      expect(pgnReports.white[i]).toBe(pgnReports.black[i])
    }
  })
})

// ── Existing unit-level regression tests ──────────────────────────────────

describe('WebSocket handler — PGN desync (Bug 1)', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  const BACKEND_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1'
  const BACKEND_E5 = 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2'

  it('sync after valid-PGN opponent move', () => {
    const store = useGameStore()
    store.connectWebSocket('x')
    latestWebSocket().deliver({ eventType: 'move_applied', fen: BACKEND_E4, pgn: '1. e4' })
    expect(store.pgnMoves.length).toBe(1)
    expect(store.boardStates.length).toBe(2)
  })

  it('sync after two valid-PGN moves', () => {
    const store = useGameStore()
    store.connectWebSocket('x')
    latestWebSocket().deliver({ eventType: 'move_applied', fen: BACKEND_E4, pgn: '1. e4' })
    latestWebSocket().deliver({ eventType: 'move_applied', fen: BACKEND_E5, pgn: '1. e4 e5' })
    expect(store.pgnMoves.length).toBe(2)
    expect(store.boardStates.length).toBe(3)
  })

  it('sync when pgn is empty (else-branch uses move field)', () => {
    const store = useGameStore()
    store.connectWebSocket('x')
    latestWebSocket().deliver({ eventType: 'move_applied', fen: BACKEND_E4, pgn: '', move: 'e4' })
    expect(store.boardStates.length).toBe(2)
    expect(store.pgnMoves.length).toBe(1)
  })
})

describe('WebSocket handler — extra redo step (Bug 2)', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  const BACKEND_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1'
  const CHESSJS_E4 = new Chess(BACKEND_E4).fen()
  const CHESSJS_E5 = (() => { const c = new Chess(BACKEND_E4); c.move('e5'); return c.fen() })()

  it('canRedo is false after opponent WS move', async () => {
    const store = useGameStore()
    seedHistory(store, [INITIAL_FEN, CHESSJS_E4], ['e4'])
    await store.undo()
    expect(store.canRedo).toBe(true)

    store.connectWebSocket('x')
    FakeWebSocket.instances[0].deliver({ eventType: 'move_applied', fen: BACKEND_E4, pgn: '1. e4' })
    expect(store.canRedo).toBe(false)
  })

  it('redo never exceeds move count', async () => {
    const store = useGameStore()
    seedHistory(store, [INITIAL_FEN, CHESSJS_E4, CHESSJS_E5], ['e4', 'e5'])
    await store.undo()
    await store.undo()
    expect(store.canRedo).toBe(true)

    store.connectWebSocket('x')
    FakeWebSocket.instances[0].deliver({ eventType: 'move_applied', fen: BACKEND_E4, pgn: '1. e4' })
    expect(store.canRedo).toBe(false)
  })
})

function latestWebSocket() {
  return FakeWebSocket.instances[FakeWebSocket.instances.length - 1]
}
