export type GameStatus = 'in_progress' | 'checkmate' | 'stalemate' | 'draw'
export type Turn = 'w' | 'b'
export type PieceColor = 'white' | 'black'

export interface GameState {
  gameId: string | null
  fen: string
  pgn: string
  status: GameStatus
  moveHistory: string[]
  turn: Turn
  isCheck: boolean
  isCheckmate: boolean
  isStalemate: boolean
  isDraw: boolean
  selectedSquare: string | null
  legalMoves: string[]
  lastMove: { from: string; to: string } | null
}

export interface Move {
  from: string
  to: string
  promotion?: string
}

export interface ChessMove {
  color: Turn
  from: string
  to: string
  flags: string
  piece: string
  san: string
  lan?: string
  before?: string
  after?: string
  captured?: string
  promotion?: string
}
