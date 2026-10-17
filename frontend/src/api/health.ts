import { apiClient } from './client'

// 헬스체크 응답. OpenAPI 타입 생성(src/api/generated/) 도입 전까지 수동 정의한다.
export interface HealthResponse {
  status: string
  time: string
}

export interface DbHealthResponse {
  db: 'UP' | 'DOWN'
  time: string
}

// liveness: DB를 깨우지 않는다.
export async function fetchHealth(): Promise<HealthResponse> {
  const { data } = await apiClient.get<HealthResponse>('/api/v1/health')
  return data
}

// DB 연결 확인: 호출 시 Neon이 절전 중이면 깨운다.
export async function fetchDbHealth(): Promise<DbHealthResponse> {
  const { data } = await apiClient.get<DbHealthResponse>('/api/v1/health/db')
  return data
}
