<template>
  <div class="chess-board-container">
    <div class="chess-board">
      <div
        v-for="square in squares"
        :key="square"
        :class="squareClass(square)"
        @click="handleSquareClick(square)"
        :aria-label="`Square ${square}`"
        role="button"
        tabindex="0"
      >
        <span v-if="getPiece(square)" class="chess-piece">
          {{ getPieceSymbol(square) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()

const files = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']
const ranks = ['8', '7', '6', '5', '4', '3', '2', '1']

const squares = computed(() => {
  const result: string[] = []
  for (const rank of ranks) {
    for (const file of files) {
      result.push(`${file}${rank}`)
    }
  }
  return result
})

const pieceSymbols: Record<string, string> = {
  'wK': '♔', 'wQ': '♕', 'wR': '♖', 'wB': '♗', 'wN': '♘', 'wP': '♙',
  'bK': '♚', 'bQ': '♛', 'bR': '♜', 'bB': '♝', 'bN': '♞', 'bP': '♟',
}

function isLightSquare(square: string): boolean {
  const file = square.charCodeAt(0) - 'a'.charCodeAt(0)
  const rank = parseInt(square[1])
  return (file + rank) % 2 === 0
}

function squareClass(square: string): string {
  const classes = ['chess-square']
  classes.push(isLightSquare(square) ? 'light' : 'dark')

  if (gameStore.selectedSquare === square) {
    classes.push('selected')
  }

  if (gameStore.legalMoves.includes(square)) {
    classes.push('legal-move')
  }

  if (gameStore.lastMove && (gameStore.lastMove.from === square || gameStore.lastMove.to === square)) {
    classes.push('last-move')
  }

  return classes.join(' ')
}

function getPiece(square: string): any {
  const chess = (gameStore as any).chess
  return chess.get(square)
}

function getPieceSymbol(square: string): string {
  const piece = getPiece(square)
  if (!piece) return ''
  const key = `${piece.color}${piece.type.toUpperCase()}`
  return pieceSymbols[key] || ''
}

function handleSquareClick(square: string) {
  gameStore.selectSquare(square)
}
</script>

<style scoped>
.chess-board-container {
  @apply flex justify-center items-center p-4;
}
</style>
