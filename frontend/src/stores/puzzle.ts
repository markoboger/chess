import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Chess } from 'chess.js'

export interface PuzzleData {
  id: string
  rating: number
  themes: string[]
  solution: string[]   // UCI moves: [opponentSetup, playerMove1, opponentReply1, ...]
  fen: string          // starting position (before opponent's setup move)
}

// Module-level puzzle cache
let allPuzzles: PuzzleData[] | null = null
let puzzleLoadPromise: Promise<PuzzleData[]> | null = null

async function loadAllPuzzles(): Promise<PuzzleData[]> {
  const res = await fetch('/puzzles/lichess_small_puzzle.csv')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const text = await res.text()
  const puzzles: PuzzleData[] = []

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim()
    if (!line || line.startsWith('PuzzleId')) continue
    const parts = line.split(',')
    if (parts.length < 8) continue
    const id = parts[0].trim()
    const fen = parts[1].trim()
    const solution = parts[2].trim().split(' ').filter(m => m.length > 0)
    const rating = Number.parseInt(parts[3] ?? '', 10) || 1500
    const themes = (parts[7] ?? '').trim().split(' ').filter(t => t.length > 0)

    if (!id || !fen || solution.length < 2) continue
    puzzles.push({ id, fen, solution, rating, themes })
  }
  return puzzles
}

function getAllPuzzles(): Promise<PuzzleData[]> {
  if (allPuzzles) return Promise.resolve(allPuzzles)
  if (!puzzleLoadPromise) {
    puzzleLoadPromise = loadAllPuzzles().then(p => { allPuzzles = p; return p })
  }
  return puzzleLoadPromise
}

export const usePuzzleStore = defineStore('puzzle', () => {
  const puzzle = ref<PuzzleData | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Puzzle board state
  const chess = ref(new Chess())
  const solutionStep = ref(0)
  const solved = ref(false)
  const failed = ref(false)
  const active = ref(false)
  const lastFeedback = ref<'correct' | 'wrong' | null>(null)
  let currentPuzzleIndex = -1

  // The position FEN at the start of the puzzle (after opponent's setup move)
  const puzzleFen = computed(() => chess.value.fen())

  // Which UCI move we expect next from the player
  const expectedMove = computed((): string | null => {
    if (!puzzle.value) return null
    // solution[0] is the opponent's stimulus move (auto-played)
    // solution[1], [3], [5]... are player moves
    const playerStep = solutionStep.value * 2 + 1
    return puzzle.value.solution[playerStep] ?? null
  })

  const moveCount = computed(() => {
    if (!puzzle.value) return 0
    return Math.ceil((puzzle.value.solution.length - 1) / 2)
  })

  async function loadDailyPuzzle() {
    loading.value = true
    error.value = null
    solved.value = false
    failed.value = false
    active.value = false
    puzzle.value = null
    lastFeedback.value = null

    try {
      const puzzles = await getAllPuzzles()
      if (puzzles.length === 0) throw new Error('No puzzles available')
      // Pick a random puzzle each time (simulates "daily" variety)
      currentPuzzleIndex = Math.floor(Math.random() * puzzles.length)
      puzzle.value = puzzles[currentPuzzleIndex]
      _setupBoard()
      active.value = true
    } catch (e: any) {
      error.value = 'Could not load puzzle.'
    } finally {
      loading.value = false
    }
  }

  function _setupBoard() {
    if (!puzzle.value) return
    // Load the starting FEN directly (no PGN replay needed)
    const board = new Chess(puzzle.value.fen)

    // Auto-play the opponent's stimulus move (first in the solution list)
    const stimulusUci = puzzle.value.solution[0]
    if (stimulusUci) {
      const from = stimulusUci.slice(0, 2)
      const to = stimulusUci.slice(2, 4)
      const promo = stimulusUci.length === 5 ? stimulusUci[4] : undefined
      board.move({ from, to, ...(promo ? { promotion: promo } : {}) })
    }

    chess.value = board
    solutionStep.value = 0
  }

  /**
   * Try a player move given as UCI string (e.g. "e2e4").
   * Returns { correct, solved } — does NOT auto-play opponent reply.
   * Call applyOpponentReply() separately (with a UI delay) when correct === true and solved === false.
   */
  function tryMove(uciMove: string): { correct: boolean; solved: boolean } {
    if (!puzzle.value || solved.value || failed.value) return { correct: false, solved: false }

    const expected = expectedMove.value
    if (!expected) return { correct: false, solved: false }

    const isCorrect = uciMove.toLowerCase() === expected.toLowerCase()
    lastFeedback.value = isCorrect ? 'correct' : 'wrong'

    if (!isCorrect) {
      failed.value = true
      return { correct: false, solved: false }
    }

    // Apply player's move to the internal chess instance
    const from = uciMove.slice(0, 2)
    const to = uciMove.slice(2, 4)
    const promo = uciMove.length === 5 ? uciMove[4] : undefined
    chess.value.move({ from, to, ...(promo ? { promotion: promo } : {}) })
    solutionStep.value++

    // Check if puzzle is complete (no more opponent replies needed)
    const nextOpponentIdx = solutionStep.value * 2
    if (nextOpponentIdx >= puzzle.value.solution.length) {
      solved.value = true
      return { correct: true, solved: true }
    }

    return { correct: true, solved: false }
  }

  /** Apply the opponent's auto-reply after a player's correct move. */
  function applyOpponentReply(): string | null {
    if (!puzzle.value || solved.value) return null
    const nextOpponentIdx = solutionStep.value * 2
    const oppUci = puzzle.value.solution[nextOpponentIdx]
    if (!oppUci) return null
    const of2 = oppUci.slice(0, 2)
    const ot2 = oppUci.slice(2, 4)
    const op2 = oppUci.length === 5 ? oppUci[4] : undefined
    chess.value.move({ from: of2, to: ot2, ...(op2 ? { promotion: op2 } : {}) })
    if (solutionStep.value * 2 + 1 >= puzzle.value.solution.length) {
      solved.value = true
    }
    return oppUci
  }

  function retry() {
    if (!puzzle.value) return
    failed.value = false
    solved.value = false
    lastFeedback.value = null
    solutionStep.value = 0
    _setupBoard()
  }

  function reset() {
    puzzle.value = null
    active.value = false
    solved.value = false
    failed.value = false
    lastFeedback.value = null
    solutionStep.value = 0
    chess.value = new Chess()
    error.value = null
  }

  return {
    puzzle,
    loading,
    error,
    chess,
    active,
    solved,
    failed,
    lastFeedback,
    puzzleFen,
    expectedMove,
    moveCount,
    solutionStep,
    loadDailyPuzzle,
    tryMove,
    applyOpponentReply,
    retry,
    reset,
  }
})
