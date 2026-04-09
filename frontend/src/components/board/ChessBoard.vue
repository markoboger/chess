<template>
  <div class="board-wrapper">
    <!-- Promotion dialog overlay -->
    <div v-if="gameStore.pendingPromotion" class="promo-overlay" @click.self="gameStore.cancelPromotion()">
      <div class="promo-dialog">
        <div class="promo-title">Promote pawn to:</div>
        <div class="promo-options">
          <button v-for="p in promotionPieces" :key="p.role" class="promo-btn" @click="gameStore.promote(p.role)">
            <span class="promo-piece">{{ p.symbol }}</span>
            <span class="promo-label">{{ p.name }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Board with coordinates -->
    <div class="board-with-coords">
      <!-- Top file labels -->
      <div class="file-labels">
        <div class="coord-spacer"></div>
        <div v-for="f in files" :key="'ft-'+f" class="coord-file">{{ f }}</div>
      </div>

      <div class="board-row-wrapper">
        <!-- Left rank labels -->
        <div class="rank-labels">
          <div v-for="r in ranks" :key="'rl-'+r" class="coord-rank">{{ r }}</div>
        </div>

        <!-- The board -->
        <div class="board-grid" :class="{ 'not-latest': !gameStore.isAtLatest }">
          <div
            v-for="square in squares"
            :key="square"
            :style="squareStyle(square)"
            class="sq"
            @click="gameStore.selectSquare(square)"
          >
            <!-- Legal move dot / capture ring -->
            <div v-if="isLegalTarget(square) && !hasPiece(square)" class="move-dot"></div>
            <div v-if="isLegalTarget(square) && hasPiece(square)" class="capture-ring"></div>
            <!-- Piece -->
            <span v-if="hasPiece(square)" class="piece" :class="{ 'piece-capture-target': isLegalTarget(square) }">
              {{ getPieceSymbol(square) }}
            </span>
          </div>
        </div>

        <!-- Right rank labels -->
        <div class="rank-labels">
          <div v-for="r in ranks" :key="'rr-'+r" class="coord-rank">{{ r }}</div>
        </div>
      </div>

      <!-- Bottom file labels -->
      <div class="file-labels">
        <div class="coord-spacer"></div>
        <div v-for="f in files" :key="'fb-'+f" class="coord-file">{{ f }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()

const filesNormal = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']
const ranksNormal = ['8', '7', '6', '5', '4', '3', '2', '1']
const filesFlipped = [...filesNormal].reverse()
const ranksFlipped = [...ranksNormal].reverse()

const files = computed(() => gameStore.boardFlipped ? filesFlipped : filesNormal)
const ranks = computed(() => gameStore.boardFlipped ? ranksFlipped : ranksNormal)

const squares = computed(() => {
  const result: string[] = []
  for (const rank of ranks.value) {
    for (const file of files.value) {
      result.push(`${file}${rank}`)
    }
  }
  return result
})

const promotionPieces = computed(() => {
  const color = gameStore.turn === 'w' ? 'w' : 'b'
  return [
    { role: 'q', name: 'Queen',  symbol: gameStore.pieceSymbols[`${color}q`] },
    { role: 'r', name: 'Rook',   symbol: gameStore.pieceSymbols[`${color}r`] },
    { role: 'b', name: 'Bishop', symbol: gameStore.pieceSymbols[`${color}b`] },
    { role: 'n', name: 'Knight', symbol: gameStore.pieceSymbols[`${color}n`] },
  ]
})

// Lichess-style board colors
const LIGHT = '#f0d9b5'
const DARK  = '#b58863'
const SELECTED = '#baca44'
const LAST_MOVE_LIGHT = '#cdd16e'
const LAST_MOVE_DARK  = '#aaa23a'
const CHECK_COLOR = '#e74c3c'
const CHECKMATE_COLOR = '#c0392b'

function isLight(square: string): boolean {
  const fileCode = square.codePointAt(0) ?? 0
  const aCode = 'a'.codePointAt(0) ?? 0
  const f = fileCode - aCode
  const r = Number.parseInt(square[1] ?? '0', 10)
  return (f + r) % 2 === 0
}

function squareStyle(square: string): Record<string, string> {
  const light = isLight(square)
  let bg: string

  if (gameStore.selectedSquare === square) {
    bg = SELECTED
  } else if (gameStore.kingSquare === square) {
    bg = gameStore.isCheckmate ? CHECKMATE_COLOR : CHECK_COLOR
  } else if (gameStore.lastMove && (gameStore.lastMove.from === square || gameStore.lastMove.to === square)) {
    bg = light ? LAST_MOVE_LIGHT : LAST_MOVE_DARK
  } else {
    bg = light ? LIGHT : DARK
  }

  return { 'background-color': bg }
}

function isLegalTarget(square: string): boolean {
  return gameStore.legalMoves.includes(square)
}

function hasPiece(square: string): boolean {
  return !!gameStore.viewChess.get(square as any)
}

function getPieceSymbol(square: string): string {
  const piece = gameStore.viewChess.get(square as any)
  if (!piece) return ''
  const key = `${piece.color}${piece.type}`
  return gameStore.pieceSymbols[key] || ''
}
</script>

<style scoped>
.board-wrapper {
  position: relative;
  display: flex;
  justify-content: center;
  padding: 8px;
  --sq: min(80px, calc((100vw - 400px) / 8));
}

.board-with-coords {
  display: flex;
  flex-direction: column;
  align-items: center;
  user-select: none;
}

.file-labels {
  display: flex;
  align-items: center;
}

.coord-spacer {
  width: calc(var(--sq) * 0.3);
}

.coord-file {
  width: var(--sq);
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  color: #666;
  line-height: 20px;
}

.rank-labels {
  display: flex;
  flex-direction: column;
}

.coord-rank {
  height: var(--sq);
  width: calc(var(--sq) * 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  color: #666;
}

.board-row-wrapper {
  display: flex;
  align-items: stretch;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(8, var(--sq));
  grid-template-rows: repeat(8, var(--sq));
  border: 3px solid #333;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0,0,0,0.25);
}

.board-grid.not-latest {
  opacity: 0.85;
}

.sq {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.1s;
}

.piece {
  font-size: calc(var(--sq) * 0.625);
  line-height: 1;
  cursor: grab;
  transition: transform 0.15s;
  text-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.piece:hover {
  transform: scale(1.08);
}

.piece-capture-target {
  opacity: 0.9;
}

.move-dot {
  position: absolute;
  width: calc(var(--sq) * 0.275);
  height: calc(var(--sq) * 0.275);
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.2);
  pointer-events: none;
  z-index: 1;
}

.capture-ring {
  position: absolute;
  width: calc(var(--sq) * 0.775);
  height: calc(var(--sq) * 0.775);
  border-radius: 50%;
  border: calc(var(--sq) * 0.075) solid rgba(0, 0, 0, 0.2);
  pointer-events: none;
  z-index: 1;
}

/* Promotion dialog */
.promo-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  border-radius: 8px;
}

.promo-dialog {
  background: white;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.3);
  text-align: center;
}

.promo-title {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 16px;
  color: #333;
}

.promo-options {
  display: flex;
  gap: 8px;
}

.promo-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 14px;
  border: 2px solid #ddd;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.15s;
}

.promo-btn:hover {
  border-color: #b58863;
  background: #f0d9b5;
  transform: scale(1.05);
}

.promo-piece {
  font-size: 40px;
  line-height: 1;
}

.promo-label {
  font-size: 11px;
  font-weight: 600;
  color: #555;
  margin-top: 4px;
}

/* Responsive: when sidebar stacks below on narrow screens */
@media (max-width: 900px) {
  .board-wrapper {
    --sq: min(80px, calc((100vw - 80px) / 8));
  }
}
</style>
