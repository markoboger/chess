<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import MenuBar from './components/controls/MenuBar.vue'
import ChessBoard from './components/board/ChessBoard.vue'
import GameControls from './components/controls/GameControls.vue'
import { useGameStore } from './stores/game'

const gameStore = useGameStore()

onMounted(async () => {
  await gameStore.createGame()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})

function handleKeydown(e: KeyboardEvent) {
  // Don't capture keys when typing in a textarea/input
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'TEXTAREA' || tag === 'INPUT') return

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
    // Cmd+V / Ctrl+V to paste FEN or PGN
    navigator.clipboard.readText().then((text: string) => {
      const trimmed = text.trim()
      if (trimmed) gameStore.loadPgnOrFen(trimmed)
    }).catch(() => { /* clipboard read denied */ })
  }
}
</script>

<template>
  <div class="app-shell">
    <MenuBar />

    <main class="app-main">
      <div class="board-area">
        <ChessBoard />
      </div>
      <div class="sidebar">
        <GameControls />
      </div>
    </main>
  </div>
</template>

<style>
/* Global app styles — not scoped so they apply everywhere */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: white;
  border-bottom: 1px solid #ddd;
  padding: 12px 24px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.app-title {
  font-size: 22px;
  font-weight: 700;
  color: #333;
}

.app-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 24px;
  gap: 0;
}

.board-area {
  background: white;
  border-radius: 12px 0 0 12px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  padding: 16px;
}

.sidebar {
  background: white;
  border-radius: 0 12px 12px 0;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  border-left: 1px solid #e0e0e0;
  width: 320px;
  min-height: 600px;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .app-main {
    flex-direction: column;
    align-items: center;
    gap: 16px;
  }
  .board-area { border-radius: 12px; }
  .sidebar { border-radius: 12px; border-left: none; width: 100%; max-width: 600px; min-height: auto; }
}
</style>
