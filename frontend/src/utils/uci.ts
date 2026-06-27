import { Chess } from 'chess.js'

const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

export function startFenFromPosition(startPosition?: string): string {
  const p = (startPosition ?? 'standard').trim()
  if (!p || p === 'standard') return INITIAL_FEN
  return p
}

/** Apply space-separated UCI moves; returns FEN states after each ply and SAN move list. */
export function replayUciMoves(
  movesUci: string,
  startPosition?: string
): { states: string[]; sans: string[]; error?: string } {
  const startFen = startFenFromPosition(startPosition)
  const chess = new Chess(startFen)
  const states: string[] = [chess.fen()]
  const sans: string[] = []
  const tokens = movesUci.trim().split(/\s+/).filter(Boolean)

  for (const uci of tokens) {
    if (uci.length < 4) return { states, sans, error: `Invalid UCI: ${uci}` }
    const from = uci.slice(0, 2)
    const to = uci.slice(2, 4)
    const promotion = uci.length >= 5 ? uci[4] : undefined
    try {
      const m = chess.move({ from, to, promotion })
      if (!m) return { states, sans, error: `Illegal move: ${uci}` }
      sans.push(m.san)
      states.push(chess.fen())
    } catch {
      return { states, sans, error: `Could not apply ${uci}` }
    }
  }

  return { states, sans }
}

export function uciMoveCount(movesUci: string): number {
  return movesUci.trim().split(/\s+/).filter(Boolean).length
}
