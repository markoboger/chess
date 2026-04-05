<template>
  <header class="navbar">
    <div class="navbar-inner">
      <!-- Brand -->
      <a class="brand" @click.prevent="emit('new-game')">
        <span class="brand-icon">♛</span>
        <span class="brand-name">Chess</span>
      </a>

      <!-- Desktop nav items -->
      <nav class="desktop-nav">
        <!-- Game menu -->
        <div class="nav-item" :class="{ open: openMenu === 'game' }">
          <button class="nav-btn" @click.stop="toggle('game')">
            Game <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'game'">
            <div class="dropdown-group">Mode</div>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'hvh' }" @click="setMode('hvh')">
              <span class="dd-check">{{ gameStore.gameMode === 'hvh' ? '●' : '' }}</span> Human vs Human
            </button>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'hvc' }" @click="setMode('hvc')">
              <span class="dd-check">{{ gameStore.gameMode === 'hvc' ? '●' : '' }}</span> Human vs Computer
            </button>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'cvc' }" @click="setMode('cvc')">
              <span class="dd-check">{{ gameStore.gameMode === 'cvc' ? '●' : '' }}</span> Computer vs Computer
            </button>
          </div>
        </div>

        <!-- Clock menu -->
        <div class="nav-item" :class="{ open: openMenu === 'clock' }">
          <button class="nav-btn" @click.stop="toggle('clock')">
            Clock <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'clock'">
            <div class="dropdown-group">Time Control</div>
            <button
              v-for="p in CLOCK_PRESETS"
              :key="p.label"
              class="dd-item"
              :class="{ active: isActivePreset(p.mode) }"
              @click="setClock(p.mode)"
            >
              <span class="dd-check">{{ isActivePreset(p.mode) ? '●' : '' }}</span> {{ p.label }}
            </button>
          </div>
        </div>

        <!-- Strategy menu -->
        <div class="nav-item" :class="{ open: openMenu === 'strategy' }">
          <button class="nav-btn" @click.stop="toggle('strategy')">
            {{ strategyButtonLabel }} <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'strategy'">
            <div class="dropdown-group">White</div>
            <button
              v-for="strategy in COMPUTER_STRATEGIES"
              :key="'w-' + strategy.id"
              class="dd-item"
              :class="{ active: gameStore.whiteComputerStrategy === strategy.id }"
              @click="setStrategy('white', strategy.id)"
            >
              <span class="dd-check">{{ gameStore.whiteComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
            </button>
            <div class="dd-sep"></div>
            <div class="dropdown-group">Black</div>
            <button
              v-for="strategy in COMPUTER_STRATEGIES"
              :key="'b-' + strategy.id"
              class="dd-item"
              :class="{ active: gameStore.blackComputerStrategy === strategy.id }"
              @click="setStrategy('black', strategy.id)"
            >
              <span class="dd-check">{{ gameStore.blackComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
            </button>
          </div>
        </div>

        <!-- View menu -->
        <div class="nav-item" :class="{ open: openMenu === 'view' }">
          <button class="nav-btn" @click.stop="toggle('view')">
            View <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'view'">
            <button class="dd-item" @click="toggleLegal">
              <span class="dd-check">{{ gameStore.showLegalMoves ? '✓' : '' }}</span> Show Legal Moves
            </button>
          </div>
        </div>

        <!-- File menu -->
        <div class="nav-item" :class="{ open: openMenu === 'file' }">
          <button class="nav-btn" @click.stop="toggle('file')">
            File <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'file'">
            <div class="dropdown-group">Import</div>
            <button class="dd-item" @click="openImport('fen')">📥 Paste FEN</button>
            <button class="dd-item" @click="openImport('pgn')">📥 Paste PGN</button>
            <div class="dd-sep"></div>
            <div class="dropdown-group">Export</div>
            <button class="dd-item" @click="copyFen">📋 Copy FEN</button>
            <button class="dd-item" @click="copyPgn">📋 Copy PGN</button>
          </div>
        </div>

        <!-- Puzzles button -->
        <button class="nav-btn nav-btn-puzzles" :class="{ 'nav-btn-active': activeView === 'puzzles' }" @click="emit('toggle-puzzles')">
          🧩 Puzzles
        </button>
      </nav>

      <!-- Right side: theme toggle -->
      <div class="navbar-right">
        <button class="icon-btn" :title="uiStore.theme === 'dark' ? 'Light mode' : 'Dark mode'" @click="uiStore.toggleTheme()">
          {{ uiStore.theme === 'dark' ? '☀' : '🌙' }}
        </button>
        <!-- Burger button (mobile) -->
        <button class="burger-btn" :class="{ open: mobileOpen }" @click="mobileOpen = !mobileOpen" aria-label="Menu">
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>

    <!-- Mobile drawer -->
    <Transition name="drawer">
      <div v-if="mobileOpen" class="mobile-drawer" @click.self="mobileOpen = false">
        <div class="drawer-section">
          <div class="drawer-label">Game Mode</div>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'hvh' }" @click="setMode('hvh'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'hvh' ? '●' : '' }}</span> Human vs Human
          </button>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'hvc' }" @click="setMode('hvc'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'hvc' ? '●' : '' }}</span> Human vs Computer
          </button>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'cvc' }" @click="setMode('cvc'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'cvc' ? '●' : '' }}</span> Computer vs Computer
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Clock</div>
          <button
            v-for="p in CLOCK_PRESETS"
            :key="p.label"
            class="drawer-item"
            :class="{ active: isActivePreset(p.mode) }"
            @click="setClock(p.mode); mobileOpen=false"
          >
            <span class="dd-check">{{ isActivePreset(p.mode) ? '●' : '' }}</span> {{ p.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">White Strategy</div>
          <button
            v-for="strategy in COMPUTER_STRATEGIES"
            :key="'mw-' + strategy.id"
            class="drawer-item"
            :class="{ active: gameStore.whiteComputerStrategy === strategy.id }"
            @click="setStrategy('white', strategy.id); mobileOpen=false"
          >
            <span class="dd-check">{{ gameStore.whiteComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Black Strategy</div>
          <button
            v-for="strategy in COMPUTER_STRATEGIES"
            :key="'mb-' + strategy.id"
            class="drawer-item"
            :class="{ active: gameStore.blackComputerStrategy === strategy.id }"
            @click="setStrategy('black', strategy.id); mobileOpen=false"
          >
            <span class="dd-check">{{ gameStore.blackComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">View</div>
          <button class="drawer-item" @click="toggleLegal; mobileOpen=false">
            <span class="dd-check">{{ gameStore.showLegalMoves ? '✓' : '' }}</span> Show Legal Moves
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">File</div>
          <button class="drawer-item" @click="openImport('fen'); mobileOpen=false">📥 Paste FEN</button>
          <button class="drawer-item" @click="openImport('pgn'); mobileOpen=false">📥 Paste PGN</button>
          <button class="drawer-item" @click="copyFen; mobileOpen=false">📋 Copy FEN</button>
          <button class="drawer-item" @click="copyPgn; mobileOpen=false">📋 Copy PGN</button>
        </div>

        <div class="drawer-section">
          <button class="drawer-item puzzles-item" @click="emit('toggle-puzzles'); mobileOpen=false">
            🧩 Puzzles
          </button>
        </div>
      </div>
    </Transition>
  </header>

  <!-- Import dialog (shared between desktop/mobile) -->
  <Teleport to="body">
    <div v-if="showImport" class="dialog-overlay" @click.self="showImport = false">
      <div class="dialog">
        <div class="dialog-title">{{ importType === 'fen' ? 'Paste FEN' : 'Paste PGN' }}</div>
        <textarea
          v-model="importText"
          class="dialog-textarea"
          :placeholder="importType === 'fen' ? 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1' : '1. e4 e5 2. Nf3 Nc6 ...'"
          rows="5"
          autofocus
        ></textarea>
        <div class="dialog-row">
          <button class="dialog-btn cancel" @click="showImport = false">Cancel</button>
          <button class="dialog-btn ok" @click="doImport">Import</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  useGameStore,
  CLOCK_PRESETS,
  COMPUTER_STRATEGIES,
  type ClockMode,
  type GameMode,
  type ComputerSide,
  type ComputerStrategyId,
} from '../../stores/game'
import { useUIStore } from '../../stores/ui'

const props = defineProps<{ activeView?: 'game' | 'puzzles' }>()
const emit = defineEmits<{ 'new-game': []; 'toggle-puzzles': [] }>()

const gameStore = useGameStore()
const uiStore = useUIStore()

const openMenu = ref<string | null>(null)
const mobileOpen = ref(false)

function strategyLabel(id: ComputerStrategyId): string {
  return COMPUTER_STRATEGIES.find(s => s.id === id)?.label ?? id
}

const strategyButtonLabel = computed(() => {
  const mode = gameStore.gameMode
  if (mode === 'hvh') return 'Strategy'
  if (mode === 'hvc') return `Strategy: ${strategyLabel(gameStore.blackComputerStrategy)}`
  // cvc: show both if different, else just the one
  const w = strategyLabel(gameStore.whiteComputerStrategy)
  const b = strategyLabel(gameStore.blackComputerStrategy)
  return w === b ? `Strategy: ${w}` : `Strategy: ${w} / ${b}`
})

function toggle(menu: string) {
  openMenu.value = openMenu.value === menu ? null : menu
}

function closeAll() {
  openMenu.value = null
}

function handleOutsideClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.nav-item') && !target.closest('.dropdown')) {
    closeAll()
  }
}

onMounted(() => document.addEventListener('click', handleOutsideClick))
onUnmounted(() => document.removeEventListener('click', handleOutsideClick))

function setMode(mode: GameMode) {
  gameStore.setGameMode(mode)
  closeAll()
}

function isActivePreset(mode: ClockMode): boolean {
  if (mode.kind === 'none' && gameStore.clockMode.kind === 'none') return true
  if (mode.kind === 'timed' && gameStore.clockMode.kind === 'timed') {
    return mode.initialMs === gameStore.clockMode.initialMs && mode.incrementMs === gameStore.clockMode.incrementMs
  }
  return false
}

function setClock(mode: ClockMode) {
  gameStore.setClockMode(mode)
  closeAll()
}

function setStrategy(side: ComputerSide, strategy: ComputerStrategyId) {
  gameStore.setComputerStrategy(side, strategy)
  closeAll()
}

function toggleLegal() {
  gameStore.showLegalMoves = !gameStore.showLegalMoves
  closeAll()
}

// Import / Export
const showImport = ref(false)
const importType = ref<'fen' | 'pgn'>('fen')
const importText = ref('')

function openImport(type: 'fen' | 'pgn') {
  importType.value = type
  importText.value = ''
  showImport.value = true
  closeAll()
}

function doImport() {
  if (!importText.value.trim()) return
  importType.value === 'fen'
    ? gameStore.loadFenString(importText.value)
    : gameStore.loadPgnString(importText.value)
  showImport.value = false
}

function copyFen() {
  navigator.clipboard.writeText(gameStore.fen)
  closeAll()
}

function copyPgn() {
  navigator.clipboard.writeText(gameStore.pgnText)
  closeAll()
}
</script>

<style scoped>
/* ── Navbar shell ─────────────────────────────────────────────────── */
.navbar {
  background: #312e2b;
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.35);
}

.navbar-inner {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 16px;
  height: 52px;
  max-width: 1400px;
  margin: 0 auto;
}

/* ── Brand ─────────────────────────────────────────────────────────── */
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  cursor: pointer;
  padding: 0 12px 0 0;
  margin-right: 8px;
  border-right: 1px solid rgba(255,255,255,0.12);
  flex-shrink: 0;
}

.brand-icon {
  font-size: 22px;
  line-height: 1;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.3px;
}

/* ── Desktop nav ───────────────────────────────────────────────────── */
.desktop-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.nav-item {
  position: relative;
}

.nav-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.85);
  font-size: 14px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
}

.nav-btn:hover,
.nav-item.open .nav-btn {
  background: rgba(255,255,255,0.12);
  color: #fff;
}

.nav-btn-active {
  background: rgba(98,153,36,0.3) !important;
  color: #a8d96b !important;
}

.nav-btn-puzzles {
  margin-left: 4px;
}

.chevron {
  font-size: 10px;
  opacity: 0.7;
}

/* ── Dropdown ──────────────────────────────────────────────────────── */
.dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 210px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.18);
  padding: 6px 0;
  z-index: 200;
  animation: fadeInDown 0.1s ease;
}

@keyframes fadeInDown {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.dropdown-group {
  padding: 4px 14px 2px;
  font-size: 11px;
  font-weight: 700;
  color: #aaa;
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.dd-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 14px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13.5px;
  text-align: left;
  color: #222;
  transition: background 0.08s;
}

.dd-item:hover { background: #f0f4ff; }
.dd-item.active { font-weight: 700; color: #2d5a1b; }

.dd-check {
  width: 16px;
  text-align: center;
  font-size: 10px;
  color: #629924;
  flex-shrink: 0;
}

.dd-sep {
  height: 1px;
  background: #eee;
  margin: 4px 0;
}

/* ── Right side ────────────────────────────────────────────────────── */
.navbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.icon-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.8);
  font-size: 18px;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  line-height: 1;
  transition: background 0.12s;
}

.icon-btn:hover { background: rgba(255,255,255,0.12); }

/* ── Burger button ─────────────────────────────────────────────────── */
.burger-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5px;
  width: 36px;
  height: 36px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.12s;
}

.burger-btn span {
  display: block;
  width: 20px;
  height: 2px;
  background: rgba(255,255,255,0.85);
  border-radius: 2px;
  transition: all 0.25s ease;
  transform-origin: center;
}

.burger-btn:hover { background: rgba(255,255,255,0.12); }

.burger-btn.open span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
.burger-btn.open span:nth-child(2) { opacity: 0; transform: scaleX(0); }
.burger-btn.open span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }

/* ── Mobile drawer ─────────────────────────────────────────────────── */
.mobile-drawer {
  background: #222;
  border-top: 1px solid rgba(255,255,255,0.08);
  padding: 8px 0 16px;
  overflow-y: auto;
  max-height: calc(100vh - 52px);
}

.drawer-section {
  border-bottom: 1px solid rgba(255,255,255,0.06);
  padding: 8px 0;
}

.drawer-section:last-child { border-bottom: none; }

.drawer-label {
  padding: 4px 20px 2px;
  font-size: 11px;
  font-weight: 700;
  color: rgba(255,255,255,0.4);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 20px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
  color: rgba(255,255,255,0.8);
  transition: background 0.08s;
}

.drawer-item:hover { background: rgba(255,255,255,0.07); }
.drawer-item.active { color: #a8d96b; font-weight: 600; }

.puzzles-item {
  color: #a8d96b;
  font-weight: 600;
}

/* ── Drawer transition ─────────────────────────────────────────────── */
.drawer-enter-active, .drawer-leave-active { transition: all 0.22s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; transform: translateY(-8px); }

/* ── Import dialog ─────────────────────────────────────────────────── */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 460px;
  max-width: 92vw;
  box-shadow: 0 16px 48px rgba(0,0,0,0.25);
}

.dialog-title {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 14px;
  color: #222;
}

.dialog-textarea {
  width: 100%;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  padding: 10px;
  border: 1.5px solid #ddd;
  border-radius: 6px;
  resize: vertical;
  outline: none;
  color: #333;
  line-height: 1.5;
}

.dialog-textarea:focus { border-color: #629924; }

.dialog-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.dialog-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.dialog-btn.cancel { background: #eee; color: #555; }
.dialog-btn.cancel:hover { background: #ddd; }
.dialog-btn.ok { background: #629924; color: #fff; }
.dialog-btn.ok:hover { background: #4e7a1b; }

/* ── Responsive: show burger, hide desktop nav ─────────────────────── */
@media (max-width: 768px) {
  .desktop-nav { display: none; }
  .burger-btn   { display: flex; }
}
</style>
