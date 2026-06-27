import type { RoundPairing } from '../api/tournament-api'

export interface TournamentGameRow {
  gameId: string
  round: number
  white: { id: string; name: string }
  black: { id: string; name: string }
  moves: string
  outcomeLabel: string
  moveCount: number
  isLive: boolean
  isFinished: boolean
  /** Server game status when known (timeout, checkmate, ongoing, …). */
  status?: string
}

const TERMINAL_STATUSES = new Set(['checkmate', 'stalemate', 'draw', 'resigned', 'timeout', 'finished'])

export function formatMatchOutcome(
  white: { name: string },
  black: { name: string },
  outcome?: 'white' | 'black' | 'draw' | null,
  status?: string | null
): string {
  const st = status?.toLowerCase()
  if (outcome === 'white') {
    return st === 'timeout' ? `${white.name} won on time` : `${white.name} won`
  }
  if (outcome === 'black') {
    return st === 'timeout' ? `${black.name} won on time` : `${black.name} won`
  }
  if (outcome === 'draw') {
    if (st === 'stalemate') return 'Draw (stalemate)'
    return 'Draw'
  }
  if (st === 'timeout') {
    return 'Finished on time'
  }
  if (st && TERMINAL_STATUSES.has(st)) {
    return st.charAt(0).toUpperCase() + st.slice(1)
  }
  return '…'
}

export function pairingsToGameRows(
  pairings: RoundPairing[],
  round: number,
  tournamentStatus: string | undefined,
  currentRound: number
): TournamentGameRow[] {
  const rows: TournamentGameRow[] = []
  const tournamentLive = tournamentStatus === 'started'
  for (const p of pairings) {
    for (const m of p.matches ?? []) {
      const outcome = m.outcome
      const hasOutcome = outcome === 'white' || outcome === 'black' || outcome === 'draw'
      const isFinished = hasOutcome || round < currentRound || tournamentStatus === 'finished'
      const isLive = tournamentLive && !isFinished && round >= currentRound
      rows.push({
        gameId: m.gameId,
        round,
        white: p.white,
        black: p.black,
        moves: m.moves ?? '',
        outcomeLabel: formatMatchOutcome(p.white, p.black, outcome),
        moveCount: (m.moves ?? '').trim().split(/\s+/).filter(Boolean).length,
        isLive,
        isFinished,
      })
    }
  }
  return rows
}

export function formatGameStateResult(state: {
  status?: string
  winner?: 'white' | 'black' | null
  white: { name: string }
  black: { name: string }
}): string {
  const st = state.status?.toLowerCase()
  if (state.winner === 'white') {
    return formatMatchOutcome(state.white, state.black, 'white', st)
  }
  if (state.winner === 'black') {
    return formatMatchOutcome(state.white, state.black, 'black', st)
  }
  if (st === 'draw' || st === 'stalemate') return 'Draw'
  if (st === 'ongoing' || st === 'pending') return 'In progress'
  if (st && TERMINAL_STATUSES.has(st)) {
    return formatMatchOutcome(state.white, state.black, null, st)
  }
  return state.status ?? 'Unknown'
}

export function isGameLiveStatus(status?: string): boolean {
  const st = status?.toLowerCase()
  return st === 'ongoing' || st === 'pending'
}
