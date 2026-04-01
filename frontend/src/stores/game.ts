import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Chess } from 'chess.js'
import { gameApi } from '../api/game-api'
import type { GameStatus, Turn } from '../types/game'

export const useGameStore = defineStore('game', () => {
  // State
  const gameId = ref<string | null>(null)
  const chess = ref(new Chess())
  const fen = ref(chess.value.fen())
  const pgn = ref('')
  const status = ref<GameStatus>('in_progress')
  const moveHistory = ref<string[]>([])
  const selectedSquare = ref<string | null>(null)
  const legalMoves = ref<string[]>([])
  const lastMove = ref<{ from: string; to: string } | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Computed
  const turn = computed((): Turn => chess.value.turn())
  const turnColor = computed(() => (chess.value.turn() === 'w' ? 'white' : 'black'))
  const isCheck = computed(() => chess.value.isCheck())
  const isCheckmate = computed(() => chess.value.isCheckmate())
  const isStalemate = computed(() => chess.value.isStalemate())
  const isDraw = computed(() => chess.value.isDraw())
  const isGameOver = computed(() => chess.value.isGameOver())

  // Actions
  async function createGame(startFen?: string) {
    loading.value = true
    error.value = null
    try {
      const response = await gameApi.createGame(startFen ? { startFen } : undefined)
      gameId.value = response.gameId
      fen.value = response.fen
      pgn.value = response.pgn
      chess.value = new Chess(response.fen)
      moveHistory.value = []
      lastMove.value = null
      selectedSquare.value = null
      status.value = 'in_progress'
    } catch (err: any) {
      error.value = err.message || 'Failed to create game'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function makeMove(move: string) {
    if (!gameId.value) {
      error.value = 'No active game'
      return false
    }

    loading.value = true
    error.value = null
    try {
      const result = chess.value.move(move)
      if (!result) {
        error.value = 'Invalid move'
        return false
      }

      const response = await gameApi.makeMove(gameId.value, move)
      if (response.success) {
        fen.value = response.fen
        pgn.value = response.pgn
        moveHistory.value = response.moveHistory
        lastMove.value = { from: result.from, to: result.to }
        selectedSquare.value = null
        legalMoves.value = []

        if (chess.value.isCheckmate()) {
          status.value = 'checkmate'
        } else if (chess.value.isStalemate()) {
          status.value = 'stalemate'
        } else if (chess.value.isDraw()) {
          status.value = 'draw'
        }
        return true
      } else {
        error.value = response.error || 'Move failed'
        chess.value.undo()
        return false
      }
    } catch (err: any) {
      error.value = err.message || 'Failed to make move'
      chess.value.undo()
      return false
    } finally {
      loading.value = false
    }
  }

  function selectSquare(square: string) {
    const piece = chess.value.get(square as any)
    if (piece && piece.color === chess.value.turn()) {
      selectedSquare.value = square
      const moves = chess.value.moves({ square: square as any, verbose: true })
      legalMoves.value = moves.map((m: any) => m.to)
    } else if (selectedSquare.value && legalMoves.value.includes(square)) {
      makeMove({ from: selectedSquare.value, to: square } as any)
    } else {
      selectedSquare.value = null
      legalMoves.value = []
    }
  }

  async function loadFen(fenString: string) {
    if (!gameId.value) {
      error.value = 'No active game'
      return
    }

    loading.value = true
    error.value = null
    try {
      const response = await gameApi.loadFen(gameId.value, fenString)
      fen.value = response.fen
      pgn.value = response.pgn
      chess.value = new Chess(response.fen)
      moveHistory.value = response.moveHistory
      lastMove.value = null
      selectedSquare.value = null
    } catch (err: any) {
      error.value = err.message || 'Failed to load FEN'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteGame() {
    if (!gameId.value) return

    try {
      await gameApi.deleteGame(gameId.value)
      resetGame()
    } catch (err: any) {
      error.value = err.message || 'Failed to delete game'
    }
  }

  function resetGame() {
    gameId.value = null
    chess.value = new Chess()
    fen.value = chess.value.fen()
    pgn.value = ''
    status.value = 'in_progress'
    moveHistory.value = []
    selectedSquare.value = null
    legalMoves.value = []
    lastMove.value = null
    error.value = null
  }

  return {
    // State
    gameId,
    fen,
    pgn,
    status,
    moveHistory,
    selectedSquare,
    legalMoves,
    lastMove,
    loading,
    error,
    // Computed
    turn,
    turnColor,
    isCheck,
    isCheckmate,
    isStalemate,
    isDraw,
    isGameOver,
    // Actions
    createGame,
    makeMove,
    selectSquare,
    loadFen,
    deleteGame,
    resetGame,
  }
})
