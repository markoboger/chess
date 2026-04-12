/** POST body / JSON shape for https://chess-api.com/v1 (same as desktop ChessGUI). */

export interface StockfishAnalysis {
  evalPawns: number
  mateIn: number | null
  bestSan: string
  bestFrom: string
  bestTo: string
  depth: number
}

function readMate(raw: unknown): number | null {
  if (typeof raw !== 'number' || !Number.isFinite(raw)) return null
  const n = Math.trunc(raw)
  if (n === 0) return null
  return n
}

export async function fetchStockfishAnalysis(
  fen: string,
  depth = 12
): Promise<StockfishAnalysis | null> {
  const url = import.meta.env.VITE_STOCKFISH_API_URL || '/chess-api/v1'
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), 15_000)
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fen, depth }),
      signal: ctrl.signal,
    })
    if (!res.ok) return null
    const j = (await res.json()) as Record<string, unknown>
    const evalPawns = typeof j.eval === 'number' && Number.isFinite(j.eval) ? j.eval : 0
    const mateIn = readMate(j.mate)
    const bestSan = typeof j.san === 'string' ? j.san : ''
    const bestFrom = typeof j.from === 'string' ? j.from : ''
    const bestTo = typeof j.to === 'string' ? j.to : ''
    const d =
      typeof j.depth === 'number' && Number.isFinite(j.depth) ? Math.trunc(j.depth) : depth
    return { evalPawns, mateIn, bestSan, bestFrom, bestTo, depth: d }
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

/** Centipawn-loss style symbols (White's perspective), matching ChessGUI. */
export function moveAnnotation(
  evalBefore: number,
  evalAfter: number,
  whiteMove: boolean
): string {
  const cpLoss = whiteMove
    ? (evalBefore - evalAfter) * 100
    : (evalAfter - evalBefore) * 100
  if (cpLoss < 10) return ''
  if (cpLoss < 25) return '?!'
  if (cpLoss < 100) return '?'
  return '??'
}

export function annotationCssClass(ann: string): string {
  if (ann === '??') return 'ann-blunder'
  if (ann === '?') return 'ann-mistake'
  if (ann === '?!') return 'ann-dubious'
  return ''
}
