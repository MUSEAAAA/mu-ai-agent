import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api',
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
