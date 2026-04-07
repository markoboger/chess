export interface GameSettings {
  whiteIsHuman: boolean
  blackIsHuman: boolean
  whiteStrategy: string
  blackStrategy: string
  clockInitialMs?: number
  clockIncrementMs?: number
}

export interface CreateGameRequest {
  startFen?: string
  settings?: GameSettings
}

export interface CreateGameResponse {
  gameId: string
  fen: string
  settings: GameSettings
}

export interface GameStateResponse {
  gameId: string
  fen: string
  pgn: string
  status: string
  settings: GameSettings
}

export interface GameSummary {
  gameId: string
  status: string
  settings: GameSettings
}

export interface ListGamesResponse {
  games: GameSummary[]
}

export interface MakeMoveRequest {
  move: string
}

export interface MakeMoveResponse {
  success: boolean
  fen: string
  event?: string
}

export interface LoadFenRequest {
  fen: string
}

export interface LoadFenResponse {
  success: boolean
  fen: string
}

export interface AiMoveRequest {
  strategy: string
}

export interface AiMoveResponse {
  move?: string
}

export interface Opening {
  code: string
  name: string
  moves: string
  fen: string
}

export interface OpeningSearchResponse {
  openings: Opening[]
  total: number
}
