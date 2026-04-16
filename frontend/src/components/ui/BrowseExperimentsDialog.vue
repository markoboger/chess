<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay" @click.self="close">
        <div class="dialog browse-dialog">
          <div class="dialog-header">
            <h2 class="dialog-title">Browse experiment games</h2>
            <p class="dialog-sub">
              Load a finished match-runner game by replaying its PGN into a new session (same idea as the desktop app).
            </p>
          </div>

          <div class="browse-columns">
            <div class="browse-col">
              <div class="col-head">
                <span class="col-label">Experiments</span>
                <button type="button" class="refresh-btn" :disabled="loadingExps" @click="loadExperiments">
                  ↻ Refresh
                </button>
              </div>
              <ul class="browse-list" aria-label="Experiments">
                <li
                  v-for="(e, i) in experiments"
                  :key="e.id"
                  :class="{ selected: selectedExpIndex === i }"
                  @click="selectExperiment(i)"
                >
                  {{ e.name }} <span class="meta">[{{ e.status }}] ({{ e.requestedGames }} games)</span>
                </li>
                <li v-if="!loadingExps && experiments.length === 0" class="empty">No experiments</li>
              </ul>
            </div>
            <div class="browse-col">
              <div class="col-head">
                <span class="col-label">Games</span>
              </div>
              <ul class="browse-list" aria-label="Games in experiment">
                <li
                  v-for="(r, i) in runs"
                  :key="r.id"
                  :class="{ selected: selectedRunIndex === i }"
                  @click="selectedRunIndex = i"
                >
                  {{ r.moveCount ?? '?' }} moves
                  <span class="meta">{{ r.result || '?' }} · {{ r.winner || '—' }} · {{ shortId(r.chessGameId) }}</span>
                </li>
                <li v-if="selectedExpIndex >= 0 && !loadingRuns && runs.length === 0" class="empty">No runs</li>
              </ul>
            </div>
          </div>

          <p class="status-line">{{ statusText }}</p>

          <div v-if="localError" class="error-bar">{{ localError }}</div>

          <div class="dialog-actions">
            <button type="button" class="btn-cancel" @click="close">Cancel</button>
            <button
              type="button"
              class="btn-replay"
              :disabled="replaying || selectedRunIndex < 0 || !selectedRun?.pgn?.trim()"
              @click="doReplay"
            >
              {{ replaying ? 'Replaying…' : 'Replay game' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useGameStore } from '../../stores/game'
import { usePuzzleStore } from '../../stores/puzzle'
import { matchrunnerApi, type MrExperiment, type MrMatchRun } from '../../api/matchrunner-api'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; replayed: [] }>()

const gameStore = useGameStore()
const puzzleStore = usePuzzleStore()

const experiments = ref<MrExperiment[]>([])
const runs = ref<MrMatchRun[]>([])
const selectedExpIndex = ref(-1)
const selectedRunIndex = ref(-1)
const loadingExps = ref(false)
const loadingRuns = ref(false)
const statusText = ref('Select an experiment.')
const localError = ref<string | null>(null)
const replaying = ref(false)

const selectedRun = computed(() =>
  selectedRunIndex.value >= 0 && selectedRunIndex.value < runs.value.length
    ? runs.value[selectedRunIndex.value]
    : null
)

function shortId(id: string) {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id
}

async function loadExperiments() {
  loadingExps.value = true
  localError.value = null
  statusText.value = 'Loading experiments…'
  selectedExpIndex.value = -1
  selectedRunIndex.value = -1
  runs.value = []
  try {
    experiments.value = await matchrunnerApi.listExperiments()
    statusText.value =
      experiments.value.length === 0
        ? 'No experiments found.'
        : `${experiments.value.length} experiment(s). Select one to load games.`
  } catch {
    experiments.value = []
    localError.value =
      'Could not reach the match runner. Start it on port 8084 or set VITE_MATCH_RUNNER_URL.'
    statusText.value = 'Connection failed.'
  } finally {
    loadingExps.value = false
  }
}

async function loadRunsFor(expId: string) {
  loadingRuns.value = true
  selectedRunIndex.value = -1
  runs.value = []
  statusText.value = 'Loading games…'
  try {
    runs.value = await matchrunnerApi.listRuns(expId)
    statusText.value =
      runs.value.length === 0
        ? 'No runs for this experiment.'
        : `${runs.value.length} game(s) — pick one to replay.`
  } catch {
    runs.value = []
    localError.value = 'Could not load runs for this experiment.'
    statusText.value = 'Failed to load runs.'
  } finally {
    loadingRuns.value = false
  }
}

function selectExperiment(index: number) {
  selectedExpIndex.value = index
  const e = experiments.value[index]
  if (e) void loadRunsFor(e.id)
}

async function doReplay() {
  const r = selectedRun.value
  if (!r?.pgn?.trim()) {
    localError.value = 'No PGN stored for this game.'
    return
  }
  replaying.value = true
  localError.value = null
  const ok = await gameStore.replayBrowsedExperimentPgn(r.pgn)
  replaying.value = false
  if (ok) {
    puzzleStore.reset()
    emit('replayed')
    emit('close')
  } else {
    localError.value = gameStore.error ?? 'Replay failed.'
  }
}

function close() {
  emit('close')
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      localError.value = null
      selectedExpIndex.value = -1
      selectedRunIndex.value = -1
      runs.value = []
      void loadExperiments()
    }
  }
)
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-dialog-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.browse-dialog {
  background: var(--color-dialog-bg);
  color: var(--color-text);
  border-radius: 14px;
  width: 700px;
  max-width: 100%;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid var(--color-border);
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.dialog-header {
  padding: 20px 22px 12px;
  border-bottom: 1px solid var(--color-border);
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-panel-text);
}

.dialog-sub {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.browse-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 12px 16px;
  min-height: 280px;
  max-height: min(48vh, 400px);
}

@media (max-width: 640px) {
  .browse-columns {
    grid-template-columns: 1fr;
    max-height: none;
  }
}

.browse-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.col-label {
  font-weight: 700;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.refresh-btn {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--color-border-strong);
  background: var(--color-control-bg);
  color: var(--color-text);
  cursor: pointer;
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.browse-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-border-input);
  border-radius: 8px;
  overflow-y: auto;
  flex: 1;
  background: var(--color-session-row-bg);
  font-size: 12px;
}

.browse-list li {
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-panel-sep);
  line-height: 1.35;
  color: var(--color-panel-text);
}
.browse-list li:last-child {
  border-bottom: none;
}
.browse-list li:hover {
  background: var(--color-panel-hover);
}
.browse-list li.selected {
  background: var(--color-pgn-active-bg);
  color: var(--color-pgn-active-text);
  font-weight: 600;
}
.browse-list li.empty {
  cursor: default;
  color: var(--color-text-muted);
  font-style: italic;
  font-weight: 400;
}

.meta {
  display: block;
  font-weight: 400;
  color: var(--color-text-secondary);
  font-size: 11px;
  margin-top: 2px;
}

.status-line {
  margin: 0;
  padding: 0 20px 8px;
  font-size: 11px;
  color: var(--color-text-muted);
}

.error-bar {
  margin: 0 16px 12px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-err-text);
  background: var(--color-err-bg);
  border-radius: 8px;
  border: 1px solid var(--color-err-border);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px 18px;
  border-top: 1px solid var(--color-border);
}

.btn-cancel {
  padding: 8px 18px;
  border-radius: 8px;
  border: 1px solid var(--color-border-strong);
  background: var(--color-card-bg);
  color: var(--color-text);
  cursor: pointer;
  font-size: 14px;
}

.btn-replay {
  padding: 8px 18px;
  border-radius: 8px;
  border: none;
  background: var(--color-accent-strong);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}
.btn-replay:disabled {
  opacity: 0.45;
  cursor: default;
}
</style>
