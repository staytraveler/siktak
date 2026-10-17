import axios, { AxiosError } from 'axios'
import { clearSession, getSession } from './session'

// 백엔드 주소는 환경변수로 주입한다 (루트 .env.example의 VITE_API_BASE_URL).
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

const LOGIN_API = '/api/v1/auth/login'

apiClient.interceptors.request.use((config) => {
  const session = getSession()
  if (session && config.url !== LOGIN_API) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  return config
})

// 401(토큰 없음·만료·정지)이면 세션을 지우고 로그인 화면으로 보낸다. 로그인 실패 401은 화면이 직접 보여준다.
apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (error instanceof AxiosError && error.response?.status === 401 && error.config?.url !== LOGIN_API) {
      clearSession()
      if (window.location.pathname !== '/login') {
        const next = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/login?next=${next}`)
      }
    }
    return Promise.reject(error)
  },
)

// 공통 에러 응답 {code, message, details} (CLAUDE.md 9장)
export interface ApiError {
  code: string
  message: string
  details?: unknown
}

export function errorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiError | undefined
    if (data?.message) return data.message
    return error.message
  }
  return '알 수 없는 오류가 발생했습니다.'
}
