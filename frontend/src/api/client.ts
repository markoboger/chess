import axios from 'axios'
import { log } from '../utils/log'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  /** Default; heavy endpoints (e.g. ai-move) override per request. */
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config) => {
    if (config.method === 'post' && config.url?.includes('/moves')) {
      log.warn(
        '[HTTP] POST moves:',
        config.url,
        config.data,
        new Error('HTTP POST /moves trace').stack?.split('\n').slice(1, 5).join(' | ')
      )
    }
    return config
  }
)

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    log.error('API Error:', error.response?.data || error.message)
    return Promise.reject(error)
  }
)

export default apiClient
