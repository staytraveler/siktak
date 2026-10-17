import axios from 'axios'

// 백엔드 주소는 환경변수로 주입한다 (루트 .env.example의 VITE_API_BASE_URL).
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})
