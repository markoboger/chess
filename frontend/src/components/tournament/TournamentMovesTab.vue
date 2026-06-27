<template>
  <div class="moves-tab">
    <template v-if="browse.selectedGame">
      <div class="moves-game-head">
        <div class="moves-title">
          R{{ browse.selectedGame.round }} · {{ browse.selectedGame.white.name }}
          <span class="vs">vs</span>
          {{ browse.selectedGame.black.name }}
        </div>
        <div v-if="resultLabel" class="moves-result" :class="resultClass">{{ resultLabel }}</div>
      </div>

      <div class="moves-nav">
        <button type="button" class="nav-btn" :disabled="gameStore.currentIndex === 0" @click="gameStore.goToMove(0)">
          ⏮
        </button>
        <button type="button" class="nav-btn" :disabled="gameStore.currentIndex === 0" @click="gameStore.backward()">
          ◀
        </button>
        <span class="nav-pos">{{ gameStore.currentIndex }} / {{ gameStore.boardStates.length - 1 }}</span>
        <button type="button" class="nav-btn" :disabled="gameStore.isAtLatest" @click="gameStore.forward()">
          ▶
        </button>
        <button
          type="button"
          class="nav-btn"
          :disabled="gameStore.isAtLatest"
          @click="gameStore.goToMove(gameStore.boardStates.length - 1)"
        >
          ⏭
        </button>
      </div>

      <div class="moves-section">
        <div class="section-label">Algebraic (SAN)</div>
        <div class="san-scroll">
          <span v-if="gameStore.pgnMoves.length === 0" class="empty-moves">No moves yet</span>
          <template v-for="(move, i) in gameStore.pgnMoves" :key="'san-' + i">
            <span v-if="i % 2 === 0" class="move-num">{{ Math.floor(i / 2) + 1 }}.</span>
            <button
              type="button"
              class="san-move"
              :class="{ active: i === gameStore.currentIndex - 1 }"
              @click="gameStore.goToMove(i + 1)"
            >
              {{ move }}
            </button>
          </template>
        </div>
      </div>

      <div class="moves-section">
        <div class="section-label row-label">
          UCI
          <button type="button" class="link-sm" @click="copyUci">Copy</button>
        </div>
        <div class="uci-scroll">{{ uciDisplay }}</div>
      </div>
    </template>

    <p v-else class="empty-hint">Select a game on the Tournament tab to view its move list.</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTournamentBrowseStore } from '../../stores/tournamentBrowse'
import { useGameStore } from '../../stores/game'

const browse = useTournamentBrowseStore()
const gameStore = useGameStore()
const copyOk = ref(false)

const resultLabel = computed(
  () => browse.selectedGameResult ?? browse.selectedGame?.outcomeLabel ?? null
)

const resultClass = computed(() => {
  const r = (resultLabel.value ?? '').toLowerCase()
  if (r.includes('time') || r.includes('won')) return 'result-decisive'
  if (r.includes('draw')) return 'result-draw'
  if (r.includes('progress') || r.includes('live')) return 'result-live'
  return ''
})

const uciDisplay = computed(() => {
  const uci = browse.selectedGameUci.trim()
  if (uci) return uci
  return browse.selectedGame?.moves?.trim() || '—'
})

async function copyUci() {
  const text = uciDisplay.value
  if (!text || text === '—') return
  await navigator.clipboard.writeText(text)
  copyOk.value = true
  setTimeout(() => {
    copyOk.value = false
  }, 1500)
}
</script>

<style scoped>
.moves-tab {
  padding: 4px 0 8px;
}

.moves-game-head {
  margin-bottom: 10px;
}

.moves-title {
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--color-panel-text);
}

.vs {
  font-weight: 500;
  color: var(--color-text-secondary);
  margin: 0 4px;
}

.moves-result {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
}

.moves-result.result-decisive {
  color: #166534;
}

.moves-result.result-draw {
  color: #165f8e;
}

.moves-result.result-live {
  color: #2d6a4f;
}

.moves-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
}

.nav-btn {
  border: 1px solid var(--color-border);
  background: var(--color-input-bg);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 12px;
}

.nav-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.nav-pos {
  flex: 1;
  text-align: center;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.moves-section {
  margin-bottom: 12px;
}

.section-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.row-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.link-sm {
  border: none;
  background: none;
  font-size: 10px;
  color: var(--color-accent-text);
  cursor: pointer;
  text-transform: none;
  font-weight: 600;
}

.san-scroll {
  max-height: 160px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-input-bg);
  font-size: 12px;
  line-height: 1.7;
}

.san-move {
  border: none;
  background: none;
  padding: 1px 4px;
  margin: 0 2px;
  border-radius: 4px;
  cursor: pointer;
  font: inherit;
  color: var(--color-panel-text);
}

.san-move:hover {
  background: var(--color-panel-hover);
}

.san-move.active {
  background: var(--color-accent-soft);
  font-weight: 700;
}

.move-num {
  color: var(--color-text-secondary);
  margin-right: 2px;
  font-weight: 600;
}

.uci-scroll {
  max-height: 100px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-muted-bg);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  line-height: 1.5;
  word-break: break-all;
  color: var(--color-text-secondary);
}

.empty-moves,
.empty-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.empty-hint {
  padding: 12px 4px;
}
</style>
