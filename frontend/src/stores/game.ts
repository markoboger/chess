import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Chess } from 'chess.js'
import type { GameStatus, Turn } from '../types/game'
import { useOpeningStore } from './opening'

// ── Clock mode types ─────────────────────────────────────────────────
export interface ClockModeTimed { kind: 'timed'; initialMs: number; incrementMs: number; label: string }
export interface ClockModeNone { kind: 'none' }
export type ClockMode = ClockModeTimed | ClockModeNone

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
export type ComputerStrategyId = 'greedy' | 'opening-continuation'

export const COMPUTER_STRATEGIES: { id: ComputerStrategyId; label: string }[] = [
  { id: 'greedy', label: 'Greedy Capture' },
  { id: 'opening-continuation', label: 'Opening Continuation' },
]

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

  // ── Game mode ────────────────────────────────────────────────────────
  const gameMode = ref<GameMode>('hvh')
  const paused = ref(false)
  const computerScheduled = ref(false)
  const whiteComputerStrategy = ref<ComputerStrategyId>('greedy')
  const blackComputerStrategy = ref<ComputerStrategyId>('opening-continuation')

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
    return {
      byBlack: getCaptured('w'),
      byWhite: getCaptured('b'),
      advantageText: advantage > 0 ? `White +${advantage}` : advantage < 0 ? `Black +${-advantage}` : '=',
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
        if (sq && sq.type === 'k' && sq.color === kingColor) {
          const file = String.fromCharCode('a'.charCodeAt(0) + c)
          const rank = String(8 - r)
          return `${file}${rank}`
        }
      }
    }
    return null
  })

  // ── Clock helpers ────────────────────────────────────────────────────
  function formatTime(ms: number): string {
    const totalSec = Math.max(0, Math.floor(ms / 1000))
    const h = Math.floor(totalSec / 3600)
    const m = Math.floor((totalSec % 3600) / 60)
    const s = totalSec % 60
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }

  const whiteClockDisplay = computed(() => {
    if (clockMode.value.kind === 'none') return formatTime(whiteTimeMs.value)
    return formatTime(whiteTimeMs.value)
  })
  const blackClockDisplay = computed(() => {
    if (clockMode.value.kind === 'none') return formatTime(blackTimeMs.value)
    return formatTime(blackTimeMs.value)
  })

  function startClock() {
    if (clockInterval) return
    lastTickTime = Date.now()
    clockInterval = setInterval(() => {
      if (paused.value || isGameOver.value) return
      const now = Date.now()
      const delta = now - lastTickTime
      lastTickTime = now

      if (latestTurn.value === 'w') {
        if (clockMode.value.kind === 'timed') {
          whiteTimeMs.value = Math.max(0, whiteTimeMs.value - delta)
          if (whiteTimeMs.value <= 0) handleTimeout('w')
        } else {
          whiteTimeMs.value += delta
        }
      } else {
        if (clockMode.value.kind === 'timed') {
          blackTimeMs.value = Math.max(0, blackTimeMs.value - delta)
          if (blackTimeMs.value <= 0) handleTimeout('b')
        } else {
          blackTimeMs.value += delta
        }
      }
    }, 100)
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

  // ── Computer player (random legal moves) ─────────────────────────────
  function isComputerTurn(): boolean {
    if (gameMode.value === 'hvh') return false
    if (gameMode.value === 'hvc') return latestTurn.value === 'b'
    if (gameMode.value === 'cvc') return true
    return false
  }

  function currentComputerStrategy(): ComputerStrategyId {
    return latestTurn.value === 'w' ? whiteComputerStrategy.value : blackComputerStrategy.value
  }

  function setComputerStrategy(side: ComputerSide, strategy: ComputerStrategyId) {
    if (side === 'white') whiteComputerStrategy.value = strategy
    else blackComputerStrategy.value = strategy
    triggerComputerMoveIfNeeded()
  }

  function chooseGreedyMove(moves: any[]): any {
    const captures = moves.filter((m: any) => m.captured)
    if (captures.length > 0) {
      const vals: Record<string, number> = { q: 9, r: 5, b: 3, n: 3, p: 1 }
      captures.sort((a: any, b: any) => (vals[b.captured] || 0) - (vals[a.captured] || 0))
      return captures[0]
    }
    return moves[Math.floor(Math.random() * moves.length)]
  }

  async function chooseOpeningContinuationMove(): Promise<any | null> {
    const openingStore = useOpeningStore()
    const candidate = await openingStore.bestContinuationForFen(chess.value.fen())
    if (!candidate) return null

    try {
      const clone = new Chess(chess.value.fen())
      const result = clone.move(candidate.san as any)
      return result || null
    } catch {
      return null
    }
  }

  function makeComputerMove() {
    if (computerScheduled.value || paused.value || isGameOver.value) return
    if (!isComputerTurn()) return
    if (!isAtLatest.value) return

    computerScheduled.value = true
    const delay = gameMode.value === 'cvc' ? 400 : 250

    setTimeout(async () => {
      computerScheduled.value = false
      if (paused.value || isGameOver.value || !isComputerTurn() || !isAtLatest.value) return

      const moves = chess.value.moves({ verbose: true })
      if (moves.length === 0) return

      let chosen: any
      if (currentComputerStrategy() === 'opening-continuation') {
        chosen = await chooseOpeningContinuationMove()
      }
      if (!chosen) chosen = chooseGreedyMove(moves)

      const promo = chosen.promotion || undefined
      const ok = applyMove(chosen.from, chosen.to, promo)
      if (ok) triggerComputerMoveIfNeeded()
    }, delay)
  }

  function triggerComputerMoveIfNeeded() {
    if (!isGameOver.value && !paused.value && isComputerTurn() && isAtLatest.value) {
      makeComputerMove()
    }
  }

  // ── Actions ──────────────────────────────────────────────────────────
  async function createGame(startFen?: string) {
    loading.value = true
    error.value = null
    try {
      chess.value = startFen ? new Chess(startFen) : new Chess()
    } catch {
      chess.value = new Chess()
    }
    gameId.value = 'local'
    gameOverByTimeout.value = false
    resetHistory()
    resetClock()
    status.value = 'in_progress'
    loading.value = false
    triggerComputerMoveIfNeeded()
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
  }

  function applyMove(from: string, to: string, promotion?: string): boolean {
    if (!isAtLatest.value) {
      error.value = 'Navigate to latest position before making a move.'
      return false
    }
    error.value = null

    const moveObj: any = { from, to }
    if (promotion) moveObj.promotion = promotion

    const result = chess.value.move(moveObj)
    if (!result) {
      error.value = 'Invalid move'
      return false
    }

    pgnMoves.value = [...pgnMoves.value, result.san]
    boardStates.value = [...boardStates.value, chess.value.fen()]
    currentIndex.value = boardStates.value.length - 1
    lastMove.value = { from: result.from, to: result.to }
    selectedSquare.value = null
    legalMoves.value = []

    if (chess.value.isCheckmate()) status.value = 'checkmate'
    else if (chess.value.isStalemate()) status.value = 'stalemate'
    else if (chess.value.isDraw()) status.value = 'draw'
    else status.value = 'in_progress'

    switchClock()
    return true
  }

  function selectSquare(square: string) {
    if (pendingPromotion.value) return
    if (!isAtLatest.value) return
    if (isGameOver.value) return
    // Block human clicks when it's computer's turn (not applicable in puzzle mode)
    if (!puzzleMode.value && isComputerTurn()) return

    const piece = viewChess.value.get(square as any)
    const currentTurn = viewChess.value.turn()

    if (selectedSquare.value && legalMoves.value.includes(square)) {
      // In puzzle mode, don't apply the move — let the puzzle panel handle it
      if (puzzleMode.value) {
        puzzlePendingMove.value = { from: selectedSquare.value, to: square }
        selectedSquare.value = null
        legalMoves.value = []
        return
      }
      const fromPiece = viewChess.value.get(selectedSquare.value as any)
      if (fromPiece && fromPiece.type === 'p') {
        const toRank = square[1]
        if ((fromPiece.color === 'w' && toRank === '8') || (fromPiece.color === 'b' && toRank === '1')) {
          pendingPromotion.value = { from: selectedSquare.value, to: square }
          return
        }
      }
      const ok = applyMove(selectedSquare.value, square)
      if (ok) triggerComputerMoveIfNeeded()
    } else if (piece && piece.color === currentTurn) {
      selectedSquare.value = square
      if (showLegalMoves.value) {
        const moves = viewChess.value.moves({ square: square as any, verbose: true })
        legalMoves.value = moves.map((m: any) => m.to)
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
    const ok = applyMove(from, to, role)
    if (ok) triggerComputerMoveIfNeeded()
  }

  function cancelPromotion() {
    pendingPromotion.value = null
    selectedSquare.value = null
    legalMoves.value = []
  }

  // ── Game mode changes ────────────────────────────────────────────────
  function setGameMode(mode: GameMode) {
    gameMode.value = mode
    if (mode !== 'cvc') { paused.value = false }
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
  function loadFenString(fenStr: string): boolean {
    try {
      const c = new Chess(fenStr.trim())
      chess.value = c
      gameOverByTimeout.value = false
      resetHistory()
      resetClock()
      status.value = 'in_progress'
      return true
    } catch {
      error.value = 'Invalid FEN string'
      return false
    }
  }

  function loadPgnString(pgnStr: string): boolean {
    try {
      const c = new Chess()
      c.loadPgn(pgnStr.trim())
      // Rebuild history by replaying
      const newChess = new Chess()
      const history = c.history({ verbose: true })
      const states: string[] = [newChess.fen()]
      const moves: string[] = []
      for (const m of history) {
        const result = newChess.move({ from: m.from, to: m.to, promotion: m.promotion })
        if (result) {
          states.push(newChess.fen())
          moves.push(result.san)
        }
      }
      chess.value = newChess
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
      if (newChess.isCheckmate()) status.value = 'checkmate'
      else if (newChess.isStalemate()) status.value = 'stalemate'
      else if (newChess.isDraw()) status.value = 'draw'
      else status.value = 'in_progress'
      updateLastMoveFromIndex()
      return true
    } catch {
      error.value = 'Invalid PGN'
      return false
    }
  }

  function loadPgnOrFen(input: string): boolean {
    const trimmed = input.trim()
    // FEN heuristic: contains '/' rank separators and no newlines
    const isFen = (trimmed.match(/\//g) || []).length >= 7 && !trimmed.includes('\n')
    if (isFen) return loadFenString(trimmed)
    return loadPgnString(trimmed)
  }

  function resetGame() {
    chess.value = new Chess()
    gameId.value = 'local'
    gameMode.value = 'hvh'
    paused.value = false
    gameOverByTimeout.value = false
    resetHistory()
    resetClock()
    status.value = 'in_progress'
  }

  return {
    // State
    chess,
    viewChess,
    gameId,
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
    applyMove,
    selectSquare,
    promote,
    cancelPromotion,
    backward,
    forward,
    goToMove,
    resetGame,
    triggerComputerMoveIfNeeded,
    // Import/Export
    loadFenString,
    loadPgnString,
    loadPgnOrFen,
    formatTime,
  }
})
