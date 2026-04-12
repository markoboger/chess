import axios from 'axios'

/** Match runner HTTP API (ExperimentRoutes) — same service as desktop "Browse Experiment Games". */
const client = axios.create({
  baseURL: import.meta.env.VITE_MATCH_RUNNER_URL || '/match-runner',
  timeout: 20_000,
  headers: { 'Content-Type': 'application/json' },
})

export interface MrExperiment {
  id: string
  name: string
  description: string | null
  createdAt: string
  status: string
  requestedGames: number
  finishedAt?: string | null
  totalDurationMs?: number | null
}

export interface MrMatchRun {
  id: string
  experimentId: string
  chessGameId: string
  whiteStrategy: string
  blackStrategy: string
  startedAt: string
  finishedAt?: string | null
  result?: string | null
  winner?: string | null
  moveCount?: number | null
  finalFen?: string | null
  pgn?: string | null
  errorMessage?: string | null
  durationMs?: number | null
}

export const matchrunnerApi = {
  async listExperiments(): Promise<MrExperiment[]> {
    const { data } = await client.get<MrExperiment[]>('/experiments')
    return Array.isArray(data) ? data : []
  },

  async listRuns(experimentId: string): Promise<MrMatchRun[]> {
    const { data } = await client.get<MrMatchRun[]>(`/experiments/${experimentId}/runs`)
    return Array.isArray(data) ? data : []
  },
}
