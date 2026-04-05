import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Chess } from 'chess.js'

export interface OpeningInfo {
  eco: string
  name: string
  moves: string
}

// Module-level singleton so the map is built only once across store resets
let fenMap: Map<string, OpeningInfo> | null = null
let mapPromise: Promise<Map<string, OpeningInfo>> | null = null

async function buildFenMap(): Promise<Map<string, OpeningInfo>> {
  const map = new Map<string, OpeningInfo>()
  const files = ['a', 'b', 'c', 'd', 'e']
  const board = new Chess()

  for (const f of files) {
    try {
      const res = await fetch(`/openings/${f}.tsv`)
      if (!res.ok) continue
      const text = await res.text()
      for (const rawLine of text.split('\n')) {
        const line = rawLine.trim()
        if (!line || line.startsWith('eco')) continue
        const tab1 = line.indexOf('\t')
        const tab2 = line.indexOf('\t', tab1 + 1)
        if (tab1 < 0 || tab2 < 0) continue
        const eco = line.slice(0, tab1).trim()
        const name = line.slice(tab1 + 1, tab2).trim()
        const pgn = line.slice(tab2 + 1).trim()
        if (!eco || !name) continue
        try {
          board.reset()
          if (pgn) board.loadPgn(pgn)
          const placement = board.fen().split(' ')[0]
          if (!map.has(placement)) {
            map.set(placement, { eco, name, moves: pgn })
          }
        } catch {
          // ignore entries with unparseable PGN
        }
      }
    } catch {
      // ignore fetch errors for individual files
    }
  }
  return map
}

function getMap(): Promise<Map<string, OpeningInfo>> {
  if (fenMap) return Promise.resolve(fenMap)
  if (!mapPromise) {
    mapPromise = buildFenMap().then(m => { fenMap = m; return m })
  }
  return mapPromise
}

export const useOpeningStore = defineStore('opening', () => {
  const current = ref<OpeningInfo | null>(null)
  const ready = ref(false)

  // Start loading eagerly so the map is ready by the time moves are made
  async function init() {
    await getMap()
    ready.value = true
  }

  async function lookupByFen(piecePlacement: string) {
    const map = await getMap()
    ready.value = true
    current.value = map.get(piecePlacement) ?? null
  }

  function clear() {
    current.value = null
  }

  return { current, ready, init, lookupByFen, clear }
})
