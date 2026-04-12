import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Chess } from 'chess.js'
import type { Move, PieceSymbol, Square } from 'chess.js'
import type { GameStatus, Turn } from '../types/game'
import type { GameSettings } from '../types/api'
import { useOpeningStore } from './opening'
import { gameApi } from '../api/game-api'

function formatClockTime(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// ── Clock mode types ─────────────────────────────────────────────────
export interface ClockModeTimed { kind: 'timed'; initialMs: number; incrementMs: number; label: string }
export interface ClockModeNone { kind: 'none' }
export type ClockMode = ClockModeTimed | ClockModeNone
type VerboseMove = Move
type ChessSquare = Square
type PromotionRole = Exclude<PieceSymbol, 'k' | 'p'>
type GameStateResponse = Awaited<ReturnType<typeof gameApi.getGameState>>
type MoveAppliedMessage = {
  eventType?: string
  fen?: string
  move?: string
  pgn?: string
}

export const CLOCK_PRESETS: { label: string; mode: ClockMode }[] = [
  { label: 'No Limit', mode: { kind: 'none' } },
  { label: 'Bullet 1+0', mode: { kind: 'timed', initialMs: 1 * 60 * 1000, incrementMs: 0, label: 'Bullet 1+0' } },
  { label: 'Bullet 2+1', mode: { kind: 'timed', initialMs: 2 * 60 * 1000, incrementMs: 1000, label: 'Bullet 2+1' } },
  { label: 'Blitz 3+0', mode: { kind: 'timed', initialMs: 3 * 60 * 1000, incrementMs: 0, label: 'Blitz 3+0' } },
  { label: 'Blitz 3+2', mode: { kind: 'timed', initialMs: 3 * 60 * 1000, incrementMs: 2000, label: 'Blitz 3+2' } },
  { label: 'Blitz 5+0', mode: { kind: 'timed', initialMs: 5 * 60 * 1000, incrementMs: 0, label: 'Blitz 5+0' } },
  { label: 'Blitz 5+3', mode: { kind: 'timed', initialMs: 5 * 60 * 1000, incrementMs: 3000, label: 'Blitz 5+3' } },
  { label: 'Rapid 10+0', mode: { kind: 'timed', initialMs: 10 * 60 * 1000, incrementMs: 0, label: 'Rapid 10+0' } },
  { label: 'Rapid 15+10', mode: { kind: 'timed', initialMs: 15 * 60 * 1000, incrementMs: 10000, label: 'Rapid 15+10' } },
  { label: 'Classical 30+0', mode: { kind: 'timed', initialMs: 30 * 60 * 1000, incrementMs: 0, label: 'Classical 30+0' } },
]

export type GameMode = 'hvh' | 'hvc' | 'cvc'
export type ComputerSide = 'white' | 'black'
export type PlayerColor = 'white' | 'black' | 'spectator'
export type ComputerStrategyId =
  | 'deepening-opening-endgame'
  | 'random'
  | 'greedy'
  | 'material-balance'
  | 'piece-square'
  | 'minimax'
  | 'endgame-minimax'
  | 'quiescence'
  | 'iterative-deepening'
  | 'opening-continuation'

export const COMPUTER_STRATEGIES: { id: ComputerStrategyId; label: string }[] = [
  { id: 'deepening-opening-endgame', label: 'Deepening + Opening + Endgame' },
  { id: 'opening-continuation', label: 'Opening Continuation' },
  { id: 'random', label: 'Random' },
  { id: 'greedy', label: 'Greedy' },
  { id: 'material-balance', label: 'Material Balance' },
  { id: 'piece-square', label: 'Piece-Square Tables' },
  { id: 'minimax', label: 'Minimax (d=3)' },
  { id: 'endgame-minimax', label: 'Endgame Minimax (d=3)' },
  { id: 'quiescence', label: 'Minimax+QSearch (d=3)' },
  { id: 'iterative-deepening', label: 'Iterative Deepening' },
]

const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

function deriveGameMode(whiteIsHuman: boolean, blackIsHuman: boolean): GameMode {
  if (whiteIsHuman && blackIsHuman) return 'hvh'
  if (!whiteIsHuman && !blackIsHuman) return 'cvc'
  return 'hvc'
}

function defaultStrategy(strategy?: string | null): ComputerStrategyId {
  return (strategy as ComputerStrategyId) || 'deepening-opening-endgame'
}

function clockModeFromSettings(settings?: GameSettings): ClockMode {
  if (!settings?.clockInitialMs) return { kind: 'none' }
  return {
    kind: 'timed',
    initialMs: settings.clockInitialMs,
    incrementMs: settings.clockIncrementMs ?? 0,
    label: '',
  }
}

function replayStatesFromMoves(parsedMoves: string[]): string[] {
  const states: string[] = [INITIAL_FEN]
  const replay = new Chess()
  for (const san of parsedMoves) {
    try {
      replay.move(san)
      states.push(replay.fen())
    } catch {
      break
    }
  }
  return states
}

function parsePgnMovesSafe(pgn: string): string[] {
  if (!pgn.trim()) return []
  try {
    const tempChess = new Chess()
    tempChess.loadPgn(pgn)
    return tempChess.history()
  } catch {
    return []
  }
}

function fenSignature(fen: string): string {
  return fen.split(' ').slice(0, 2).join(' ')
}

function isTimeoutError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const candidate = err as { code?: string; message?: string }
  return candidate.code === 'ECONNABORTED' || candidate.message?.includes('timeout') === true
}

function errorStatus(err: unknown): number | undefined {
  if (!err || typeof err !== 'object') return undefined
  return (err as { response?: { status?: number } }).response?.status
}

function backendMoveErrorMessage(err: unknown): string {
  if (!err || typeof err !== 'object') return 'Move rejected by backend.'
  return (
    (err as { response?: { data?: { error?: string } } }).response?.data?.error ||
    'Move rejected by backend.'
  )
}

export const useGameStore = defineStore('game', () => {
  // ── Core chess engine (always the "latest" position) ─────────────────
  const chess = ref(new Chess())

  // ── Move history navigation (mirrors GUI's _boardStates / _currentIndex) ──
  const boardStates = ref<string[]>([chess.value.fen()])
  const pgnMoves = ref<string[]>([])
  const currentIndex = ref(0)

  // ── UI state ─────────────────────────────────────────────────────────
  const gameId = ref<string | null>(null)
  const selectedSquare = ref<string | null>(null)
  const legalMoves = ref<string[]>([])
  const lastMove = ref<{ from: string; to: string } | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const status = ref<GameStatus>('in_progress')
  const pendingPromotion = ref<{ from: string; to: string } | null>(null)
  const showLegalMoves = ref(true)
  const boardFlipped = ref(false)

  // ── Session / multiplayer ────────────────────────────────────────────
  // Which side the local player controls in the current session
  const myColor = ref<PlayerColor>('white')

  // Whether each side is human (tracks GameSettings)
  const whiteIsHuman = ref(true)
  const blackIsHuman = ref(true)

  // WebSocket for opponent move updates
  let wsConnection: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempts = 0
  let intentionalWsClose = false

  // Generation counter — incremented on every new game; lets stale setTimeout callbacks self-cancel
  let gameGeneration = 0

  // Redo buffer for undo/redo support
  const redoStates = ref<string[]>([])
  const redoMoves = ref<string[]>([])

  // ── Game mode ────────────────────────────────────────────────────────
  const gameMode = ref<GameMode>('hvh')
  const paused = ref(false)
  const computerScheduled = ref(false)
  // Guard against concurrent applyMove calls (e.g. double-click while HTTP is in-flight)
  const moveInFlight = ref(false)
  const whiteComputerStrategy = ref<ComputerStrategyId>('deepening-opening-endgame')
  const blackComputerStrategy = ref<ComputerStrategyId>('deepening-opening-endgame')

  // ── Puzzle mode ──────────────────────────────────────────────────────
  const puzzleMode = ref(false)
  const puzzlePendingMove = ref<{ from: string; to: string; promotion?: string } | null>(null)

  // ── Chess clock ──────────────────────────────────────────────────────
  const clockMode = ref<ClockMode>({ kind: 'none' })
  const whiteTimeMs = ref(0)   // elapsed (no-limit) or remaining (timed)
  const blackTimeMs = ref(0)
  const clockStarted = ref(false)
  const gameOverByTimeout = ref(false)
  let clockInterval: ReturnType<typeof setInterval> | null = null
  let lastTickTime = 0

  // ── Computed ─────────────────────────────────────────────────────────
  const viewChess = computed(() => new Chess(boardStates.value[currentIndex.value]))
  const fen = computed(() => boardStates.value[currentIndex.value])
  const turn = computed((): Turn => viewChess.value.turn())
  const turnColor = computed(() => viewChess.value.turn() === 'w' ? 'white' : 'black')
  const isCheck = computed(() => viewChess.value.isCheck())
  const isCheckmate = computed(() => viewChess.value.isCheckmate())
  const isStalemate = computed(() => viewChess.value.isStalemate())
  const isDraw = computed(() => viewChess.value.isDraw())
  const isGameOver = computed(() => viewChess.value.isGameOver() || gameOverByTimeout.value)
  const isAtLatest = computed(() => currentIndex.value === boardStates.value.length - 1)
  const canUndo = computed(() => boardStates.value.length > 1)
  const canRedo = computed(() => redoStates.value.length > 0)

  const latestTurn = computed((): Turn => chess.value.turn())

  const statusText = computed(() => {
    if (gameOverByTimeout.value) {
      return latestTurn.value === 'w' ? 'Time out! Black wins.' : 'Time out! White wins.'
    }
    if (isCheckmate.value) {
      return viewChess.value.turn() === 'w' ? 'Checkmate! Black wins.' : 'Checkmate! White wins.'
    }
    if (isStalemate.value) return 'Stalemate! Draw.'
    if (isDraw.value) return 'Draw!'
    if (isCheck.value) {
      return `${turnColor.value === 'white' ? 'White' : 'Black'} is in check!`
    }
    if (paused.value) return 'Paused'
    return turnColor.value === 'white' ? 'White to move' : 'Black to move'
  })

  // ── Captured pieces & material balance ───────────────────────────────
  const startingCounts: Record<string, number> = { p: 8, n: 2, b: 2, r: 2, q: 1 }
  const pieceValues: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9 }
  const pieceSymbols: Record<string, string> = {
    wk: '♔', wq: '♕', wr: '♖', wb: '♗', wn: '♘', wp: '♙',
    bk: '♚', bq: '♛', br: '♜', bb: '♝', bn: '♞', bp: '♟',
  }

  const capturedPieces = computed(() => {
    const board = viewChess.value.board()
    const live: Record<string, number> = {}
    for (const row of board) {
      for (const sq of row) {
        if (sq) {
          const key = `${sq.color}${sq.type}`
          live[key] = (live[key] || 0) + 1
        }
      }
    }
    function getCaptured(color: 'w' | 'b'): string[] {
      const symbols: string[] = []
      for (const role of ['q', 'r', 'b', 'n', 'p']) {
        const key = `${color}${role}`
        const count = (startingCounts[role] || 0) - (live[key] || 0)
        const sym = pieceSymbols[key] || ''
        for (let i = 0; i < Math.max(0, count); i++) symbols.push(sym)
      }
      return symbols
    }
    function getMaterial(color: 'w' | 'b'): number {
      let total = 0
      for (const role of ['q', 'r', 'b', 'n', 'p']) {
        const captured = (startingCounts[role] || 0) - (live[`${color}${role}`] || 0)
        total += Math.max(0, captured) * (pieceValues[role] || 0)
      }
      return total
    }
    const whiteCaptured = getMaterial('b')
    const blackCaptured = getMaterial('w')
    const advantage = whiteCaptured - blackCaptured
    let advantageText = '='
    if (advantage > 0) advantageText = `White +${advantage}`
    else if (advantage < 0) advantageText = `Black +${-advantage}`
    return {
      byBlack: getCaptured('w'),
      byWhite: getCaptured('b'),
      advantageText,
      advantage,
    }
  })

  // ── King square for check/checkmate highlighting ─────────────────────
  const kingSquare = computed((): string | null => {
    if (!isCheck.value && !isCheckmate.value) return null
    const board = viewChess.value.board()
    const kingColor = viewChess.value.turn()
    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        const sq = board[r][c]
        if (sq?.type === 'k' && sq.color === kingColor) {
          const file = String.fromCodePoint(('a'.codePointAt(0) ?? 97) + c)
          const rank = String(8 - r)
          return `${file}${rank}`
        }
      }
    }
    return null
  })

  const whiteClockDisplay = computed(() => {
    return formatClockTime(whiteTimeMs.value)
  })
  const blackClockDisplay = computed(() => {
    return formatClockTime(blackTimeMs.value)
  })

  function startClock() {
    if (clockInterval) return
    lastTickTime = Date.now()
    clockInterval = setInterval(() => {
      if (paused.value || isGameOver.value) return
      const now = Date.now()
      const delta = now - lastTickTime
      lastTickTime = now

      updateActiveClock(delta)
    }, 100)
  }

  function updateActiveClock(delta: number) {
    if (latestTurn.value === 'w') {
      updateClockForSide('w', delta)
      return
    }
    updateClockForSide('b', delta)
  }

  function updateClockForSide(color: Turn, delta: number) {
    if (clockMode.value.kind === 'timed') {
      if (color === 'w') {
        whiteTimeMs.value = Math.max(0, whiteTimeMs.value - delta)
        if (whiteTimeMs.value <= 0) handleTimeout('w')
        return
      }
      blackTimeMs.value = Math.max(0, blackTimeMs.value - delta)
      if (blackTimeMs.value <= 0) handleTimeout('b')
      return
    }
    if (color === 'w') whiteTimeMs.value += delta
    else blackTimeMs.value += delta
  }

  function stopClock() {
    if (clockInterval) { clearInterval(clockInterval); clockInterval = null }
  }

  function resetClock() {
    stopClock()
    clockStarted.value = false
    if (clockMode.value.kind === 'timed') {
      whiteTimeMs.value = clockMode.value.initialMs
      blackTimeMs.value = clockMode.value.initialMs
    } else {
      whiteTimeMs.value = 0
      blackTimeMs.value = 0
    }
  }

  function switchClock() {
    if (!clockStarted.value) {
      clockStarted.value = true
      startClock()
    }
    // Apply increment to the side that just moved
    if (clockMode.value.kind === 'timed') {
      const justMoved = latestTurn.value === 'w' ? 'b' : 'w'  // turn already switched
      if (justMoved === 'w') whiteTimeMs.value += clockMode.value.incrementMs
      else blackTimeMs.value += clockMode.value.incrementMs
    }
    lastTickTime = Date.now()
    if (isGameOver.value) stopClock()
  }

  function handleTimeout(_color: Turn) {
    gameOverByTimeout.value = true
    stopClock()
    gameMode.value = 'hvh'
    paused.value = false
  }

  function setClockMode(mode: ClockMode) {
    clockMode.value = mode
    resetClock()
  }

  // ── Computer player ───────────────────────────────────────────────────
  function isComputerTurn(): boolean {
    if (gameMode.value === 'hvh') return false
    if (gameMode.value === 'cvc') return true
    // hvc: one side is computer — use whiteIsHuman/blackIsHuman to know which
    return latestTurn.value === 'w' ? !whiteIsHuman.value : !blackIsHuman.value
  }

  // ── WebSocket (receive opponent moves in HvH sessions) ───────────────
  function isMyTurn(): boolean {
    if (myColor.value === 'spectator') return false
    return myColor.value === 'white' ? latestTurn.value === 'w' : latestTurn.value === 'b'
  }

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function applySettingsToStore(settings?: GameSettings) {
    whiteIsHuman.value = settings?.whiteIsHuman ?? true
    blackIsHuman.value = settings?.blackIsHuman ?? true
    gameMode.value = deriveGameMode(whiteIsHuman.value, blackIsHuman.value)
    if (!settings) return
    whiteComputerStrategy.value = defaultStrategy(settings.whiteStrategy)
    blackComputerStrategy.value = defaultStrategy(settings.blackStrategy)
    clockMode.value = clockModeFromSettings(settings)
  }

  function syncFromGameState(
    response: GameStateResponse,
    options: { resetClockState?: boolean } = {}
  ) {
    chess.value = new Chess(response.fen)
    applySettingsToStore(response.settings)

    const parsedMoves = parsePgnMovesSafe(response.pgn)
    const states = replayStatesFromMoves(parsedMoves)

    boardStates.value = states
    pgnMoves.value = parsedMoves
    currentIndex.value = states.length - 1
    selectedSquare.value = null
    legalMoves.value = []
    lastMove.value = null
    pendingPromotion.value = null
    redoStates.value = []
    redoMoves.value = []
    moveInFlight.value = false
    if (options.resetClockState ?? false) resetClock()
    syncStatusFromChess()
    updateLastMoveFromIndex()
  }

  async function reconnectSession(id: string, generation: number) {
    if (generation !== gameGeneration || gameId.value !== id || intentionalWsClose) return
    try {
      const response = await gameApi.getGameState(id)
      if (generation !== gameGeneration || gameId.value !== id || intentionalWsClose) return
      syncFromGameState(response)
      connectWebSocket(id)
    } catch {
      scheduleReconnect(id, generation)
    }
  }

  function scheduleReconnect(id: string, generation: number) {
    if (intentionalWsClose || generation !== gameGeneration || gameId.value !== id) return
    if (!(whiteIsHuman.value && blackIsHuman.value)) return
    clearReconnectTimer()
    const delay = Math.min(1000 * (2 ** reconnectAttempts), 10000)
    reconnectAttempts += 1
    reconnectTimer = setTimeout(() => {
      void reconnectSession(id, generation)
    }, delay)
  }

  function connectWebSocket(id: string) {
    disconnectWebSocket()
    intentionalWsClose = false
    clearReconnectTimer()
    const generation = gameGeneration
    const wsBase = import.meta.env.VITE_REALTIME_WS_URL ?? 'ws://localhost:8083'
    const ws = new WebSocket(`${wsBase}/ws/${id}`)
    ws.onopen = () => {
      reconnectAttempts = 0
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data as string) as MoveAppliedMessage
        if (msg.eventType === 'heartbeat' || msg.eventType !== 'move_applied') return
        applyRemoteMove(msg)
      } catch { /* ignore malformed messages */ }
    }
    ws.onerror = () => { /* silent — server may not be running */ }
    ws.onclose = () => {
      if (!intentionalWsClose && generation === gameGeneration && gameId.value === id) {
        scheduleReconnect(id, generation)
      }
    }
    wsConnection = ws
  }

  function disconnectWebSocket() {
    intentionalWsClose = true
    clearReconnectTimer()
    if (wsConnection) { wsConnection.close(); wsConnection = null }
  }

  // Keep stopPolling as an alias so App.vue's onUnmounted call still works
  function stopPolling() { disconnectWebSocket() }

  function currentComputerStrategy(): ComputerStrategyId {
    return latestTurn.value === 'w' ? whiteComputerStrategy.value : blackComputerStrategy.value
  }

  function applyRemoteMove(msg: MoveAppliedMessage) {
    const newFen = msg.fen
    console.log('[WS] move_applied received', { move: msg.move, pgn: msg.pgn, fen: newFen, currentPgnLen: pgnMoves.value.length, moveInFlight: moveInFlight.value })
    if (!newFen || fenSignature(newFen) === fenSignature(chess.value.fen())) {
      console.log('[WS] skipped: fenSig match')
      return
    }

    const parsedMoves = parsePgnMovesSafe(msg.pgn ?? '')
    if (parsedMoves.length > 0 && parsedMoves.length < pgnMoves.value.length) {
      console.log('[WS] skipped: stale event', parsedMoves.length, '<', pgnMoves.value.length)
      return
    }

    console.log('[WS] APPLYING move', { parsedMoves, gameMode: gameMode.value })
    chess.value = new Chess(newFen)
    if (parsedMoves.length > 0) {
      boardStates.value = replayStatesFromMoves(parsedMoves)
      pgnMoves.value = parsedMoves
    } else {
      boardStates.value = [...boardStates.value, newFen]
      if (msg.move) pgnMoves.value = [...pgnMoves.value, msg.move]
    }
    redoStates.value = []
    redoMoves.value = []
    currentIndex.value = boardStates.value.length - 1
    selectedSquare.value = null
    legalMoves.value = []
    updateLastMoveFromIndex()
    syncStatusFromChess()
    switchClock()
    triggerComputerMoveIfNeeded()
  }

  function setComputerStrategy(side: ComputerSide, strategy: ComputerStrategyId) {
    if (side === 'white') whiteComputerStrategy.value = strategy
    else blackComputerStrategy.value = strategy
    triggerComputerMoveIfNeeded()
  }

  function chooseGreedyMove(moves: VerboseMove[]): VerboseMove {
    const captures = moves.filter((m) => m.captured)
    if (captures.length > 0) {
      const vals: Record<string, number> = { q: 9, r: 5, b: 3, n: 3, p: 1 }
      captures.sort((a, b) => (vals[b.captured ?? ''] || 0) - (vals[a.captured ?? ''] || 0))
      return captures[0]
    }
    return moves[Math.floor(Math.random() * moves.length)]
  }

  async function chooseOpeningContinuationMove(): Promise<VerboseMove | null> {
    const openingStore = useOpeningStore()
    const candidate = await openingStore.bestContinuationForFen(chess.value.fen())
    if (!candidate) return null

    try {
      const clone = new Chess(chess.value.fen())
      const result = clone.move(candidate.san)
      return result || null
    } catch {
      return null
    }
  }

  // Strategies handled entirely in the browser (no backend round-trip needed)
  const CLIENT_SIDE_STRATEGIES = new Set<ComputerStrategyId>(['opening-continuation', 'random', 'greedy'])

  async function chooseBackendMove(strategy: ComputerStrategyId): Promise<string | null> {
    if (!gameId.value) return null
    try {
      const response = await gameApi.aiMove(gameId.value, strategy)
      return response.move ?? null
    } catch {
      return null
    }
  }

  async function applyBackendComputerMove(strategy: ComputerStrategyId): Promise<boolean> {
    const san = await chooseBackendMove(strategy)
    if (!san) return false
    const parsed = new Chess(chess.value.fen()).move(san)
    if (!parsed) return false
    return applyMove(parsed.from, parsed.to, parsed.promotion)
  }

  async function chooseClientSideMove(
    strategy: ComputerStrategyId,
    moves: VerboseMove[]
  ): Promise<VerboseMove> {
    if (strategy === 'opening-continuation') {
      const openingMove = await chooseOpeningContinuationMove()
      if (openingMove) return openingMove
    }
    if (strategy === 'random') return moves[Math.floor(Math.random() * moves.length)]
    return chooseGreedyMove(moves)
  }

  function makeComputerMove() {
    if (computerScheduled.value || paused.value || isGameOver.value) return
    if (!isComputerTurn()) return
    if (!isAtLatest.value) return

    computerScheduled.value = true
    const delay = gameMode.value === 'cvc' ? 400 : 250
    const gen = gameGeneration   // capture so the callback can detect a new game

    setTimeout(async () => {
      computerScheduled.value = false
      if (gen !== gameGeneration) return   // new game was created — discard stale callback
      if (paused.value || isGameOver.value || !isComputerTurn() || !isAtLatest.value) return

      const strategy = currentComputerStrategy()
      const moves = chess.value.moves({ verbose: true }) as VerboseMove[]
      if (moves.length === 0) return

      if (!CLIENT_SIDE_STRATEGIES.has(strategy)) {
        if (await applyBackendComputerMove(strategy)) triggerComputerMoveIfNeeded()
        return
      }

      const chosen = await chooseClientSideMove(strategy, moves)

      const promo = chosen.promotion || undefined
      const ok = await applyMove(chosen.from, chosen.to, promo)
      if (ok) triggerComputerMoveIfNeeded()
    }, delay)
  }

  function triggerComputerMoveIfNeeded() {
    console.log('[trigger] isComputerTurn=', isComputerTurn(), 'gameMode=', gameMode.value, 'whiteIsHuman=', whiteIsHuman.value, 'blackIsHuman=', blackIsHuman.value, 'latestTurn=', latestTurn.value)
    if (!isGameOver.value && !paused.value && isComputerTurn() && isAtLatest.value) {
      console.log('[trigger] TRIGGERING computer move')
      makeComputerMove()
    }
  }

  // ── Actions ──────────────────────────────────────────────────────────
  async function createGame(
    startFen?: string,
    settings?: GameSettings,
    playAs?: PlayerColor,
    options?: { skipComputerKickoff?: boolean }
  ) {
    loading.value = true
    error.value = null
    paused.value = false
    gameGeneration++                  // invalidate all stale computer-move callbacks
    computerScheduled.value = false
    moveInFlight.value = false
    disconnectWebSocket()
    try {
      const createPayload =
        startFen || settings
          ? { startFen, settings }
          : {
              settings: {
                whiteIsHuman: true,
                blackIsHuman: true,
                whiteStrategy: 'deepening-opening-endgame',
                blackStrategy: 'deepening-opening-endgame',
              },
            }
      const response = await gameApi.createGame(createPayload)
      chess.value = new Chess(response.fen)
      gameId.value = response.gameId
      gameOverByTimeout.value = false

      // Derive effective settings: prefer backend response, fall back to what was requested,
      // then defaults.  This ensures gameMode is always updated even if the backend omits settings.
      const s = response.settings ?? settings
      const effectiveWhiteHuman = s?.whiteIsHuman ?? true
      const effectiveBlackHuman = s?.blackIsHuman ?? true
      whiteIsHuman.value = effectiveWhiteHuman
      blackIsHuman.value = effectiveBlackHuman
      gameMode.value = deriveGameMode(effectiveWhiteHuman, effectiveBlackHuman)
      if (s) applySettingsToStore(s)

      // Determine which side the local player controls.
      // For a local HvH game the creator plays both sides freely (spectator = no restriction).
      // The board is still flipped for convenience if they chose Black in the dialog.
      // Move restrictions only apply to the remote joiner (set in joinGame).
      if (!effectiveWhiteHuman || !effectiveBlackHuman) {
        // Has a computer side: the human player needs a specific color
        if (playAs) myColor.value = playAs
        else if (effectiveWhiteHuman) myColor.value = 'white'
        else if (effectiveBlackHuman) myColor.value = 'black'
        else myColor.value = 'spectator'
        boardFlipped.value = myColor.value === 'black'
      } else {
        // Local HvH: no move restriction; flip board to match the chosen starting side
        myColor.value = 'spectator'
        boardFlipped.value = playAs === 'black'
      }

      resetHistory()
      resetClock()
      syncStatusFromChess()
      if (!options?.skipComputerKickoff) triggerComputerMoveIfNeeded()
      // Connect WebSocket for HvH sessions to receive opponent moves
      if (response.gameId && whiteIsHuman.value && blackIsHuman.value) {
        connectWebSocket(response.gameId)
      }
    } catch {
      error.value = 'Could not create game via backend.'
    } finally {
      loading.value = false
    }
  }

  async function joinGame(sessionId: string): Promise<boolean> {
    loading.value = true
    error.value = null
    paused.value = false
    gameGeneration++
    computerScheduled.value = false
    disconnectWebSocket()
    try {
      const response = await gameApi.getGameState(sessionId)
      gameId.value = response.gameId
      gameOverByTimeout.value = false

      const s = response.settings
      const effectiveWhiteHuman = s?.whiteIsHuman ?? true
      const effectiveBlackHuman = s?.blackIsHuman ?? true
      const joinIsHvH = effectiveWhiteHuman && effectiveBlackHuman

      // Joiner plays as Black in a two-human game; otherwise spectator for CvC
      if (joinIsHvH) {
        myColor.value = 'black'
      } else {
        myColor.value = 'spectator'
      }
      boardFlipped.value = myColor.value === 'black'
      syncFromGameState(response, { resetClockState: true })
      triggerComputerMoveIfNeeded()
      connectWebSocket(sessionId)
      return true
    } catch (err: unknown) {
      if (errorStatus(err) === 404) {
        error.value = 'Game not found. The session may have expired (server was restarted).'
      } else if (isTimeoutError(err)) {
        error.value = 'Connection timed out. Check that the game service is running.'
      } else {
        error.value = 'Could not join game. Check that all services are running.'
      }
      return false
    } finally {
      loading.value = false
    }
  }

  function resetHistory() {
    boardStates.value = [chess.value.fen()]
    pgnMoves.value = []
    currentIndex.value = 0
    selectedSquare.value = null
    legalMoves.value = []
    lastMove.value = null
    error.value = null
    pendingPromotion.value = null
    redoStates.value = []
    redoMoves.value = []
  }

  async function applyMove(from: string, to: string, promotion?: string): Promise<boolean> {
    console.log(
      '[applyMove] CALLED',
      from,
      '->',
      to,
      'moveInFlight=',
      moveInFlight.value,
      new Error('applyMove trace').stack?.split('\n').slice(1, 4).join(' | ')
    )
    // Prevent a second move being sent while one HTTP round-trip is already in flight.
    // Without this guard a double-click (or any concurrent path) sends two requests to
    // the backend: the first applies the intended move and flips the turn; the second
    // then resolves the same SAN notation for the *other* side (e.g. "d5" becomes the
    // white e4×d5 capture), causing an unintended auto-move.
    if (moveInFlight.value) return false
    if (!isAtLatest.value) {
      error.value = 'Navigate to latest position before making a move.'
      return false
    }
    error.value = null
    if (!gameId.value) {
      error.value = 'No active backend game.'
      return false
    }

    const probe = new Chess(chess.value.fen())
    const moveObj: { from: ChessSquare; to: ChessSquare; promotion?: PromotionRole } = {
      from: from as ChessSquare,
      to: to as ChessSquare,
    }
    if (promotion) moveObj.promotion = promotion as PromotionRole

    const result = probe.move(moveObj)
    if (!result) {
      error.value = 'Invalid move'
      return false
    }

    moveInFlight.value = true
    try {
      const lenBefore = boardStates.value.length
      const response = await gameApi.makeMove(gameId.value, result.san)
      // The WS echo may have already applied this move while we were awaiting.
      // Use boardStates length rather than FEN comparison: chess.js strips en passant
      // squares that have no capturing pawn, so the FEN strings differ even for the
      // same board state, making FEN comparison unreliable as a dedup guard.
      if (boardStates.value.length === lenBefore) {
        chess.value = new Chess(response.fen)
        pgnMoves.value = [...pgnMoves.value, result.san]
        boardStates.value = [...boardStates.value, response.fen]
        currentIndex.value = boardStates.value.length - 1
        redoStates.value = []
        redoMoves.value = []
        syncStatusFromChess()
        switchClock()
      }
      lastMove.value = { from: result.from, to: result.to }
      selectedSquare.value = null
      legalMoves.value = []
      return true
    } catch (err: unknown) {
      error.value = backendMoveErrorMessage(err)
      return false
    } finally {
      moveInFlight.value = false
    }
  }

  function selectSquare(square: string) {
    if (pendingPromotion.value) return
    if (!isAtLatest.value) return
    if (isGameOver.value) return
    // Block all interaction while a move HTTP round-trip is in flight
    if (moveInFlight.value) return
    // Block when it's computer's turn (skip in puzzle mode)
    if (!puzzleMode.value && isComputerTurn()) return
    // Block when it's the remote opponent's turn in a session
    if (!puzzleMode.value && myColor.value !== 'spectator' && !isMyTurn()) return

    const piece = viewChess.value.get(square as ChessSquare)
    const currentTurn = viewChess.value.turn()

    if (selectedSquare.value && legalMoves.value.includes(square)) {
      // In puzzle mode, don't apply the move — let the puzzle panel handle it
      if (puzzleMode.value) {
        puzzlePendingMove.value = { from: selectedSquare.value, to: square }
        selectedSquare.value = null
        legalMoves.value = []
        return
      }
      const fromPiece = viewChess.value.get(selectedSquare.value as ChessSquare)
      if (fromPiece?.type === 'p') {
        const toRank = square[1]
        if ((fromPiece.color === 'w' && toRank === '8') || (fromPiece.color === 'b' && toRank === '1')) {
          pendingPromotion.value = { from: selectedSquare.value, to: square }
          return
        }
      }
      void applyMove(selectedSquare.value, square).then(ok => {
        if (ok) triggerComputerMoveIfNeeded()
      })
    } else if (piece && piece.color === currentTurn) {
      selectedSquare.value = square
      if (showLegalMoves.value) {
        const moves = viewChess.value.moves({ square: square as ChessSquare, verbose: true }) as VerboseMove[]
        legalMoves.value = moves.map((m) => m.to)
      } else {
        legalMoves.value = []
      }
    } else {
      selectedSquare.value = null
      legalMoves.value = []
    }
  }

  function promote(role: string) {
    if (!pendingPromotion.value) return
    const { from, to } = pendingPromotion.value
    pendingPromotion.value = null
    void applyMove(from, to, role).then(ok => {
      if (ok) triggerComputerMoveIfNeeded()
    })
  }

  function cancelPromotion() {
    pendingPromotion.value = null
    selectedSquare.value = null
    legalMoves.value = []
  }

  // ── Game mode changes ────────────────────────────────────────────────
  function setGameMode(mode: GameMode) {
    // Like desktop ChessGUI Run: CvC always plays from the latest position, not a scrubbed line.
    if (mode === 'cvc' && !isAtLatest.value) goToMove(boardStates.value.length - 1)
    gameMode.value = mode
    if (mode !== 'cvc') { paused.value = false }
    // Keep whiteIsHuman/blackIsHuman in sync so isComputerTurn() is consistent
    // with the new mode.  Without this, switching from a prior HvC game to 'hvh'
    // and then back to 'hvc' leaves the stale "whiteIsHuman=false" in place,
    // making the computer immediately play for white.
    if (mode === 'hvh') {
      whiteIsHuman.value = true
      blackIsHuman.value = true
    } else if (mode === 'cvc') {
      whiteIsHuman.value = false
      blackIsHuman.value = false
    }
    triggerComputerMoveIfNeeded()
  }

  function togglePause() {
    paused.value = !paused.value
    if (paused.value) {
      stopClock()
    } else {
      if (clockStarted.value) { lastTickTime = Date.now(); startClock() }
      // Jump to latest if navigated away
      if (!isAtLatest.value) goToMove(boardStates.value.length - 1)
      triggerComputerMoveIfNeeded()
    }
  }

  // ── Navigation ───────────────────────────────────────────────────────
  function backward() {
    if (currentIndex.value > 0) {
      currentIndex.value--
      selectedSquare.value = null
      legalMoves.value = []
      updateLastMoveFromIndex()
    }
  }

  function forward() {
    if (currentIndex.value < boardStates.value.length - 1) {
      currentIndex.value++
      selectedSquare.value = null
      legalMoves.value = []
      updateLastMoveFromIndex()
    }
  }

  function goToMove(moveIndex: number) {
    const targetIdx = Math.max(0, Math.min(moveIndex, boardStates.value.length - 1))
    currentIndex.value = targetIdx
    selectedSquare.value = null
    legalMoves.value = []
    updateLastMoveFromIndex()
  }

  async function undo() {
    if (!isAtLatest.value) goToMove(boardStates.value.length - 1)
    if (boardStates.value.length <= 1) return

    redoStates.value = [boardStates.value[boardStates.value.length - 1], ...redoStates.value]
    redoMoves.value = [pgnMoves.value[pgnMoves.value.length - 1], ...redoMoves.value]

    boardStates.value = boardStates.value.slice(0, -1)
    pgnMoves.value = pgnMoves.value.slice(0, -1)
    currentIndex.value = boardStates.value.length - 1

    const newFen = boardStates.value[currentIndex.value]
    chess.value = new Chess(newFen)
    selectedSquare.value = null
    legalMoves.value = []
    updateLastMoveFromIndex()
    syncStatusFromChess()

    if (gameId.value) {
      try { await gameApi.loadFen(gameId.value, newFen) } catch { /* best-effort */ }
    }
  }

  async function redo() {
    if (redoStates.value.length === 0) return
    if (!isAtLatest.value) goToMove(boardStates.value.length - 1)

    const state = redoStates.value[0]
    const move = redoMoves.value[0]
    redoStates.value = redoStates.value.slice(1)
    redoMoves.value = redoMoves.value.slice(1)

    boardStates.value = [...boardStates.value, state]
    pgnMoves.value = [...pgnMoves.value, move]
    currentIndex.value = boardStates.value.length - 1

    chess.value = new Chess(state)
    selectedSquare.value = null
    legalMoves.value = []
    updateLastMoveFromIndex()
    syncStatusFromChess()

    if (gameId.value) {
      try { await gameApi.loadFen(gameId.value, state) } catch { /* best-effort */ }
    }
  }

  function flipBoard() {
    boardFlipped.value = !boardFlipped.value
  }

  function updateLastMoveFromIndex() {
    if (currentIndex.value === 0) {
      lastMove.value = null
    } else {
      const prevFen = boardStates.value[currentIndex.value - 1]
      const san = pgnMoves.value[currentIndex.value - 1]
      try {
        const temp = new Chess(prevFen)
        const result = temp.move(san)
        lastMove.value = result ? { from: result.from, to: result.to } : null
      } catch {
        lastMove.value = null
      }
    }
  }

  // ── PGN text formatting ──────────────────────────────────────────────
  const pgnText = computed(() => {
    const lines: string[] = []
    for (let i = 0; i < pgnMoves.value.length; i += 2) {
      const num = Math.floor(i / 2) + 1
      const white = pgnMoves.value[i]
      const black = i + 1 < pgnMoves.value.length ? ` ${pgnMoves.value[i + 1]}` : ''
      lines.push(`${num}. ${white}${black}`)
    }
    return lines.join('\n')
  })

  // ── Import / Export ──────────────────────────────────────────────────
  async function loadFenString(fenStr: string): Promise<boolean> {
    try {
      if (!gameId.value) {
        await createGame(fenStr.trim())
        return !error.value
      }
      const response = await gameApi.loadFen(gameId.value, fenStr.trim())
      chess.value = new Chess(response.fen)
      gameOverByTimeout.value = false
      resetHistory()
      resetClock()
      syncStatusFromChess()
      return true
    } catch {
      error.value = 'Invalid FEN string'
      return false
    }
  }

  async function loadPgnString(pgnStr: string): Promise<boolean> {
    try {
      const c = new Chess()
      c.loadPgn(pgnStr.trim())
      const history = c.history()
      await createGame()
      if (!gameId.value) return false
      const states: string[] = [chess.value.fen()]
      const moves: string[] = []
      let latestFen = chess.value.fen()
      for (const san of history) {
        const response = await gameApi.makeMove(gameId.value, san)
        latestFen = response.fen
        states.push(latestFen)
        moves.push(san)
      }
      chess.value = new Chess(latestFen)
      boardStates.value = states
      pgnMoves.value = moves
      currentIndex.value = states.length - 1
      gameOverByTimeout.value = false
      resetClock()
      selectedSquare.value = null
      legalMoves.value = []
      lastMove.value = null
      error.value = null
      pendingPromotion.value = null
      syncStatusFromChess()
      updateLastMoveFromIndex()
      return true
    } catch {
      error.value = 'Invalid PGN'
      return false
    }
  }

  /** Replay a stored experiment PGN in a fresh CvC session (mirrors desktop Browse Games). */
  async function replayBrowsedExperimentPgn(pgnStr: string): Promise<boolean> {
    const trimmed = pgnStr.trim()
    if (!trimmed) {
      error.value = 'No PGN to replay.'
      return false
    }
    error.value = null
    try {
      const probe = new Chess()
      probe.loadPgn(trimmed)
      const history = probe.history()
      if (history.length === 0) {
        error.value = 'PGN contained no moves.'
        return false
      }

      const browseSettings: GameSettings = {
        whiteIsHuman: false,
        blackIsHuman: false,
        whiteStrategy: 'deepening-opening-endgame',
        blackStrategy: 'deepening-opening-endgame',
      }

      await createGame(undefined, browseSettings, undefined, { skipComputerKickoff: true })
      if (!gameId.value || error.value) return false

      const states: string[] = [chess.value.fen()]
      const moves: string[] = []
      let latestFen = chess.value.fen()
      for (const san of history) {
        const response = await gameApi.makeMove(gameId.value, san)
        latestFen = response.fen
        states.push(latestFen)
        moves.push(san)
      }

      puzzleMode.value = false
      chess.value = new Chess(latestFen)
      boardStates.value = states
      pgnMoves.value = moves
      currentIndex.value = states.length - 1
      redoStates.value = []
      redoMoves.value = []
      gameOverByTimeout.value = false
      resetClock()
      selectedSquare.value = null
      legalMoves.value = []
      lastMove.value = null
      pendingPromotion.value = null
      syncStatusFromChess()
      updateLastMoveFromIndex()
      gameMode.value = 'cvc'
      whiteIsHuman.value = false
      blackIsHuman.value = false
      myColor.value = 'spectator'
      boardFlipped.value = false
      paused.value = false
      triggerComputerMoveIfNeeded()
      return true
    } catch {
      error.value = 'Could not replay experiment PGN.'
      return false
    }
  }

  async function loadPgnOrFen(input: string): Promise<boolean> {
    const trimmed = input.trim()
    // FEN heuristic: contains '/' rank separators and no newlines
    const isFen = (trimmed.match(/\//g) || []).length >= 7 && !trimmed.includes('\n')
    if (isFen) return loadFenString(trimmed)
    return loadPgnString(trimmed)
  }

  function resetGame() {
    gameMode.value = 'hvh'
    paused.value = false
    disconnectWebSocket()
    void createGame()
  }

  function syncStatusFromChess() {
    if (chess.value.isCheckmate()) status.value = 'checkmate'
    else if (chess.value.isStalemate()) status.value = 'stalemate'
    else if (chess.value.isDraw()) status.value = 'draw'
    else status.value = 'in_progress'
  }

  return {
    // State
    chess,
    viewChess,
    gameId,
    myColor,
    whiteIsHuman,
    blackIsHuman,
    fen,
    status,
    selectedSquare,
    legalMoves,
    lastMove,
    loading,
    error,
    pendingPromotion,
    showLegalMoves,
    // Puzzle mode
    puzzleMode,
    puzzlePendingMove,
    // Game mode
    gameMode,
    paused,
    computerScheduled,
    whiteComputerStrategy,
    blackComputerStrategy,
    currentComputerStrategy,
    setComputerStrategy,
    setGameMode,
    togglePause,
    // Clock
    clockMode,
    whiteTimeMs,
    blackTimeMs,
    clockStarted,
    gameOverByTimeout,
    whiteClockDisplay,
    blackClockDisplay,
    setClockMode,
    resetClock,
    // History
    boardStates,
    pgnMoves,
    currentIndex,
    isAtLatest,
    pgnText,
    // Computed
    turn,
    turnColor,
    latestTurn,
    isCheck,
    isCheckmate,
    isStalemate,
    isDraw,
    isGameOver,
    statusText,
    capturedPieces,
    kingSquare,
    pieceSymbols,
    // Actions
    createGame,
    joinGame,
    applyMove,
    selectSquare,
    promote,
    cancelPromotion,
    backward,
    forward,
    goToMove,
    undo,
    redo,
    canUndo,
    canRedo,
    flipBoard,
    boardFlipped,
    resetGame,
    triggerComputerMoveIfNeeded,
    stopPolling,
    // Session WebSocket
    connectWebSocket,
    disconnectWebSocket,
    // Import/Export
    loadFenString,
    loadPgnString,
    loadPgnOrFen,
    replayBrowsedExperimentPgn,
    formatTime: formatClockTime,
  }
})
