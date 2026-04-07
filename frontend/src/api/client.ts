import axios from 'axios'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 2000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config) => {
    if (config.method === 'post' && config.url?.includes('/moves')) {
      console.warn('[HTTP] POST moves:', config.url, config.data, new Error().stack?.split('\n').slice(1, 5).join(' | '))
    }
    return config
  }
)

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error.response?.data || error.message)
    return Promise.reject(error)
  }
)

export default apiClient
