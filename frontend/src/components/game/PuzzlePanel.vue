<template>
  <div class="puzzle-panel">
    <!-- Header -->
    <div class="panel-header">
      <span class="panel-title">🧩 Puzzle Practice</span>
      <button class="load-btn" @click="handleLoadPuzzle()" :disabled="puzzleStore.loading">
        {{ puzzleStore.loading ? 'Loading…' : puzzleStore.puzzle ? '↺ Next' : 'Load Puzzle' }}
      </button>
    </div>

    <!-- Error -->
    <div v-if="puzzleStore.error" class="panel-error">
      {{ puzzleStore.error }}
    </div>

    <!-- No puzzle yet -->
    <div v-else-if="!puzzleStore.puzzle && !puzzleStore.loading" class="panel-empty">
      <p>Practice tactics from a curated set of puzzles.</p>
      <button class="btn-primary" @click="handleLoadPuzzle()">Load Random Puzzle</button>
    </div>

    <!-- Puzzle info -->
    <template v-if="puzzleStore.puzzle">
      <div class="puzzle-meta">
        <span class="meta-chip rating">⚡ {{ puzzleStore.puzzle.rating }}</span>
        <span v-for="t in puzzleStore.puzzle.themes.slice(0, 3)" :key="t" class="meta-chip theme">
          {{ formatTheme(t) }}
        </span>
      </div>

      <!-- Status banner -->
      <div v-if="puzzleStore.solved" class="status-banner solved">
        ✓ Solved! Well done.
      </div>
      <div v-else-if="puzzleStore.failed" class="status-banner failed">
        ✗ Wrong move. Try again or see the solution.
      </div>
      <div v-else class="status-banner thinking">
        Find the best move — you are {{ turnColor }}
      </div>

      <!-- Progress dots -->
      <div class="progress-row">
        <div
          v-for="i in puzzleStore.moveCount"
          :key="i"
          class="progress-dot"
          :class="{
            done: i <= puzzleStore.solutionStep,
            current: i === puzzleStore.solutionStep + 1 && !puzzleStore.solved
          }"
        ></div>
      </div>

      <!-- Actions -->
      <div class="action-row">
        <button v-if="puzzleStore.failed" class="btn-secondary" @click="handleRetry()">↺ Retry</button>
        <button v-if="puzzleStore.failed || puzzleStore.solved" class="btn-secondary" @click="showSolution = !showSolution">
          {{ showSolution ? 'Hide' : 'Show' }} Solution
        </button>
        <button class="btn-primary" @click="handleLoadPuzzle()">↺ Next Puzzle</button>
      </div>

      <!-- Solution -->
      <div v-if="showSolution" class="solution-box">
        <div class="solution-label">Solution</div>
        <div class="solution-moves">
          <template v-for="(uci, i) in puzzleStore.puzzle.solution" :key="i">
            <span v-if="i % 2 === 0" class="sol-sep">{{ Math.floor(i / 2) + 1 }}.</span>
            <span class="sol-move">{{ uci }}</span>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { usePuzzleStore } from '../../stores/puzzle'
import { useGameStore } from '../../stores/game'

const emit = defineEmits<{ 'puzzle-loaded': [] }>()

const puzzleStore = usePuzzleStore()
const gameStore = useGameStore()
const showSolution = ref(false)

const turnColor = computed(() => {
  if (!puzzleStore.puzzle) return ''
  const fen = puzzleStore.puzzleFen
  return fen.split(' ')[1] === 'w' ? 'White' : 'Black'
})

function formatTheme(t: string): string {
  return t.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase()).trim()
}

// When a puzzle is loaded, sync its position to the game board and enable puzzle mode
watch(() => puzzleStore.active, (active) => {
  if (active) {
    gameStore.puzzleMode = true
    gameStore.loadFenString(puzzleStore.puzzleFen)
    emit('puzzle-loaded')
  } else {
    gameStore.puzzleMode = false
  }
})

// Intercept moves attempted on the board while in puzzle mode
watch(() => gameStore.puzzlePendingMove, (move) => {
  if (!move || !puzzleStore.active) {
    gameStore.puzzlePendingMove = null
    return
  }

  const uciMove = `${move.from}${move.to}${move.promotion ?? ''}`
  const { correct, solved } = puzzleStore.tryMove(uciMove)

  if (!correct) {
    // Wrong move — clear the pending move; board position unchanged
    gameStore.puzzlePendingMove = null
    return
  }

  // Correct move — sync player's move to the game store display
  gameStore.loadFenString(puzzleStore.puzzleFen)
  gameStore.puzzlePendingMove = null

  if (!solved) {
    // Auto-play opponent reply after a short delay
    setTimeout(() => {
      puzzleStore.applyOpponentReply()
      gameStore.loadFenString(puzzleStore.puzzleFen)
    }, 500)
  }
})

async function handleLoadPuzzle() {
  showSolution.value = false
  await puzzleStore.loadDailyPuzzle()
}

function handleRetry() {
  puzzleStore.retry()
  gameStore.loadFenString(puzzleStore.puzzleFen)
  showSolution.value = false
}
</script>

<style scoped>
.puzzle-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 100%;
}

/* Header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
}

.panel-title {
  font-weight: 700;
  font-size: 14px;
  color: #333;
}

.load-btn {
  font-size: 12px;
  padding: 5px 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  background: #fafafa;
  cursor: pointer;
  color: #555;
  transition: all 0.12s;
}
.load-btn:hover:not(:disabled) { background: #e8e8e8; }
.load-btn:disabled { opacity: 0.5; cursor: default; }

/* Error / empty */
.panel-error {
  padding: 12px 16px;
  background: #fde8e8;
  color: #c0392b;
  font-size: 13px;
}

.panel-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 32px 24px;
  text-align: center;
  color: #666;
  font-size: 14px;
}

/* Meta chips */
.puzzle-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 16px 0;
}

.meta-chip {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.meta-chip.rating {
  background: #fff3cd;
  color: #856404;
}

.meta-chip.theme {
  background: #e8f4ff;
  color: #0056b3;
}

/* Status banner */
.status-banner {
  margin: 10px 16px 0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
}

.status-banner.solved   { background: #d4edda; color: #155724; }
.status-banner.failed   { background: #fde8e8; color: #721c24; }
.status-banner.thinking { background: #e8f4ff; color: #004085; }

/* Progress dots */
.progress-row {
  display: flex;
  gap: 6px;
  padding: 10px 16px 0;
  justify-content: center;
}

.progress-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ddd;
  transition: background 0.2s;
}

.progress-dot.done    { background: #629924; }
.progress-dot.current { background: #b58863; box-shadow: 0 0 0 2px rgba(181,136,99,0.35); }

/* Actions */
.action-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px 0;
  flex-wrap: wrap;
}

.btn-primary {
  padding: 8px 16px;
  background: #629924;
  color: #fff;
  border: none;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
  flex: 1;
}
.btn-primary:hover { background: #4e7a1b; }

.btn-secondary {
  padding: 8px 14px;
  background: #f0f0f0;
  color: #333;
  border: none;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.btn-secondary:hover { background: #e0e0e0; }

/* Solution box */
.solution-box {
  margin: 12px 16px 0;
  padding: 10px 12px;
  background: #f9f9f9;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
}

.solution-label {
  font-size: 11px;
  font-weight: 700;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.solution-moves {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  color: #333;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: baseline;
}

.sol-sep {
  color: #999;
  font-size: 12px;
}

.sol-move {
  background: #ededf0;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}
</style>
