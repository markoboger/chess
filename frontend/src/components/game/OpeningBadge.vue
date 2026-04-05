<template>
  <Transition name="badge">
    <div v-if="openingStore.current" class="opening-badge">
      <span class="eco">{{ openingStore.current.eco }}</span>
      <span class="sep">·</span>
      <span class="name">{{ openingStore.current.name }}</span>
    </div>
    <div v-else-if="showEmpty" class="opening-badge opening-badge--empty">
      <span class="name">Opening not recognised</span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { useOpeningStore } from '../../stores/opening'
import { useGameStore } from '../../stores/game'
import { watch } from 'vue'

defineProps<{ showEmpty?: boolean }>()

const openingStore = useOpeningStore()
const gameStore = useGameStore()

watch(
  () => gameStore.fen,
  (fen: string) => {
    const piecePlacement = fen.split(' ')[0]
    openingStore.lookupByFen(piecePlacement)
  },
  { immediate: true }
)
</script>

<style scoped>
.opening-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(98, 153, 36, 0.12);
  border: 1px solid rgba(98, 153, 36, 0.3);
  border-radius: 20px;
  font-size: 12.5px;
  line-height: 1.4;
  max-width: 100%;
  overflow: hidden;
}

.opening-badge--empty {
  background: rgba(150, 150, 150, 0.08);
  border-color: rgba(150, 150, 150, 0.2);
}

.eco {
  font-weight: 700;
  color: #4a7c1b;
  font-size: 12px;
  flex-shrink: 0;
}

.sep {
  color: #aaa;
  flex-shrink: 0;
}

.name {
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.opening-badge--empty .name {
  color: #999;
  font-style: italic;
}

/* Transition */
.badge-enter-active { transition: opacity 0.3s, transform 0.3s; }
.badge-leave-active { transition: opacity 0.2s; }
.badge-enter-from  { opacity: 0; transform: translateY(-4px); }
.badge-leave-to    { opacity: 0; }
</style>
