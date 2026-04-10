import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { Chess } from 'chess.js'
import type { GameSettings } from '../../types/api'

const bestContinuationForFen = vi.fn()

class FakeWebSocket {
  onopen: (() => void) | null = null
  onmessage: ((ev: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  readyState = 1

  constructor(_url: string) {
    queueMicrotask(() => this.onopen?.())
  }

  close() {
    this.readyState = 3
    this.onclose?.()
  }

  deliver(data: object) {
    this.onmessage?.({ data: JSON.stringify(data) })
  }
}

vi.stubGlobal('WebSocket', FakeWebSocket as unknown as typeof WebSocket)

vi.mock('../opening', () => ({
  useOpeningStore: () => ({
    bestContinuationForFen,
  }),
}))

vi.mock('../../api/game-api', () => ({
  gameApi: {
    getGameState: vi.fn(),
    makeMove: vi.fn(),
    loadFen: vi.fn(),
    createGame: vi.fn(),
    listGames: vi.fn().mockResolvedValue([]),
    deleteGame: vi.fn(),
    deleteAllGames: vi.fn(),
    aiMove: vi.fn(),
  },
}))

import { useGameStore } from '../game'
import { gameApi } from '../../api/game-api'

function createResponse(
  settings: GameSettings,
  fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'
) {
  return {
    gameId: 'game-1',
    fen,
    settings,
  }
}

function moveResponseFromSan(san: string, fen?: string) {
  const chess = new Chess()
  chess.move(san)
  return {
    fen: fen ?? chess.fen(),
    success: true,
    event: undefined,
  }
}

describe('game store refactor coverage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    vi.clearAllMocks()
    bestContinuationForFen.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('applies backend settings and timed clock when creating a cvC game', async () => {
    vi.mocked(gameApi.createGame).mockResolvedValue(
      createResponse({
        whiteIsHuman: false,
        blackIsHuman: false,
        whiteStrategy: 'material-balance',
        blackStrategy: 'iterative-deepening',
        clockInitialMs: 180000,
        clockIncrementMs: 2000,
      })
    )

    const store = useGameStore()
    await store.createGame()

    expect(store.gameMode).toBe('cvc')
    expect(store.whiteIsHuman).toBe(false)
    expect(store.blackIsHuman).toBe(false)
    expect(store.whiteComputerStrategy).toBe('material-balance')
    expect(store.blackComputerStrategy).toBe('iterative-deepening')
    expect(store.clockMode).toEqual({
      kind: 'timed',
      initialMs: 180000,
      incrementMs: 2000,
      label: '',
    })
  })

  it('chooses the fallback human color when creating an hvC game', async () => {
    vi.mocked(gameApi.createGame).mockResolvedValue(
      createResponse({
        whiteIsHuman: false,
        blackIsHuman: true,
        whiteStrategy: 'greedy',
        blackStrategy: 'opening-continuation',
      })
    )

    const store = useGameStore()
    await store.createGame()

    expect(store.gameMode).toBe('hvc')
    expect(store.myColor).toBe('black')
    expect(store.boardFlipped).toBe(true)
  })

  it('uses backend move errors from nested response payloads', async () => {
    vi.mocked(gameApi.createGame).mockResolvedValue(
      createResponse({
        whiteIsHuman: true,
        blackIsHuman: true,
        whiteStrategy: 'opening-continuation',
        blackStrategy: 'opening-continuation',
      })
    )
    vi.mocked(gameApi.makeMove).mockRejectedValue({
      response: { data: { error: 'Illegal move from backend.' } },
    })

    const store = useGameStore()
    await store.createGame()

    const ok = await store.applyMove('e2', 'e4')

    expect(ok).toBe(false)
    expect(store.error).toBe('Illegal move from backend.')
  })

  it('surfaces timeout errors when joining a game', async () => {
    vi.mocked(gameApi.getGameState).mockRejectedValue({
      code: 'ECONNABORTED',
      message: 'timeout of 1000ms exceeded',
    })

    const store = useGameStore()
    const ok = await store.joinGame('game-1')

    expect(ok).toBe(false)
    expect(store.error).toBe('Connection timed out. Check that the game service is running.')
  })

  it('applies opening-continuation computer moves through the shared move pipeline', async () => {
    vi.mocked(gameApi.createGame).mockResolvedValue(
      createResponse({
        whiteIsHuman: false,
        blackIsHuman: true,
        whiteStrategy: 'opening-continuation',
        blackStrategy: 'opening-continuation',
      })
    )
    bestContinuationForFen.mockResolvedValue({
      san: 'e4',
      remainingPlies: 20,
      eco: 'C20',
      name: 'King Pawn Game',
    })
    vi.mocked(gameApi.makeMove).mockResolvedValue(moveResponseFromSan('e4'))

    const store = useGameStore()
    await store.createGame()

    store.triggerComputerMoveIfNeeded()
    await vi.advanceTimersByTimeAsync(250)

    expect(bestContinuationForFen).toHaveBeenCalled()
    expect(store.pgnMoves).toEqual(['e4'])
    expect(store.boardStates).toHaveLength(2)
  })

  it('applies backend AI moves when the strategy is server-side', async () => {
    vi.mocked(gameApi.createGame).mockResolvedValue(
      createResponse({
        whiteIsHuman: false,
        blackIsHuman: true,
        whiteStrategy: 'material-balance',
        blackStrategy: 'opening-continuation',
      })
    )
    vi.mocked(gameApi.aiMove).mockResolvedValue({ move: 'e4' })
    vi.mocked(gameApi.makeMove).mockResolvedValue(moveResponseFromSan('e4'))

    const store = useGameStore()
    await store.createGame()

    store.triggerComputerMoveIfNeeded()
    await vi.advanceTimersByTimeAsync(250)

    expect(gameApi.aiMove).toHaveBeenCalledWith('game-1', 'material-balance')
    expect(store.pgnMoves).toEqual(['e4'])
  })
})
