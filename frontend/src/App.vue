<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import NavBar from './components/layout/NavBar.vue'
import ChessBoard from './components/board/ChessBoard.vue'
import BoardInfoPanel from './components/controls/BoardInfoPanel.vue'
import GameControls from './components/controls/GameControls.vue'
import OpeningBadge from './components/game/OpeningBadge.vue'
import PuzzlePanel from './components/game/PuzzlePanel.vue'
import NewGameDialog from './components/ui/NewGameDialog.vue'
import { useGameStore } from './stores/game'
import { usePuzzleStore } from './stores/puzzle'
import { useOpeningStore } from './stores/opening'

const gameStore = useGameStore()
const puzzleStore = usePuzzleStore()
const openingStore = useOpeningStore()

const activeView = ref<'game' | 'puzzles'>('game')
const showNewGameDialog = ref(false)

function togglePuzzles() {
  activeView.value = activeView.value === 'puzzles' ? 'game' : 'puzzles'
  if (activeView.value === 'game') {
    puzzleStore.reset()
    gameStore.puzzleMode = false
    gameStore.resetGame()
  }
}

function handleNewGame() {
  puzzleStore.reset()
  gameStore.puzzleMode = false
  activeView.value = 'game'
  showNewGameDialog.value = true
}

function handleGameStarted() {
  showNewGameDialog.value = false
}

onMounted(async () => {
  // URL-based join: ?session=<id>
  const params = new URLSearchParams(globalThis.location?.search ?? '')
  const sessionParam = params.get('session')
  if (sessionParam) {
    await gameStore.joinGame(sessionParam.trim())
    // Clean the URL without a page reload
    globalThis.history?.replaceState({}, '', globalThis.location?.pathname ?? '')
  } else {
    await gameStore.createGame()
  }
  openingStore.init()   // pre-load opening map in background
  globalThis.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  gameStore.stopPolling()
  globalThis.removeEventListener('keydown', handleKeydown)
})

function handleKeydown(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'TEXTAREA' || tag === 'INPUT') return
  if (gameStore.puzzleMode) return  // disable nav keys during puzzle

  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    gameStore.backward()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    gameStore.forward()
  } else if (e.key === 'Home') {
    e.preventDefault()
    gameStore.goToMove(0)
  } else if (e.key === 'End') {
    e.preventDefault()
    gameStore.goToMove(gameStore.boardStates.length - 1)
  } else if (e.key === 'v' && (e.metaKey || e.ctrlKey)) {
    navigator.clipboard.readText().then((text: string) => {
      const trimmed = text.trim()
      if (trimmed) gameStore.loadPgnOrFen(trimmed)
    }).catch(() => {})
  }
}
</script>

<template>
  <div class="app-shell">
    <NavBar
      :active-view="activeView"
      @new-game="handleNewGame"
      @toggle-puzzles="togglePuzzles"
    />

    <NewGameDialog
      :visible="showNewGameDialog"
      @close="showNewGameDialog = false"
      @started="handleGameStarted"
    />

    <main class="app-main">
      <div class="game-row">
        <!-- Left: board + info -->
        <div class="left-card">
          <div class="board-area">
            <ChessBoard />
          </div>
          <BoardInfoPanel />
        </div>

        <!-- Right: sidebar -->
        <div class="sidebar-wrapper">
          <div class="sidebar-card">
            <Transition name="panel" mode="out-in">
              <PuzzlePanel v-if="activeView === 'puzzles'" key="puzzles" @puzzle-loaded="() => {}" />
              <GameControls v-else key="game" @new-game="handleNewGame" />
            </Transition>
          </div>
        </div>
      </div>

      <div class="opening-row">
        <OpeningBadge v-if="!puzzleStore.active" />
        <span v-else-if="puzzleStore.puzzle" class="puzzle-mode-badge">
          🧩 Puzzle mode · Find the best move
        </span>
      </div>
    </main>
  </div>
</template>

<style>
/* ── Global reset ──────────────────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #eeece8;
  color: #222;
  min-height: 100vh;
}

/* ── App shell ─────────────────────────────────────────────────────── */
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Main content ──────────────────────────────────────────────────── */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
}

/* ── Game row (board + sidebar, same height) ──────────────────── */
.game-row {
  display: grid;
  grid-template-columns: auto 320px;
}

.left-card {
  background: white;
  border-radius: 12px 0 0 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.10);
  overflow: hidden;
}

.board-area {
  padding: 16px;
}

.opening-row {
  padding-left: 4px;
  padding-top: 8px;
  min-height: 28px;
}

.puzzle-mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(181, 136, 99, 0.15);
  border: 1px solid rgba(181, 136, 99, 0.4);
  border-radius: 20px;
  font-size: 12.5px;
  font-weight: 600;
  color: #7a4e2d;
}

/* ── Sidebar ──────────────────────────────────────────────────────── */
.sidebar-wrapper {
  position: relative;
}

.sidebar-card {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  border-radius: 0 12px 12px 0;
  box-shadow: 0 4px 20px rgba(0,0,0,0.10);
  border-left: 1px solid #e0ddd8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── Panel transition ──────────────────────────────────────────────── */
.panel-enter-active, .panel-leave-active { transition: opacity 0.18s ease; }
.panel-enter-from, .panel-leave-to { opacity: 0; }

/* ── Responsive ────────────────────────────────────────────────────── */
@media (max-width: 900px) {
  .app-main {
    padding: 16px 12px;
  }

  .game-row {
    grid-template-columns: 1fr;
    gap: 16px;
    justify-items: center;
  }

  .left-card {
    border-radius: 12px;
  }

  .sidebar-wrapper {
    position: static;
  }

  .sidebar-card {
    position: static;
    border-radius: 12px;
    border-left: none;
    width: 100%;
    max-width: 620px;
  }
}

@media (max-width: 620px) {
  .app-main { padding: 12px 8px; }
}
</style>
