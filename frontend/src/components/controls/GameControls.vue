<template>
  <div class="controls">
    <!-- Status -->
    <div class="status-bar" :class="statusClass">
      {{ gameStore.statusText }}
    </div>

    <!-- Error -->
    <div v-if="gameStore.error" class="error-bar">
      {{ gameStore.error }}
    </div>

    <!-- Clock + Captured pieces panel -->
    <div class="clock-captures-panel">
      <div class="captures-side">
        <div class="captured-row">
          <span v-for="(sym, i) in gameStore.capturedPieces.byBlack" :key="'bc'+i" class="captured-piece">{{ sym }}</span>
          <span v-if="gameStore.capturedPieces.byBlack.length === 0" class="captured-empty">&nbsp;</span>
        </div>
        <div class="material-label">{{ gameStore.capturedPieces.advantageText }}</div>
        <div class="captured-row">
          <span v-for="(sym, i) in gameStore.capturedPieces.byWhite" :key="'wc'+i" class="captured-piece">{{ sym }}</span>
          <span v-if="gameStore.capturedPieces.byWhite.length === 0" class="captured-empty">&nbsp;</span>
        </div>
      </div>
      <div class="clock-side">
        <div class="clock-label">BLACK</div>
        <div class="clock-time" :class="{ 'clock-active': gameStore.latestTurn === 'b' && gameStore.clockStarted, 'clock-danger': isBlackLow }">
          {{ gameStore.blackClockDisplay }}
        </div>
        <div class="clock-label">WHITE</div>
        <div class="clock-time" :class="{ 'clock-active': gameStore.latestTurn === 'w' && gameStore.clockStarted, 'clock-danger': isWhiteLow }">
          {{ gameStore.whiteClockDisplay }}
        </div>
      </div>
    </div>

    <!-- PGN display -->
    <div class="pgn-section">
      <div class="section-header">
        <span class="section-title">Game PGN</span>
        <button class="copy-btn" @click="copyPgn" :title="copyPgnLabel">{{ copyPgnIcon }}</button>
      </div>
      <div class="pgn-scroll" ref="pgnScrollRef">
        <span v-if="gameStore.pgnMoves.length === 0" class="pgn-empty">No moves yet</span>
        <template v-for="(move, i) in gameStore.pgnMoves" :key="i">
          <span v-if="i % 2 === 0" class="pgn-number">{{ Math.floor(i / 2) + 1 }}.</span>
          <span
            class="pgn-move"
            :class="{ 'pgn-active': i === gameStore.currentIndex - 1 }"
            @click="gameStore.goToMove(i + 1)"
          >{{ move }}</span>
          <br v-if="i % 2 === 1" />
        </template>
      </div>
    </div>

    <!-- Navigation -->
    <div class="nav-row">
      <button class="nav-btn" @click="gameStore.goToMove(0)" :disabled="gameStore.currentIndex === 0" title="Start">⏮</button>
      <button class="nav-btn" @click="gameStore.backward()" :disabled="gameStore.currentIndex === 0" title="Back">◀</button>
      <button class="nav-btn" @click="gameStore.forward()" :disabled="gameStore.isAtLatest" title="Forward">▶</button>
      <button class="nav-btn" @click="gameStore.goToMove(gameStore.boardStates.length - 1)" :disabled="gameStore.isAtLatest" title="End">⏭</button>
    </div>

    <!-- FEN display -->
    <div class="fen-section">
      <div class="section-header">
        <span class="section-title">FEN</span>
        <button class="copy-btn" @click="copyFen" :title="copyFenLabel">{{ copyFenIcon }}</button>
      </div>
      <div class="fen-text">{{ gameStore.fen }}</div>
    </div>

    <!-- Game buttons -->
    <div class="button-row">
      <button class="btn-new-game" @click="gameStore.resetGame()">★ New Game</button>
      <button v-if="gameStore.gameMode !== 'cvc'" class="btn-run" @click="gameStore.setGameMode('cvc')">▶ Run</button>
      <button v-if="gameStore.gameMode === 'cvc'" class="btn-pause" @click="gameStore.togglePause()">
        {{ gameStore.paused ? '▶ Continue' : '⏸ Pause' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()
const pgnScrollRef = ref<HTMLElement | null>(null)

const copyPgnIcon = ref('📋')
const copyPgnLabel = ref('Copy PGN')
const copyFenIcon = ref('📋')
const copyFenLabel = ref('Copy FEN')

const statusClass = computed(() => {
  if (gameStore.gameOverByTimeout) return 'status-checkmate'
  if (gameStore.isCheckmate) return 'status-checkmate'
  if (gameStore.isStalemate || gameStore.isDraw) return 'status-draw'
  if (gameStore.isCheck) return 'status-check'
  if (gameStore.paused) return 'status-paused'
  return ''
})

const isWhiteLow = computed(() => {
  if (gameStore.clockMode.kind !== 'timed') return false
  return gameStore.whiteTimeMs < 30000
})

const isBlackLow = computed(() => {
  if (gameStore.clockMode.kind !== 'timed') return false
  return gameStore.blackTimeMs < 30000
})

function copyToClipboard(text: string, iconRef: typeof copyPgnIcon, labelRef: typeof copyPgnLabel) {
  navigator.clipboard.writeText(text).then(() => {
    iconRef.value = '✓'
    labelRef.value = 'Copied!'
    setTimeout(() => { iconRef.value = '📋'; labelRef.value = iconRef === copyPgnIcon ? 'Copy PGN' : 'Copy FEN' }, 1500)
  })
}

function copyPgn() { copyToClipboard(gameStore.pgnText, copyPgnIcon, copyPgnLabel) }
function copyFen() { copyToClipboard(gameStore.fen, copyFenIcon, copyFenLabel) }

// Auto-scroll PGN to active move
watch(() => gameStore.currentIndex, async () => {
  await nextTick()
  if (!pgnScrollRef.value) return
  const active = pgnScrollRef.value.querySelector('.pgn-active')
  if (active) active.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  else pgnScrollRef.value.scrollTop = pgnScrollRef.value.scrollHeight
})
</script>

<style scoped>
.controls {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.status-bar {
  padding: 10px 16px;
  font-weight: 700;
  font-size: 15px;
  color: #333;
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
}
.status-check { color: #e67e22; background: #fef3e2; }
.status-checkmate { color: #c0392b; background: #fde8e8; }
.status-draw { color: #2980b9; background: #e8f4fd; }
.status-paused { color: #555; background: #f0f0f0; }

.error-bar {
  padding: 6px 16px;
  font-size: 13px;
  color: #c0392b;
  background: #fde8e8;
  border-bottom: 1px solid #f5c6cb;
}

/* Clock + Captures panel */
.clock-captures-panel {
  display: flex;
  background: #ebebeb;
  border-bottom: 1px solid #ddd;
}
.captures-side {
  flex: 1;
  padding: 8px 12px;
}
.clock-side {
  padding: 6px 14px;
  text-align: right;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  border-left: 1px solid #ddd;
}
.clock-label {
  font-size: 10px;
  font-weight: 700;
  color: #888;
  letter-spacing: 0.5px;
}
.clock-time {
  font-family: monospace;
  font-size: 22px;
  font-weight: 700;
  color: #888;
  line-height: 1.2;
}
.clock-time.clock-active {
  color: #333;
  background: #d5e8d4;
  border-radius: 4px;
  padding: 0 4px;
}
.clock-time.clock-danger {
  color: #e74c3c;
}
.clock-time.clock-active.clock-danger {
  color: #e74c3c;
  background: #fde8e8;
}

.captured-row {
  display: flex;
  flex-wrap: wrap;
  gap: 1px;
  min-height: 24px;
  align-items: center;
}
.captured-piece { font-size: 20px; line-height: 1; }
.captured-empty { font-size: 20px; }
.material-label {
  text-align: center;
  font-weight: 700;
  font-size: 11px;
  color: #555;
  padding: 1px 0;
}

.pgn-section { padding: 0 16px; }
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0 6px;
}
.section-title { font-weight: 600; font-size: 13px; color: #555; }
.copy-btn {
  font-size: 13px;
  padding: 2px 6px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fafafa;
  cursor: pointer;
  line-height: 1;
}
.copy-btn:hover { background: #eee; }

.pgn-scroll {
  font-family: monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 330px;
  overflow-y: auto;
  padding: 8px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 6px;
}
.pgn-empty { color: #aaa; font-style: italic; }
.pgn-number { color: #888; margin-right: 2px; }
.pgn-move {
  cursor: pointer;
  padding: 1px 3px;
  border-radius: 3px;
  margin-right: 4px;
}
.pgn-move:hover { color: #1565C0; background: #e3f2fd; }
.pgn-active {
  font-weight: 700;
  color: #1565C0;
  background: #e3f2fd;
}

.nav-row {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
}
.nav-btn {
  width: 48px;
  height: 36px;
  font-size: 16px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fafafa;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-btn:hover:not(:disabled) { background: #e8e8e8; }
.nav-btn:disabled { opacity: 0.35; cursor: default; }

.fen-section { padding: 0 16px; }
.fen-text {
  font-family: monospace;
  font-size: 11px;
  padding: 6px 8px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 6px;
  word-break: break-all;
  color: #333;
  line-height: 1.4;
}

.button-row {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding: 14px 16px;
}
.btn-new-game {
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #f1c40f;
  color: #333;
  transition: all 0.15s;
}
.btn-new-game:hover { background: #d4ac0d; transform: scale(1.02); }

.btn-run {
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #27ae60;
  color: white;
  transition: all 0.15s;
}
.btn-run:hover { background: #219a52; transform: scale(1.02); }

.btn-pause {
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #e67e22;
  color: white;
  transition: all 0.15s;
}
.btn-pause:hover { background: #cf6d17; transform: scale(1.02); }
</style>
