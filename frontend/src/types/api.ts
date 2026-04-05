export interface CreateGameRequest {
  startFen?: string
}

export interface CreateGameResponse {
  gameId: string
  fen: string
}

export interface GameStateResponse {
  gameId: string
  fen: string
  pgn: string
  status: string
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
