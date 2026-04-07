import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useGameStore } from '../game'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []

  onopen: (() => void) | null = null
  onmessage: ((ev: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  readyState = 1
  url: string

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
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

vi.stubGlobal('WebSocket', FakeWebSocket as any)

vi.mock('../../api/game-api', () => ({
  gameApi: {
    getGameState: vi.fn(),
    makeMove: vi.fn(),
    loadFen: vi.fn(),
    createGame: vi.fn(),
    listGames: vi.fn(),
    deleteGame: vi.fn(),
    deleteAllGames: vi.fn(),
    aiMove: vi.fn(),
  },
}))

import { gameApi } from '../../api/game-api'

describe('session websocket reconnect', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    FakeWebSocket.instances = []
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('ignores heartbeat messages', () => {
    const store = useGameStore()
    ;(store as any).gameId = 'game-1'
    store.connectWebSocket('game-1')

    FakeWebSocket.instances[0].deliver({ eventType: 'heartbeat' })

    expect(store.pgnMoves).toEqual([])
    expect(store.boardStates.length).toBe(1)
  })

  it('reconnects and resyncs state after websocket close', async () => {
    const store = useGameStore()
    ;(store as any).gameId = 'game-1'
    store.whiteIsHuman = true
    store.blackIsHuman = true

    vi.mocked(gameApi.getGameState).mockResolvedValue({
      gameId: 'game-1',
      fen: 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2',
      pgn: '1. e4 e5',
      status: 'White to move',
      settings: {
        whiteIsHuman: true,
        blackIsHuman: true,
        whiteStrategy: 'opening-continuation',
        blackStrategy: 'opening-continuation',
      },
    })

    store.connectWebSocket('game-1')
    expect(FakeWebSocket.instances.length).toBe(1)

    FakeWebSocket.instances[0].close()
    await vi.advanceTimersByTimeAsync(1000)
    await Promise.resolve()

    expect(vi.mocked(gameApi.getGameState)).toHaveBeenCalledWith('game-1')
    expect(FakeWebSocket.instances.length).toBe(2)
    expect(store.pgnText).toBe('1. e4 e5')
    expect(store.boardStates.length).toBe(3)
  })
})
