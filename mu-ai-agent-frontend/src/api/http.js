import axios from 'axios'

const PRODUCTION_API_BASE_URL = '/api'
const DEVELOPMENT_API_BASE_URL = 'http://localhost:8123/api'

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.PROD ? PRODUCTION_API_BASE_URL : DEVELOPMENT_API_BASE_URL)
).replace(/\/+$/, '')

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 180000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }

    const message =
      error.response?.data?.message ||
      error.response?.statusText ||
      error.message ||
      '请求失败，请稍后重试'

    return Promise.reject(new Error(message, { cause: error }))
  },
)
