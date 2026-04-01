<template>
  <button
    @click="uiStore.toggleTheme()"
    class="p-2 rounded-lg bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
    :aria-label="uiStore.theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'"
  >
    <span class="text-2xl">{{ uiStore.theme === 'light' ? '🌙' : '☀️' }}</span>
  </button>
</template>

<script setup lang="ts">
import { useUIStore } from '../../stores/ui'

const uiStore = useUIStore()
</script>
