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

        <!-- The board + best-move arrow (Stockfish) -->
        <div class="board-grid-wrap">
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
              <!-- Piece (hidden at arrival square while a flying clone animates) -->
              <span
                v-if="hasPiece(square)"
                class="piece"
                :class="{
                  'piece-capture-target': isLegalTarget(square),
                  'piece-under-fly': flyMask.has(square),
                }"
              >
                {{ getPieceSymbol(square) }}
              </span>
            </div>
          </div>
          <!-- Lichess-style sliding piece overlay (one step at a time) -->
          <div
            v-if="flyLayer && flyLayer.items.length"
            class="piece-anim-layer"
            aria-hidden="true"
          >
            <div
              v-for="p in flyLayer.items"
              :key="p.id"
              class="flying-piece"
              :class="{ 'flying-piece--run': flyLayer.armed }"
              :style="{
                '--fc': String(p.fc),
                '--fr': String(p.fr),
                '--dc': String(p.dc),
                '--dr': String(p.dr),
              }"
            >
              {{ p.symbol }}
            </div>
          </div>
          <svg
            v-if="bestMoveArrow"
            class="analysis-arrow"
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <defs>
              <!-- ref at base midpoint: line ends at base, head extends toward tip (no shaft through head) -->
              <marker
                :id="arrowMarkerId"
                markerUnits="strokeWidth"
                markerWidth="2.4"
                markerHeight="2.4"
                refX="0"
                refY="1.2"
                orient="auto"
              >
                <polygon points="0 0, 2.4 1.2, 0 2.4" fill="rgba(56, 118, 75, 0.62)" />
              </marker>
            </defs>
            <line
              :x1="bestMoveArrow.x1"
              :y1="bestMoveArrow.y1"
              :x2="bestMoveArrow.x2"
              :y2="bestMoveArrow.y2"
              stroke="rgba(56, 118, 75, 0.5)"
              stroke-width="1.1"
              stroke-linecap="butt"
              :marker-end="`url(#${arrowMarkerId})`"
            />
          </svg>
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
import { computed, watch, useId, ref, nextTick, onBeforeUnmount } from 'vue'
import { Chess } from 'chess.js'
import type { Square as ChessSquare } from 'chess.js'
import { useGameStore } from '../../stores/game'
import { useAnalysisStore } from '../../stores/analysis'

const gameStore = useGameStore()
const analysisStore = useAnalysisStore()
const arrowMarkerId = useId().replace(/:/g, '')

/** Milliseconds — keep in sync with `.flying-piece` transition */
const FLY_MS = 200

type FlyItem = {
  id: string
  from: string
  to: string
  symbol: string
  fc: number
  fr: number
  dc: number
  dr: number
}

const flyLayer = ref<{ items: FlyItem[]; armed: boolean } | null>(null)
const flyMask = computed(() => {
  if (!flyLayer.value?.items.length) return new Set<string>()
  return new Set(flyLayer.value.items.map((p) => p.to))
})

let flyClearTimer: ReturnType<typeof setTimeout> | null = null

function clearFlyTimer() {
  if (flyClearTimer != null) {
    clearTimeout(flyClearTimer)
    flyClearTimer = null
  }
}

function displayGrid(square: string): { col: number; row: number } {
  const file = square.codePointAt(0)! - 'a'.codePointAt(0)!
  const rank = Number.parseInt(square[1] ?? '0', 10)
  let col = file
  let row = 8 - rank
  if (gameStore.boardFlipped) {
    col = 7 - col
    row = 7 - row
  }
  return { col, row }
}

function rookCastleFromTo(kingFrom: string, kingTo: string): { from: string; to: string } | null {
  if (kingFrom[0] !== 'e' || kingFrom[1] !== kingTo[1]) return null
  if (kingTo[0] === 'g') return { from: `h${kingFrom[1]}`, to: `f${kingFrom[1]}` }
  if (kingTo[0] === 'c') return { from: `a${kingFrom[1]}`, to: `d${kingFrom[1]}` }
  return null
}

function buildFlyItems(): FlyItem[] | null {
  const idx = gameStore.currentIndex
  const states = gameStore.boardStates
  const pgn = gameStore.pgnMoves
  if (idx < 1 || idx >= states.length) return null
  const prevFen = states[idx - 1]
  const san = pgn[idx - 1]
  if (!san) return null
  try {
    const probe = new Chess(prevFen)
    const mv = probe.move(san)
    if (!mv) return null
    const after = new Chess(states[idx]!)
    const moved = after.get(mv.to as ChessSquare)
    if (!moved) return null
    const sym =
      gameStore.pieceSymbols[`${moved.color}${moved.type}`] || ''
    const a = displayGrid(mv.from)
    const b = displayGrid(mv.to)
    const items: FlyItem[] = [
      {
        id: 'm',
        from: mv.from,
        to: mv.to,
        symbol: sym,
        fc: a.col,
        fr: a.row,
        dc: b.col - a.col,
        dr: b.row - a.row,
      },
    ]
    if (mv.piece === 'k' && Math.abs(mv.to.charCodeAt(0) - mv.from.charCodeAt(0)) === 2) {
      const rk = rookCastleFromTo(mv.from, mv.to)
      if (rk) {
        const prevBoard = new Chess(prevFen)
        const rPiece = prevBoard.get(rk.from as ChessSquare)
        if (rPiece) {
          const ra = displayGrid(rk.from)
          const rb = displayGrid(rk.to)
          const rsym =
            gameStore.pieceSymbols[`${rPiece.color}${rPiece.type}`] || ''
          items.push({
            id: 'r',
            from: rk.from,
            to: rk.to,
            symbol: rsym,
            fc: ra.col,
            fr: ra.row,
            dc: rb.col - ra.col,
            dr: rb.row - ra.row,
          })
        }
      }
    }
    return items
  } catch {
    return null
  }
}

async function runMoveFlyAnimation() {
  clearFlyTimer()
  flyLayer.value = null

  if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }

  const items = buildFlyItems()
  if (!items?.length) return

  flyLayer.value = { items, armed: false }
  await nextTick()
  await new Promise<void>((r) => requestAnimationFrame(() => requestAnimationFrame(() => r())))
  flyLayer.value = { items, armed: true }
  flyClearTimer = setTimeout(() => {
    flyLayer.value = null
    flyClearTimer = null
  }, FLY_MS + 40)
}

watch(
  () =>
    ({
      len: gameStore.boardStates.length,
      idx: gameStore.currentIndex,
      lm: gameStore.lastMove,
      flipped: gameStore.boardFlipped,
    }) as const,
  async (cur, prev) => {
    if (cur.flipped !== prev?.flipped) {
      clearFlyTimer()
      flyLayer.value = null
      return
    }
    if (!cur.lm) {
      clearFlyTimer()
      flyLayer.value = null
      return
    }
    if (!prev) return

    /** Exactly one new state appended while the user was already on the previous tip */
    const appendedOneAtTip =
      cur.len === prev.len + 1 &&
      cur.idx === cur.len - 1 &&
      prev.idx === prev.len - 1

    /** Move history scrubber: single step forward */
    const steppedForwardOne =
      cur.len === prev.len && cur.idx === prev.idx + 1 && cur.idx > 0

    if (!appendedOneAtTip && !steppedForwardOne) {
      clearFlyTimer()
      flyLayer.value = null
      return
    }

    await runMoveFlyAnimation()
  }
)

onBeforeUnmount(() => {
  clearFlyTimer()
  flyLayer.value = null
})

function squareCenterPct(square: string, flipped: boolean): { x: number; y: number } {
  const file = square.charCodeAt(0) - 'a'.charCodeAt(0)
  const rank = Number.parseInt(square[1] ?? '0', 10)
  let col = file
  let row = 8 - rank
  if (flipped) {
    col = 7 - col
    row = 7 - row
  }
  return { x: ((col + 0.5) / 8) * 100, y: ((row + 0.5) / 8) * 100 }
}

const bestMoveArrow = computed(() => {
  if (!analysisStore.enabled) return null
  const r = analysisStore.cache.get(gameStore.fen)
  if (!r || r.bestFrom.length !== 2 || r.bestTo.length !== 2) return null
  const a = squareCenterPct(r.bestFrom, gameStore.boardFlipped)
  const b = squareCenterPct(r.bestTo, gameStore.boardFlipped)
  const dx = b.x - a.x
  const dy = b.y - a.y
  const len = Math.hypot(dx, dy) || 1
  const udx = dx / len
  const udy = dy / len
  /** Must match marker: markerWidth × stroke-width (see SVG markerUnits="strokeWidth"). */
  const strokeW = 1.1
  const headLen = 2.4 * strokeW + 0.35
  // Match desktop ChessGUI.drawArrow: shaft runs from **from-square center** to just before the
  // head; do not inset the tail toward `to` (large insets land the visible start on the wrong square).
  const tipX = b.x
  const tipY = b.y
  return {
    x1: a.x,
    y1: a.y,
    x2: tipX - udx * headLen,
    y2: tipY - udy * headLen,
  }
})

watch(
  () => [gameStore.fen, analysisStore.enabled] as const,
  ([fen, on]) => {
    if (on) analysisStore.ensureAnalyzed(fen)
  },
  { immediate: true }
)

watch(
  () => gameStore.boardStates.length,
  () => {
    if (analysisStore.enabled) analysisStore.prefetchBoardStates(gameStore.boardStates)
  }
)

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
  color: var(--color-coord);
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
  color: var(--color-coord);
}

.board-row-wrapper {
  display: flex;
  align-items: stretch;
}

.board-grid-wrap {
  position: relative;
  display: inline-block;
}

.piece-anim-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 3;
}

.flying-piece {
  position: absolute;
  left: calc(var(--fc) * var(--sq));
  top: calc(var(--fr) * var(--sq));
  width: var(--sq);
  height: var(--sq);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(var(--sq) * 0.625);
  line-height: 1;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  transform: translate(0, 0);
  transition: transform 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
  will-change: transform;
}

.flying-piece--run {
  transform: translate(calc(var(--dc) * var(--sq)), calc(var(--dr) * var(--sq)));
}

@media (prefers-reduced-motion: reduce) {
  .flying-piece {
    transition: none;
  }
}

.piece-under-fly {
  opacity: 0;
}

.analysis-arrow {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
  opacity: 0.88;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(8, var(--sq));
  grid-template-rows: repeat(8, var(--sq));
  border: 3px solid var(--color-board-frame);
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 8px 24px var(--color-board-shadow);
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
  background: var(--color-promo-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  border-radius: 8px;
}

.promo-dialog {
  background: var(--color-promo-dialog-bg);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.3);
  text-align: center;
  border: 1px solid var(--color-border);
}

.promo-title {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 16px;
  color: var(--color-promo-title);
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
  border: 2px solid var(--color-promo-btn-border);
  border-radius: 8px;
  background: var(--color-promo-btn-bg);
  cursor: pointer;
  transition: all 0.15s;
}

.promo-btn:hover {
  border-color: var(--color-promo-btn-hover-border);
  background: var(--color-promo-btn-hover-bg);
  transform: scale(1.05);
}

.promo-piece {
  font-size: 40px;
  line-height: 1;
}

.promo-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-promo-label);
  margin-top: 4px;
}

/* Responsive: when sidebar stacks below on narrow screens */
@media (max-width: 900px) {
  .board-wrapper {
    --sq: min(80px, calc((100vw - 80px) / 8));
  }
}
</style>
