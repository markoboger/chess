import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchStockfishAnalysis,
  type StockfishAnalysis,
} from '../api/stockfish-api'

export type { StockfishAnalysis }

export const useAnalysisStore = defineStore('analysis', () => {
  const enabled = ref(false)
  /** FEN → latest completed analysis */
  const cache = reactive(new Map<string, StockfishAnalysis>())
  const inFlight = new Set<string>()
  /** Bumps when analysis is toggled off or user starts a new session — ignore stale HTTP callbacks */
  let generation = 0

  function clearRuntimeState() {
    generation++
    inFlight.clear()
  }

  function setEnabled(on: boolean) {
    if (on === enabled.value) return
    enabled.value = on
    if (!on) clearRuntimeState()
  }

  /** Fire-and-forget: fill cache for one FEN */
  function ensureAnalyzed(fen: string) {
    if (!enabled.value || !fen) return
    if (cache.has(fen) || inFlight.has(fen)) return
    const g = generation
    inFlight.add(fen)
    void (async () => {
      const result = await fetchStockfishAnalysis(fen)
      inFlight.delete(fen)
      if (g !== generation || !enabled.value) return
      if (result) cache.set(fen, result)
    })()
  }

  /** After turning analysis on, warm cache for every position in the game */
  function prefetchBoardStates(fens: readonly string[]) {
    if (!enabled.value) return
    for (const f of fens) ensureAnalyzed(f)
  }

  function toggleAnalysis(boardFens: readonly string[]) {
    setEnabled(!enabled.value)
    if (enabled.value) prefetchBoardStates(boardFens)
  }

  return {
    enabled,
    cache,
    setEnabled,
    ensureAnalyzed,
    prefetchBoardStates,
    toggleAnalysis,
  }
})
