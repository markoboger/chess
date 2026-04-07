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

    <!-- PGN display -->
    <div class="pgn-section">
      <div class="section-header">
        <span class="section-title">Moves</span>
        <button class="copy-btn" @click="copyPgn" :title="copyPgnLabel">
          <Check v-if="copySuccess" :size="14" />
          <Copy v-else :size="14" />
        </button>
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
      <button class="nav-btn" @click="gameStore.goToMove(0)" :disabled="gameStore.currentIndex === 0" title="Start"><ChevronsLeft :size="16" /></button>
      <button class="nav-btn" @click="gameStore.backward()" :disabled="gameStore.currentIndex === 0" title="Back"><ChevronLeft :size="16" /></button>
      <button class="nav-btn" @click="gameStore.forward()" :disabled="gameStore.isAtLatest" title="Forward"><ChevronRight :size="16" /></button>
      <button class="nav-btn" @click="gameStore.goToMove(gameStore.boardStates.length - 1)" :disabled="gameStore.isAtLatest" title="End"><ChevronsRight :size="16" /></button>
    </div>

    <!-- Game action buttons -->
    <div class="button-row">
      <button class="btn-new-game" @click="emit('new-game')" title="New Game"><SquarePlus :size="15" /> New Game</button>
      <button v-if="gameStore.gameMode === 'hvc'" class="btn-run" @click="gameStore.setGameMode('cvc')" title="Run"><Play :size="15" /> Run</button>
      <button v-if="gameStore.gameMode === 'cvc'" class="btn-pause" @click="gameStore.togglePause()">
        <template v-if="gameStore.paused"><Play :size="15" /> Continue</template>
        <template v-else><Pause :size="15" /> Pause</template>
      </button>
    </div>

    <!-- Undo / Redo / Flip -->
    <div class="tool-row">
      <button class="tool-btn" @click="gameStore.undo()" :disabled="!gameStore.canUndo" title="Undo"><Undo2 :size="16" /></button>
      <button class="tool-btn" @click="gameStore.redo()" :disabled="!gameStore.canRedo" title="Redo"><Redo2 :size="16" /></button>
      <button class="tool-btn" @click="gameStore.flipBoard()" title="Flip Board"><ArrowUpDown :size="16" /></button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useGameStore } from '../../stores/game'
import {
  ChevronsLeft, ChevronLeft, ChevronRight, ChevronsRight,
  SquarePlus, Play, Pause, Undo2, Redo2, ArrowUpDown,
  Copy, Check
} from 'lucide-vue-next'

const emit = defineEmits<{ 'new-game': [] }>()

const gameStore = useGameStore()
const pgnScrollRef = ref<HTMLElement | null>(null)

const copySuccess = ref(false)
const copyPgnLabel = ref('Copy PGN')

const statusClass = computed(() => {
  if (gameStore.gameOverByTimeout) return 'status-checkmate'
  if (gameStore.isCheckmate) return 'status-checkmate'
  if (gameStore.isStalemate || gameStore.isDraw) return 'status-draw'
  if (gameStore.isCheck) return 'status-check'
  if (gameStore.paused) return 'status-paused'
  return ''
})

function copyPgn() {
  navigator.clipboard.writeText(gameStore.pgnText).then(() => {
    copySuccess.value = true
    copyPgnLabel.value = 'Copied!'
    setTimeout(() => { copySuccess.value = false; copyPgnLabel.value = 'Copy PGN' }, 1500)
  })
}

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
  height: 100%;
  overflow: hidden;
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

/* PGN section */
.pgn-section {
  padding: 0 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 4px;
  flex-shrink: 0;
}
.section-title { font-weight: 600; font-size: 13px; color: #555; }
.copy-btn {
  font-size: 13px;
  padding: 3px 6px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fafafa;
  cursor: pointer;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.copy-btn:hover { background: #eee; }

.pgn-scroll {
  font-family: monospace;
  font-size: 13px;
  line-height: 1.3;
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 6px;
  min-height: 120px;
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

/* Navigation row */
.nav-row {
  display: flex;
  justify-content: center;
  gap: 4px;
  padding: 6px 12px;
  flex-shrink: 0;
}
.nav-btn {
  width: 40px;
  height: 30px;
  font-size: 14px;
  border: 1px solid #ccc;
  border-radius: 5px;
  background: #fafafa;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-btn:hover:not(:disabled) { background: #e8e8e8; }
.nav-btn:disabled { opacity: 0.35; cursor: default; }

/* Game action buttons */
.button-row {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 8px 12px 4px;
  flex-shrink: 0;
}
.btn-new-game {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #f1c40f;
  color: #333;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.btn-new-game:hover { background: #d4ac0d; transform: scale(1.02); }

.btn-run {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #27ae60;
  color: white;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.btn-run:hover { background: #219a52; transform: scale(1.02); }

.btn-pause {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: #e67e22;
  color: white;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.btn-pause:hover { background: #cf6d17; transform: scale(1.02); }

/* Tool row (Undo / Redo / Flip) */
.tool-row {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 4px 12px 10px;
  flex-shrink: 0;
}
.tool-btn {
  width: 36px;
  height: 30px;
  font-size: 16px;
  border: 1px solid #ccc;
  border-radius: 5px;
  background: #fafafa;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.tool-btn:hover:not(:disabled) { background: #e8e8e8; }
.tool-btn:disabled { opacity: 0.35; cursor: default; }
</style>
