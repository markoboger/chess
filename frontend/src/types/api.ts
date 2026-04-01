export interface CreateGameRequest {
  startFen?: string
}

export interface CreateGameResponse {
  gameId: string
  fen: string
  pgn: string
  status: string
}

export interface GameStateResponse {
  gameId: string
  fen: string
  pgn: string
  status: string
  moveHistory: string[]
  turn: string
  isCheck: boolean
  isCheckmate: boolean
  isStalemate: boolean
}

export interface MakeMoveRequest {
  move: string
}

export interface MakeMoveResponse {
  success: boolean
  fen: string
  pgn: string
  status: string
  moveHistory: string[]
  error?: string
}

export interface LoadFenRequest {
  fen: string
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
