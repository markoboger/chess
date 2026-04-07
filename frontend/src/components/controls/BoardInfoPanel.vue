<template>
  <div class="board-info-panel">
    <!-- Clock + Captured pieces -->
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

    <!-- FEN display (compact single-line) -->
    <div class="fen-bar">
      <span class="fen-label">FEN</span>
      <span class="fen-text">{{ gameStore.fen }}</span>
      <button class="copy-btn-sm" @click="copyFen" :title="copyFenLabel">
        <Check v-if="copyFenSuccess" :size="13" />
        <Copy v-else :size="13" />
      </button>
    </div>

    <!-- Session ID (shown when a game is active) -->
    <div v-if="gameStore.gameId" class="fen-bar session-bar">
      <span class="fen-label">SESSION</span>
      <span class="fen-text session-id-text">{{ gameStore.gameId }}</span>
      <button class="copy-btn-sm" @click="copySession" :title="copySessionLabel">
        <Check v-if="copySessionSuccess" :size="13" />
        <Copy v-else :size="13" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGameStore } from '../../stores/game'
import { Copy, Check } from 'lucide-vue-next'

const gameStore = useGameStore()

const copyFenSuccess = ref(false)
const copyFenLabel = ref('Copy FEN')
const copySessionSuccess = ref(false)
const copySessionLabel = ref('Copy session ID')

const isWhiteLow = computed(() => {
  if (gameStore.clockMode.kind !== 'timed') return false
  return gameStore.whiteTimeMs < 30000
})

const isBlackLow = computed(() => {
  if (gameStore.clockMode.kind !== 'timed') return false
  return gameStore.blackTimeMs < 30000
})

function copyFen() {
  navigator.clipboard.writeText(gameStore.fen).then(() => {
    copyFenSuccess.value = true
    copyFenLabel.value = 'Copied!'
    setTimeout(() => { copyFenSuccess.value = false; copyFenLabel.value = 'Copy FEN' }, 1500)
  })
}

function copySession() {
  if (!gameStore.gameId) return
  navigator.clipboard.writeText(gameStore.gameId).then(() => {
    copySessionSuccess.value = true
    copySessionLabel.value = 'Copied!'
    setTimeout(() => { copySessionSuccess.value = false; copySessionLabel.value = 'Copy session ID' }, 1500)
  })
}
</script>

<style scoped>
.board-info-panel {
  border-top: 1px solid #e0ddd8;
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

/* FEN bar (compact single-line) */
.fen-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #f7f6f4;
  min-height: 28px;
}
.fen-label {
  font-weight: 700;
  font-size: 10px;
  color: #888;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}
.fen-text {
  font-family: monospace;
  font-size: 10px;
  color: #555;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.copy-btn-sm {
  font-size: 11px;
  padding: 2px 4px;
  border: 1px solid #ddd;
  border-radius: 3px;
  background: #fafafa;
  cursor: pointer;
  line-height: 1;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.copy-btn-sm:hover { background: #eee; }

.session-bar {
  background: #f0f8e6;
  border-top: 1px solid #d5e8b8;
}
.session-id-text {
  color: #2d5a1b;
}
</style>
