<template>
  <div class="move-history p-4">
    <h3 class="text-lg font-semibold mb-3">Move History</h3>
    <div v-if="gameStore.pgnMoves.length === 0" class="text-gray-500 dark:text-gray-400 text-sm">
      No moves yet
    </div>
    <div v-else class="space-y-1 max-h-64 overflow-y-auto">
      <div
        v-for="(move, index) in gameStore.pgnMoves"
        :key="index"
        class="text-sm font-mono"
      >
        <span class="text-gray-600 dark:text-gray-400">{{ Math.floor(index / 2) + 1 }}.</span>
        {{ move }}
      </div>
    </div>

    <div v-if="gameStore.pgnText" class="mt-4 pt-4 border-t border-gray-300 dark:border-gray-600">
      <h4 class="text-sm font-semibold mb-2">PGN</h4>
      <pre class="text-xs bg-gray-100 dark:bg-gray-800 p-2 rounded overflow-x-auto">{{ gameStore.pgnText }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useGameStore } from '../../stores/game'

const gameStore = useGameStore()
</script>
