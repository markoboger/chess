<template>
  <div class="puzzle-panel">
    <!-- Header -->
    <div class="panel-header">
      <span class="panel-title">
        <Puzzle :size="16" :stroke-width="2" class="panel-title-ico" aria-hidden="true" />
        Puzzle Practice
      </span>
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
import { Puzzle } from 'lucide-vue-next'
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
  border-bottom: 1px solid var(--color-border);
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 14px;
  color: var(--color-text);
}

.panel-title-ico {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}

.load-btn {
  font-size: 12px;
  padding: 5px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 6px;
  background: var(--color-control-bg);
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all 0.12s;
}
.load-btn:hover:not(:disabled) { background: var(--color-control-hover2); }
.load-btn:disabled { opacity: 0.5; cursor: default; }

/* Error / empty */
.panel-error {
  padding: 12px 16px;
  background: var(--color-err-bg);
  color: var(--color-err-text);
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
  color: var(--color-text-secondary);
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
  background: var(--color-puzzle-rating-bg);
  color: var(--color-puzzle-rating-text);
}

.meta-chip.theme {
  background: var(--color-puzzle-chip-theme-bg);
  color: var(--color-puzzle-theme-chip-text);
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

.status-banner.solved   { background: var(--color-puzzle-solved-bg); color: var(--color-puzzle-solved-text); }
.status-banner.failed   { background: var(--color-puzzle-fail-bg); color: var(--color-puzzle-fail-text); }
.status-banner.thinking { background: var(--color-puzzle-think-bg); color: var(--color-puzzle-think-text); }

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
  background: var(--color-progress-dot);
  transition: background 0.2s;
}

.progress-dot.done    { background: var(--color-accent); }
.progress-dot.current { background: var(--color-progress-current); box-shadow: 0 0 0 2px rgba(181,136,99,0.35); }

/* Actions */
.action-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px 0;
  flex-wrap: wrap;
}

.btn-primary {
  padding: 8px 16px;
  background: var(--color-accent-strong);
  color: #fff;
  border: none;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
  flex: 1;
}
.btn-primary:hover { background: var(--color-accent-hover); }

.btn-secondary {
  padding: 8px 14px;
  background: var(--color-btn-secondary-bg);
  color: var(--color-text);
  border: none;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.btn-secondary:hover { background: var(--color-btn-secondary-hover); }

/* Solution box */
.solution-box {
  margin: 12px 16px 0;
  padding: 10px 12px;
  background: var(--color-solution-box-bg);
  border: 1px solid var(--color-solution-box-border);
  border-radius: 8px;
}

.solution-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.solution-moves {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  color: var(--color-text);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: baseline;
}

.sol-sep {
  color: var(--color-text-muted);
  font-size: 12px;
}

.sol-move {
  background: var(--color-solution-move-bg);
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}
</style>
