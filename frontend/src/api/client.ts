import axios, { AxiosError } from 'axios'
import { areaOfPath, getUserId } from './currentUser'

// 백엔드 주소는 환경변수로 주입한다 (루트 .env.example의 VITE_API_BASE_URL).
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// [임시 — 2.3] 현재 화면 영역(/app, /admin)에서 고른 사용자를 X-User-Id로 보낸다.
apiClient.interceptors.request.use((config) => {
  const userId = getUserId(areaOfPath(window.location.pathname))
  if (userId) {
    config.headers.set('X-User-Id', userId)
  }
  return config
})

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
