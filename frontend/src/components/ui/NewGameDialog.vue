<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div v-if="visible" class="dialog-overlay" @click.self="emit('close')">
        <div class="dialog" @click.stop>
          <!-- Tabs -->
          <div class="tabs">
            <button class="tab" :class="{ active: tab === 'new' }" @click="tab = 'new'">New Game</button>
            <button class="tab" :class="{ active: tab === 'join' }" @click="tab = 'join'">Join Game</button>
          </div>

          <!-- ── New Game tab ───────────────────────────────────────────── -->
          <div v-if="tab === 'new'" class="tab-body">

            <!-- Player setup -->
            <div class="section">
              <div class="section-label">Players</div>
              <div class="player-row">
                <span class="player-icon">♔</span>
                <span class="player-name">White</span>
                <div class="toggle-group">
                  <button class="toggle-btn" :class="{ active: whiteIsHuman }" @click="whiteIsHuman = true">Human</button>
                  <button class="toggle-btn" :class="{ active: !whiteIsHuman }" @click="whiteIsHuman = false">Computer</button>
                </div>
                <select v-if="!whiteIsHuman" v-model="whiteStrategy" class="strategy-select">
                  <option v-for="s in COMPUTER_STRATEGIES" :key="s.id" :value="s.id">{{ s.label }}</option>
                </select>
              </div>
              <div class="player-row">
                <span class="player-icon">♚</span>
                <span class="player-name">Black</span>
                <div class="toggle-group">
                  <button class="toggle-btn" :class="{ active: blackIsHuman }" @click="blackIsHuman = true">Human</button>
                  <button class="toggle-btn" :class="{ active: !blackIsHuman }" @click="blackIsHuman = false">Computer</button>
                </div>
                <select v-if="!blackIsHuman" v-model="blackStrategy" class="strategy-select">
                  <option v-for="s in COMPUTER_STRATEGIES" :key="s.id" :value="s.id">{{ s.label }}</option>
                </select>
              </div>
            </div>

            <!-- Time control -->
            <div class="section">
              <div class="section-label">Time Control</div>
              <div class="clock-grid">
                <button
                  v-for="p in CLOCK_PRESETS"
                  :key="p.label"
                  class="clock-btn"
                  :class="{ active: isClockActive(p.mode) }"
                  @click="selectedClock = p.mode"
                >{{ p.label }}</button>
              </div>
            </div>

            <!-- Play as (only relevant in HvH) -->
            <div v-if="whiteIsHuman && blackIsHuman" class="section">
              <div class="section-label">I play as</div>
              <div class="toggle-group play-as-group">
                <button class="toggle-btn" :class="{ active: playAs === 'white' }" @click="playAs = 'white'">♔ White</button>
                <button class="toggle-btn" :class="{ active: playAs === 'black' }" @click="playAs = 'black'">♚ Black</button>
              </div>
            </div>

            <!-- Starting position -->
            <div class="section">
              <div class="section-label">Starting Position <span class="optional">(optional FEN)</span></div>
              <input
                v-model="startFen"
                class="fen-input"
                placeholder="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
                spellcheck="false"
              />
            </div>

            <div v-if="newError" class="error-msg">{{ newError }}</div>

            <div class="dialog-actions">
              <button class="btn-cancel" @click="emit('close')">Cancel</button>
              <button class="btn-start" :disabled="creating" @click="startGame">
                {{ creating ? 'Creating…' : 'Start Game' }}
              </button>
            </div>
          </div>

          <!-- ── Join Game tab ──────────────────────────────────────────── -->
          <div v-if="tab === 'join'" class="tab-body">

            <!-- Active sessions list -->
            <div class="section">
              <div class="section-label">
                Active Sessions
                <button class="refresh-btn" @click="loadSessions" :disabled="loadingSessions" title="Refresh">
                  {{ loadingSessions ? '…' : '↻' }}
                </button>
                <button class="delete-all-btn" @click="deleteAllSessions" :disabled="deletingAll || sessions.length === 0" title="Delete all sessions">
                  {{ deletingAll ? '…' : 'Delete All' }}
                </button>
              </div>
              <div v-if="sessions.length === 0 && !loadingSessions" class="no-sessions">No active sessions found.</div>
              <div v-else class="session-list">
                <button
                  v-for="s in sessions"
                  :key="s.gameId"
                  class="session-row"
                  :class="{ selected: joinSessionId === s.gameId }"
                  @click="joinSessionId = s.gameId"
                >
                  <span class="session-mode">{{ sessionMode(s) }}</span>
                  <span class="session-status">{{ s.status }}</span>
                  <span class="session-id-short">{{ s.gameId.slice(0, 8) }}…</span>
                </button>
              </div>
            </div>

            <!-- Manual paste fallback -->
            <div class="section">
              <div class="section-label">Or paste Session ID</div>
              <input
                v-model="joinSessionId"
                class="fen-input"
                placeholder="Paste the session ID here"
                spellcheck="false"
                @keydown.enter="doJoin"
              />
            </div>

            <div v-if="joinError" class="error-msg">{{ joinError }}</div>

            <div class="dialog-actions">
              <button class="btn-cancel" @click="emit('close')">Cancel</button>
              <button class="btn-start" :disabled="joining || !joinSessionId.trim()" @click="doJoin">
                {{ joining ? 'Joining…' : 'Join Game' }}
              </button>
            </div>
          </div>

        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { COMPUTER_STRATEGIES, CLOCK_PRESETS, type ComputerStrategyId, type ClockMode, type PlayerColor } from '../../stores/game'
import { useGameStore } from '../../stores/game'
import { gameApi, type GameSummary } from '../../api/game-api'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; started: [] }>()

const gameStore = useGameStore()

// Tab
const tab = ref<'new' | 'join'>('new')

// New game settings
const whiteIsHuman = ref(true)
const blackIsHuman = ref(true)
const whiteStrategy = ref<ComputerStrategyId>('deepening-opening-endgame')
const blackStrategy = ref<ComputerStrategyId>('deepening-opening-endgame')
const selectedClock = ref<ClockMode>({ kind: 'none' })
const startFen = ref('')
const playAs = ref<PlayerColor>('white')

const creating = ref(false)
const newError = ref<string | null>(null)

// Join game
const joinSessionId = ref('')
const joining = ref(false)
const joinError = ref<string | null>(null)
const sessions = ref<GameSummary[]>([])
const loadingSessions = ref(false)
const deletingAll = ref(false)

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await gameApi.listGames()
  } catch {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

async function deleteAllSessions() {
  deletingAll.value = true
  try {
    await gameApi.deleteAllGames()
    sessions.value = []
    joinSessionId.value = ''
  } catch { /* ignore */ } finally {
    deletingAll.value = false
  }
}

function sessionMode(s: GameSummary): string {
  const w = s.settings?.whiteIsHuman ?? true
  const b = s.settings?.blackIsHuman ?? true
  if (w && b) return 'HvH'
  if (!w && !b) return 'CvC'
  return w ? 'H(W) v C' : 'C v H(B)'
}

// Reset state when dialog opens
watch(() => props.visible, (v) => {
  if (v) {
    tab.value = 'new'
    whiteIsHuman.value = true
    blackIsHuman.value = true
    whiteStrategy.value = gameStore.whiteComputerStrategy
    blackStrategy.value = gameStore.blackComputerStrategy
    selectedClock.value = gameStore.clockMode
    startFen.value = ''
    playAs.value = 'white'
    newError.value = null
    joinSessionId.value = ''
    joinError.value = null
    sessions.value = []
  }
})

watch(tab, (t) => {
  if (t === 'join') loadSessions()
})

function isClockActive(mode: ClockMode): boolean {
  if (mode.kind === 'none' && selectedClock.value.kind === 'none') return true
  if (mode.kind === 'timed' && selectedClock.value.kind === 'timed') {
    return mode.initialMs === selectedClock.value.initialMs && mode.incrementMs === selectedClock.value.incrementMs
  }
  return false
}

async function startGame() {
  creating.value = true
  newError.value = null

  const clock = selectedClock.value
  const settings = {
    whiteIsHuman: whiteIsHuman.value,
    blackIsHuman: blackIsHuman.value,
    whiteStrategy: whiteStrategy.value,
    blackStrategy: blackStrategy.value,
    clockInitialMs: clock.kind === 'timed' ? clock.initialMs : undefined,
    clockIncrementMs: clock.kind === 'timed' ? clock.incrementMs : undefined,
  }

  // Determine playAs: for HvH use dialog choice; for HvC derive from human side
  let resolvedPlayAs: PlayerColor
  if (whiteIsHuman.value && blackIsHuman.value) {
    resolvedPlayAs = playAs.value
  } else if (whiteIsHuman.value) {
    resolvedPlayAs = 'white'
  } else if (blackIsHuman.value) {
    resolvedPlayAs = 'black'
  } else {
    resolvedPlayAs = 'spectator'
  }

  await gameStore.createGame(startFen.value.trim() || undefined, settings, resolvedPlayAs)

  creating.value = false
  if (gameStore.error) {
    newError.value = gameStore.error
  } else {
    emit('started')
    emit('close')
  }
}

async function doJoin() {
  const id = joinSessionId.value.trim()
  if (!id) return
  joining.value = true
  joinError.value = null
  const ok = await gameStore.joinGame(id)
  joining.value = false
  if (ok) {
    emit('started')
    emit('close')
  } else {
    joinError.value = gameStore.error ?? 'Game not found.'
  }
}


</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-dialog-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: var(--color-dialog-bg);
  color: var(--color-text);
  border-radius: 14px;
  width: 480px;
  max-width: 95vw;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  border: 1px solid var(--color-border);
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

/* ── Tabs ───────────────────────────────────────────────────────────── */
.tabs {
  display: flex;
  border-bottom: 2px solid var(--color-dialog-title-border);
}

.tab {
  flex: 1;
  padding: 14px 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 0.15s, border-bottom 0.15s;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
}

.tab.active {
  color: var(--color-accent-text);
  border-bottom-color: var(--color-accent);
}

/* ── Tab body ───────────────────────────────────────────────────────── */
.tab-body {
  padding: 20px 24px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ── Sections ───────────────────────────────────────────────────────── */
.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-panel-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.optional {
  font-weight: 400;
  text-transform: none;
  color: var(--color-panel-muted2);
  letter-spacing: 0;
  font-size: 11px;
}

/* ── Player rows ────────────────────────────────────────────────────── */
.player-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.player-icon {
  font-size: 18px;
  width: 22px;
  text-align: center;
  flex-shrink: 0;
}

.player-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text);
  width: 44px;
  flex-shrink: 0;
}

.toggle-group {
  display: flex;
  border-radius: 6px;
  overflow: hidden;
  border: 1.5px solid var(--color-toggle-border);
}

.toggle-btn {
  padding: 5px 12px;
  border: none;
  background: var(--color-toggle-inactive-bg);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: background 0.12s, color 0.12s;
}

.toggle-btn + .toggle-btn {
  border-left: 1.5px solid var(--color-toggle-border);
}

.toggle-btn.active {
  background: var(--color-accent-strong);
  color: #fff;
  font-weight: 700;
}

.strategy-select {
  flex: 1;
  padding: 5px 8px;
  border: 1.5px solid var(--color-border-input);
  border-radius: 6px;
  font-size: 12.5px;
  color: var(--color-input-text);
  background: var(--color-input-bg);
  cursor: pointer;
  outline: none;
}

.strategy-select:focus { border-color: var(--color-accent); }

/* ── Clock grid ─────────────────────────────────────────────────────── */
.clock-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 6px;
}

.clock-btn {
  padding: 7px 6px;
  border: 1.5px solid var(--color-border-input);
  border-radius: 6px;
  background: var(--color-control-bg);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-text-secondary);
  text-align: center;
  transition: background 0.1s, border-color 0.1s, color 0.1s;
}

.clock-btn:hover { border-color: var(--color-accent); color: var(--color-accent-text); }
.clock-btn.active { background: var(--color-accent-strong); border-color: var(--color-accent-strong); color: #fff; font-weight: 700; }

/* ── Play as ────────────────────────────────────────────────────────── */
.play-as-group {
  align-self: flex-start;
}

/* ── FEN input ──────────────────────────────────────────────────────── */
.fen-input {
  width: 100%;
  padding: 8px 10px;
  border: 1.5px solid var(--color-border-input);
  border-radius: 6px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  color: var(--color-input-text);
  background: var(--color-input-bg);
  outline: none;
  transition: border-color 0.1s;
}

.fen-input:focus { border-color: var(--color-accent); }

/* ── Error ──────────────────────────────────────────────────────────── */
.error-msg {
  font-size: 12.5px;
  color: var(--color-err-text);
  padding: 6px 10px;
  background: var(--color-error-soft-bg);
  border-radius: 6px;
  border: 1px solid var(--color-error-soft-border);
}

/* ── Action buttons ─────────────────────────────────────────────────── */
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.btn-cancel {
  padding: 9px 20px;
  border: none;
  border-radius: 7px;
  background: var(--color-control-hover);
  color: var(--color-text-secondary);
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.1s;
}

.btn-cancel:hover { background: var(--color-control-hover2); }

.btn-start {
  padding: 9px 24px;
  border: none;
  border-radius: 7px;
  background: var(--color-accent-strong);
  color: #fff;
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.1s;
}

.btn-start:hover:not(:disabled) { background: var(--color-accent-hover); }
.btn-start:disabled { opacity: 0.55; cursor: default; }

/* ── Session list ───────────────────────────────────────────────────── */
.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-accent);
  padding: 0 4px;
  margin-left: 6px;
  line-height: 1;
  vertical-align: middle;
}
.refresh-btn:disabled { opacity: 0.5; cursor: default; }

.delete-all-btn {
  background: none;
  border: 1px solid #e74c3c;
  border-radius: 4px;
  cursor: pointer;
  font-size: 10px;
  font-weight: 600;
  color: #e74c3c;
  padding: 1px 6px;
  margin-left: 6px;
  vertical-align: middle;
  transition: background 0.1s, color 0.1s;
}
.delete-all-btn:hover:not(:disabled) { background: #c0392b; color: #fff; }
.delete-all-btn:disabled { opacity: 0.4; cursor: default; }

.no-sessions {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 8px 0;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 180px;
  overflow-y: auto;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1.5px solid var(--color-border-input);
  border-radius: 7px;
  background: var(--color-session-row-bg);
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  transition: border-color 0.1s, background 0.1s;
}
.session-row:hover { border-color: var(--color-accent); background: var(--color-session-row-hover-bg); }
.session-row.selected { border-color: var(--color-accent); background: var(--color-session-row-selected-bg); }

.session-mode {
  font-weight: 700;
  color: var(--color-text);
  min-width: 60px;
}
.session-status {
  flex: 1;
  color: var(--color-text-secondary);
}
.session-id-short {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 11px;
  color: var(--color-text-muted);
}

/* ── Transition ─────────────────────────────────────────────────────── */
.dialog-fade-enter-active, .dialog-fade-leave-active { transition: opacity 0.18s ease; }
.dialog-fade-enter-from, .dialog-fade-leave-to { opacity: 0; }
</style>
