<template>
  <div class="menu-bar">
    <!-- File menu -->
    <div class="menu-item" @click.stop="toggleMenu('file')" @mouseleave="closeMenuDelayed('file')">
      <span class="menu-label">File</span>
      <div v-if="openMenu === 'file'" class="menu-dropdown" @mouseenter="cancelClose()" @mouseleave="closeMenuDelayed('file')">
        <div class="menu-group-title">Import</div>
        <button class="menu-option" @click="importFen()">FEN</button>
        <button class="menu-option" @click="importPgn()">PGN</button>
        <div class="menu-sep"></div>
        <div class="menu-group-title">Export</div>
        <button class="menu-option" @click="exportFen()">FEN</button>
        <button class="menu-option" @click="exportPgn()">PGN</button>
      </div>
    </div>

    <!-- Game menu -->
    <div class="menu-item" @click.stop="toggleMenu('game')" @mouseleave="closeMenuDelayed('game')">
      <span class="menu-label">Game</span>
      <div v-if="openMenu === 'game'" class="menu-dropdown" @mouseenter="cancelClose()" @mouseleave="closeMenuDelayed('game')">
        <button class="menu-option" :class="{ active: gameStore.gameMode === 'hvh' }" @click="setMode('hvh')">
          <span class="check-mark">{{ gameStore.gameMode === 'hvh' ? '●' : '' }}</span> Human vs Human
        </button>
        <button class="menu-option" :class="{ active: gameStore.gameMode === 'hvc' }" @click="setMode('hvc')">
          <span class="check-mark">{{ gameStore.gameMode === 'hvc' ? '●' : '' }}</span> Human vs Computer
        </button>
        <button class="menu-option" :class="{ active: gameStore.gameMode === 'cvc' }" @click="setMode('cvc')">
          <span class="check-mark">{{ gameStore.gameMode === 'cvc' ? '●' : '' }}</span> Computer vs Computer
        </button>
      </div>
    </div>

    <!-- Clock menu -->
    <div class="menu-item" @click.stop="toggleMenu('clock')" @mouseleave="closeMenuDelayed('clock')">
      <span class="menu-label">Clock</span>
      <div v-if="openMenu === 'clock'" class="menu-dropdown" @mouseenter="cancelClose()" @mouseleave="closeMenuDelayed('clock')">
        <button
          v-for="(preset, idx) in CLOCK_PRESETS"
          :key="idx"
          class="menu-option"
          :class="{ active: isActiveClockPreset(preset.mode) }"
          @click="selectClock(preset.mode)"
        >
          <span class="check-mark">{{ isActiveClockPreset(preset.mode) ? '●' : '' }}</span> {{ preset.label }}
        </button>
      </div>
    </div>

    <!-- View menu -->
    <div class="menu-item" @click.stop="toggleMenu('view')" @mouseleave="closeMenuDelayed('view')">
      <span class="menu-label">View</span>
      <div v-if="openMenu === 'view'" class="menu-dropdown" @mouseenter="cancelClose()" @mouseleave="closeMenuDelayed('view')">
        <button class="menu-option" @click="toggleLegalMoves()">
          <span class="check-mark">{{ gameStore.showLegalMoves ? '✓' : '' }}</span> Show Legal Moves
        </button>
      </div>
    </div>
  </div>

  <!-- Import dialogs -->
  <div v-if="showImportDialog" class="dialog-overlay" @click.self="showImportDialog = false">
    <div class="dialog">
      <div class="dialog-title">Import {{ importType.toUpperCase() }}</div>
      <textarea
        v-model="importText"
        class="dialog-textarea"
        :placeholder="importType === 'fen' ? 'Paste FEN string...' : 'Paste PGN moves...'"
        rows="6"
      ></textarea>
      <div class="dialog-buttons">
        <button class="dialog-btn cancel" @click="showImportDialog = false">Cancel</button>
        <button class="dialog-btn ok" @click="doImport()">Import</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useGameStore, CLOCK_PRESETS, type ClockMode, type GameMode } from '../../stores/game'

const gameStore = useGameStore()

const openMenu = ref<string | null>(null)
let closeTimeout: ReturnType<typeof setTimeout> | null = null

function toggleMenu(menu: string) {
  openMenu.value = openMenu.value === menu ? null : menu
}

function closeMenuDelayed(menu: string) {
  closeTimeout = setTimeout(() => {
    if (openMenu.value === menu) openMenu.value = null
  }, 200)
}

function cancelClose() {
  if (closeTimeout) { clearTimeout(closeTimeout); closeTimeout = null }
}

// Game mode
function setMode(mode: GameMode) {
  gameStore.setGameMode(mode)
  openMenu.value = null
}

// Clock
function isActiveClockPreset(mode: ClockMode): boolean {
  if (mode.kind === 'none' && gameStore.clockMode.kind === 'none') return true
  if (mode.kind === 'timed' && gameStore.clockMode.kind === 'timed') {
    return mode.initialMs === gameStore.clockMode.initialMs && mode.incrementMs === gameStore.clockMode.incrementMs
  }
  return false
}

function selectClock(mode: ClockMode) {
  gameStore.setClockMode(mode)
  openMenu.value = null
}

// View
function toggleLegalMoves() {
  gameStore.showLegalMoves = !gameStore.showLegalMoves
  openMenu.value = null
}

// Import/Export
const showImportDialog = ref(false)
const importType = ref<'fen' | 'pgn'>('fen')
const importText = ref('')

function importFen() {
  importType.value = 'fen'
  importText.value = ''
  showImportDialog.value = true
  openMenu.value = null
}

function importPgn() {
  importType.value = 'pgn'
  importText.value = ''
  showImportDialog.value = true
  openMenu.value = null
}

function doImport() {
  if (!importText.value.trim()) return
  if (importType.value === 'fen') {
    gameStore.loadFenString(importText.value)
  } else {
    gameStore.loadPgnString(importText.value)
  }
  showImportDialog.value = false
}

function exportFen() {
  openMenu.value = null
  navigator.clipboard.writeText(gameStore.fen)
  gameStore.error = null
}

function exportPgn() {
  openMenu.value = null
  navigator.clipboard.writeText(gameStore.pgnText)
  gameStore.error = null
}
</script>

<style scoped>
.menu-bar {
  display: flex;
  background: #fafafa;
  border-bottom: 1px solid #ddd;
  padding: 0 8px;
  font-size: 13px;
  user-select: none;
}

.menu-item {
  position: relative;
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 4px;
}
.menu-item:hover { background: #e8e8e8; }

.menu-label { font-weight: 500; color: #333; }

.menu-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  min-width: 200px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  padding: 4px 0;
  z-index: 200;
}

.menu-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 14px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
  color: #333;
}
.menu-option:hover { background: #e8f0fe; }
.menu-option.active { font-weight: 600; }

.check-mark {
  width: 16px;
  text-align: center;
  font-size: 10px;
  color: #1565C0;
}

.menu-group-title {
  padding: 4px 14px 2px;
  font-size: 11px;
  font-weight: 700;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.menu-sep {
  height: 1px;
  background: #eee;
  margin: 4px 0;
}

/* Import dialog */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}
.dialog {
  background: white;
  border-radius: 12px;
  padding: 24px;
  width: 440px;
  max-width: 90vw;
  box-shadow: 0 12px 40px rgba(0,0,0,0.2);
}
.dialog-title {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 12px;
  color: #333;
}
.dialog-textarea {
  width: 100%;
  font-family: monospace;
  font-size: 12px;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 6px;
  resize: vertical;
  outline: none;
}
.dialog-textarea:focus { border-color: #1565C0; }
.dialog-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.dialog-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.dialog-btn.cancel { background: #eee; color: #555; }
.dialog-btn.cancel:hover { background: #ddd; }
.dialog-btn.ok { background: #1565C0; color: white; }
.dialog-btn.ok:hover { background: #0d47a1; }
</style>
