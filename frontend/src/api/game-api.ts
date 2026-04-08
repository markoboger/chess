import apiClient from './client'
import type {
  CreateGameRequest,
  CreateGameResponse,
  GameSummary,
  GameStateResponse,
  ListGamesResponse,
  MakeMoveRequest,
  MakeMoveResponse,
  LoadFenRequest,
  LoadFenResponse,
  AiMoveRequest,
  AiMoveResponse,
} from '../types/api'

export type { GameSettings, GameSummary } from '../types/api'

export const gameApi = {
  async listGames(): Promise<GameSummary[]> {
    const response = await apiClient.get<ListGamesResponse>('/games')
    return response.data.games
  },

  async createGame(request?: CreateGameRequest): Promise<CreateGameResponse> {
    const response = await apiClient.post('/games', request || {})
    return response.data
  },

  async getGameState(gameId: string): Promise<GameStateResponse> {
    const response = await apiClient.get(`/games/${gameId}`)
    return response.data
  },

  async makeMove(gameId: string, move: string): Promise<MakeMoveResponse> {
    const request: MakeMoveRequest = { move }
    const response = await apiClient.post(`/games/${gameId}/moves`, request)
    return response.data
  },

  async loadFen(gameId: string, fen: string): Promise<LoadFenResponse> {
    const request: LoadFenRequest = { fen }
    const response = await apiClient.post(`/games/${gameId}/fen`, request)
    return response.data
  },

  async aiMove(gameId: string, strategy: string): Promise<AiMoveResponse> {
    const request: AiMoveRequest = { strategy }
    const response = await apiClient.post(`/games/${gameId}/ai-move`, request)
    return response.data
  },

  async deleteGame(gameId: string): Promise<void> {
    await apiClient.delete(`/games/${gameId}`)
  },

  async deleteAllGames(): Promise<void> {
    await apiClient.delete('/games')
  },
}
