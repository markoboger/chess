<script setup lang="ts">
import { onMounted } from 'vue'
import ChessBoard from './components/board/ChessBoard.vue'
import GameControls from './components/controls/GameControls.vue'
import MoveHistory from './components/controls/MoveHistory.vue'
import ThemeToggle from './components/ui/ThemeToggle.vue'
import { useGameStore } from './stores/game'

const gameStore = useGameStore()

onMounted(async () => {
  await gameStore.createGame()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 transition-colors">
    <header class="bg-white dark:bg-gray-800 shadow-md">
      <div class="container mx-auto px-4 py-4 flex justify-between items-center">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">
          Chess Game
        </h1>
        <ThemeToggle />
      </div>
    </header>

    <main class="container mx-auto px-4 py-8">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Chess Board (takes 2 columns on desktop) -->
        <div class="lg:col-span-2">
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6">
            <ChessBoard />
          </div>
        </div>

        <!-- Sidebar (controls and history) -->
        <div class="space-y-6">
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg">
            <GameControls />
          </div>

          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg">
            <MoveHistory />
          </div>
        </div>
      </div>

      <div v-if="gameStore.loading" class="fixed inset-0 bg-black/50 flex items-center justify-center">
        <div class="bg-white dark:bg-gray-800 rounded-lg p-8 text-center">
          <div class="text-2xl">⌛</div>
          <div class="mt-2 text-gray-900 dark:text-white">Loading...</div>
        </div>
      </div>
    </main>
  </div>
</template>
