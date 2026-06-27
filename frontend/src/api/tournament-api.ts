import axios from 'axios'
import { tournamentBaseUrl } from './tournament-stream'

/** NowChess tournament server — see api/openapi.yaml in maichess/tournament-server. */
const client = axios.create({
  baseURL: tournamentBaseUrl(),
  timeout: 25_000,
  headers: { 'Content-Type': 'application/json' },
})

export interface BotRef {
  id: string
  name: string
}

export interface TournamentResult {
  rank: number
  points: number
  tieBreak: number
  bot: BotRef
  nbGames: number
  wins: number
  draws: number
  losses: number
}

export interface TournamentInfo {
  id: string
  fullName: string
  clock: { limit: number; increment: number }
  variant: { key: string; name: string }
  rated: boolean
  nbPlayers: number
  nbRounds: number
  format: string
  matchesPerPairing: number
  startPosition: string
  createdBy: string
  status?: 'created' | 'started' | 'finished'
  round?: number
  standing?: { page: number; players: TournamentResult[] }
  winner?: BotRef | null
}

export interface TournamentList {
  created: TournamentInfo[]
  started: TournamentInfo[]
  finished: TournamentInfo[]
}

export interface RoundMatch {
  gameId: string
  whiteId?: string
  outcome?: 'white' | 'black' | 'draw' | null
  moves?: string
}

export interface RoundPairing {
  white: BotRef
  black: BotRef
  matches: RoundMatch[]
  aggregateOutcome?: 'white' | 'black' | 'draw' | null
}

export interface RoundPairings {
  round: number
  pairings: RoundPairing[]
}

export interface GameState {
  id: string
  tournamentId: string
  round: number
  white: BotRef
  black: BotRef
  moves: string
  fen: string
  status: string
  turn: 'white' | 'black'
  winner?: 'white' | 'black' | null
  clock?: { whiteTime: number; blackTime: number; increment: number }
  startPosition?: string
}

export interface RegisterResponse {
  id: string
  token: string
}

export interface RegisteredBot {
  id: string
  name: string
  family?: string | null
  strategyType?: string | null
  engineType?: string | null
}

export interface RegisterPermanentBotParams {
  name: string
  family?: string
  strategyType?: string
  engineType?: string
}

export interface CreateTournamentParams {
  name: string
  nbRounds: number
  clockLimit: number
  clockIncrement: number
  format?: string
  rated?: boolean
  /** Comma-separated registered bot ids to add at creation */
  bots?: string
}

function authedClient(token: string) {
  return axios.create({
    baseURL: tournamentBaseUrl(),
    timeout: 25_000,
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
}

export function tournamentApiError(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const d = e.response?.data as { error?: string } | string | undefined
    if (d && typeof d === 'object' && d.error) return d.error
    if (typeof d === 'string' && d.trim()) return d
    return e.message
  }
  return e instanceof Error ? e.message : 'Request failed'
}

function toFormBody(params: Record<string, string | number | boolean>): string {
  return new URLSearchParams(
    Object.entries(params).map(([k, v]) => [k, String(v)])
  ).toString()
}

export const tournamentApi = {
  baseUrl: tournamentBaseUrl,

  async listTournaments(): Promise<TournamentList> {
    const { data } = await client.get<TournamentList>('/api/tournament')
    return {
      created: data.created ?? [],
      started: data.started ?? [],
      finished: data.finished ?? [],
    }
  },

  async getTournament(id: string): Promise<TournamentInfo> {
    const { data } = await client.get<TournamentInfo>(`/api/tournament/${id}`)
    return data
  },

  async getRoundPairings(tournamentId: string, round: number): Promise<RoundPairings> {
    const { data } = await client.get<RoundPairings>(`/api/tournament/${tournamentId}/round/${round}`)
    return data
  },

  async getGame(tournamentId: string, gameId: string): Promise<GameState> {
    const { data } = await client.get<GameState>(`/api/tournament/${tournamentId}/game/${gameId}`)
    return data
  },

  async registerIdentity(name: string, isBot: boolean): Promise<RegisterResponse> {
    const { data } = await client.post<RegisterResponse>('/api/auth/register', { name, isBot })
    return data
  },

  async registerPermanentBot(
    params: RegisterPermanentBotParams,
    botToken: string
  ): Promise<RegisteredBot> {
    const { data } = await authedClient(botToken).post<RegisteredBot>('/api/bots', {
      name: params.name,
      family: params.family ?? 'maichess',
      strategyType: params.strategyType ?? 'deepening-opening-endgame',
      engineType: params.engineType ?? 'internal',
    })
    return data
  },

  async joinTournament(tournamentId: string, token: string): Promise<void> {
    await authedClient(token).post(`/api/tournament/${tournamentId}/join`)
  },

  async createTournament(params: CreateTournamentParams, directorToken: string): Promise<TournamentInfo> {
    const body: Record<string, string | number | boolean> = {
      name: params.name,
      nbRounds: params.nbRounds,
      clockLimit: params.clockLimit,
      clockIncrement: params.clockIncrement,
      format: params.format ?? 'swiss',
      rated: params.rated ?? true,
    }
    if (params.bots?.trim()) body.bots = params.bots.trim()
    const { data } = await authedClient(directorToken).post<TournamentInfo>(
      '/api/tournament',
      toFormBody(body),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )
    return data
  },

  async startTournament(tournamentId: string, directorToken: string): Promise<TournamentInfo> {
    const { data } = await authedClient(directorToken).post<TournamentInfo>(
      `/api/tournament/${tournamentId}/start`
    )
    return data
  },

  async addParticipant(
    tournamentId: string,
    botId: string,
    directorToken: string
  ): Promise<void> {
    await authedClient(directorToken).post(`/api/tournament/${tournamentId}/participants`, {
      botId,
    })
  },
}
