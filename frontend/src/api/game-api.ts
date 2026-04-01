import apiClient from './client'
import type {
  CreateGameRequest,
  CreateGameResponse,
  GameStateResponse,
  MakeMoveRequest,
  MakeMoveResponse,
  LoadFenRequest,
} from '../types/api'

export const gameApi = {
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

  async loadFen(gameId: string, fen: string): Promise<GameStateResponse> {
    const request: LoadFenRequest = { fen }
    const response = await apiClient.post(`/games/${gameId}/fen`, request)
    return response.data
  },

  async deleteGame(gameId: string): Promise<void> {
    await apiClient.delete(`/games/${gameId}`)
  },
}
