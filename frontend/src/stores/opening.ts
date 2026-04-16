import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Chess } from 'chess.js'

export interface OpeningInfo {
  eco: string
  name: string
  moves: string
}

export interface OpeningContinuation {
  san: string
  remainingPlies: number
  eco: string
  name: string
}

// Module-level singleton so the map is built only once across store resets
let fenMap: Map<string, OpeningInfo> | null = null
let continuationMap: Map<string, OpeningContinuation[]> | null = null
let mapPromise: Promise<Map<string, OpeningInfo>> | null = null

async function buildFenMap(): Promise<Map<string, OpeningInfo>> {
  const map = new Map<string, OpeningInfo>()
  const prefixes = new Map<string, OpeningContinuation[]>()
  const files = ['a', 'b', 'c', 'd', 'e']

  for (const f of files) {
    try {
      const res = await fetch(`/openings/${f}.tsv`)
      if (!res.ok) continue
      const text = await res.text()
      for (const rawLine of text.split('\n')) {
        const parsed = parseTsvLine(rawLine)
        if (!parsed) continue
        const { eco, name, pgn } = parsed
        indexContinuation(prefixes, eco, name, pgn)
        indexFinalPosition(map, eco, name, pgn)
      }
    } catch {
      // ignore fetch errors for individual files
    }
  }
  continuationMap = new Map(
    Array.from(prefixes.entries(), ([key, entries]) => [
      key,
      entries
        .sort((a, b) => b.remainingPlies - a.remainingPlies || a.san.localeCompare(b.san))
        .filter((entry, index, arr) => arr.findIndex(other => other.san === entry.san) === index),
    ]),
  )
  return map
}

function parseTsvLine(rawLine: string): { eco: string; name: string; pgn: string } | null {
  const line = rawLine.trim()
  if (!line || line.startsWith('eco')) return null
  const tab1 = line.indexOf('\t')
  const tab2 = line.indexOf('\t', tab1 + 1)
  if (tab1 < 0 || tab2 < 0) return null
  const eco = line.slice(0, tab1).trim()
  const name = line.slice(tab1 + 1, tab2).trim()
  const pgn = line.slice(tab2 + 1).trim()
  if (!eco || !name) return null
  return { eco, name, pgn }
}

function pgnTokens(pgn: string): string[] {
  return pgn.split(/\s+/).filter(token => token && !/^\d+\.+$/.test(token))
}

function indexContinuation(
  prefixes: Map<string, OpeningContinuation[]>,
  eco: string,
  name: string,
  pgn: string,
): void {
  try {
    const board = new Chess()
    const tokens = pgnTokens(pgn)
    for (let i = 0; i < tokens.length; i++) {
      const key = continuationKey(board.fen())
      const next = tokens[i]
      const entry: OpeningContinuation = {
        san: next,
        remainingPlies: tokens.length - i,
        eco,
        name,
      }
      prefixes.set(key, [...(prefixes.get(key) ?? []), entry])
      if (!board.move(next as any)) break
    }
  } catch {
    // ignore entries with unparseable PGN
  }
}

function indexFinalPosition(
  map: Map<string, OpeningInfo>,
  eco: string,
  name: string,
  pgn: string,
): void {
  try {
    const board = new Chess()
    if (pgn) board.loadPgn(pgn)
    const placement = board.fen().split(' ')[0]
    if (!map.has(placement)) {
      map.set(placement, { eco, name, moves: pgn })
    }
  } catch {
    // ignore entries with unparseable PGN
  }
}

function continuationKey(fen: string): string {
  return fen.split(' ').slice(0, 4).join(' ')
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
  /** Deduplicated list for the Openings menu (same data as the desktop app’s opening book). */
  const catalog = ref<OpeningInfo[]>([])

  // Start loading eagerly so the map is ready by the time moves are made
  async function init() {
    await getMap()
    ready.value = true
  }

  async function loadCatalog() {
    if (catalog.value.length > 0) return
    const map = await getMap()
    ready.value = true
    const dedupe = new Map<string, OpeningInfo>()
    for (const o of map.values()) {
      const key = `${o.eco}|${o.moves}`
      if (!dedupe.has(key)) dedupe.set(key, o)
    }
    catalog.value = [...dedupe.values()].sort(
      (a, b) => a.eco.localeCompare(b.eco) || a.name.localeCompare(b.name)
    )
  }

  async function lookupByFen(piecePlacement: string) {
    const map = await getMap()
    ready.value = true
    current.value = map.get(piecePlacement) ?? null
  }

  async function bestContinuationForFen(fen: string): Promise<OpeningContinuation | null> {
    await getMap()
    ready.value = true
    const candidates = continuationMap?.get(continuationKey(fen)) ?? []
    return candidates[0] ?? null
  }

  function clear() {
    current.value = null
  }

  return { current, ready, catalog, init, loadCatalog, lookupByFen, bestContinuationForFen, clear }
})
