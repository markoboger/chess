<template>
  <div class="game-controls p-4 space-y-4">
    <div class="flex gap-2 flex-wrap">
      <button
        @click="handleNewGame"
        :disabled="gameStore.loading"
        class="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
      >
        New Game
      </button>
      <button
        v-if="gameStore.gameId"
        @click="handleReset"
        class="px-4 py-2 bg-gray-600 text-white rounded hover:bg-gray-700"
      >
        Reset
      </button>
    </div>

    <div v-if="gameStore.gameId" class="status-display space-y-2">
      <div class="text-lg font-semibold">
        Turn: <span :class="gameStore.turnColor === 'white' ? 'text-gray-800 dark:text-gray-200' : 'text-gray-600 dark:text-gray-400'">
          {{ gameStore.turnColor === 'white' ? 'White' : 'Black' }}
        </span>
      </div>

      <div v-if="gameStore.isCheck" class="text-orange-600 dark:text-orange-400 font-semibold">
        Check!
      </div>

      <div v-if="gameStore.isCheckmate" class="text-red-600 dark:text-red-400 font-bold text-xl">
        Checkmate! {{ gameStore.turn === 'w' ? 'Black' : 'White' }} wins!
      </div>

      <div v-if="gameStore.isStalemate" class="text-blue-600 dark:text-blue-400 font-bold">
        Stalemate - Draw!
      </div>

      <div v-if="gameStore.isDraw" class="text-blue-600 dark:text-blue-400 font-bold">
        Draw!
      </div>

      <div v-if="gameStore.error" class="text-red-600 dark:text-red-400 text-sm">
        {{ gameStore.error }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()

async function handleNewGame() {
  await gameStore.createGame()
}

function handleReset() {
  gameStore.resetGame()
}
</script>
