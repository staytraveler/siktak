import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

const queryClient = new QueryClient()

// 사이트를 여는 순간 서버(Render 무료 인스턴스)와 DB(Neon 절전)를 미리 깨운다. 스플래시·로그인 화면이 보이는 동안
// 뒤에서 응답이 오므로 첫 클릭의 지연이 줄어든다. 실패해도 무시한다 (깨우는 용도일 뿐).
const apiBase = import.meta.env.VITE_API_BASE_URL
if (apiBase) {
  fetch(`${apiBase}/api/v1/health/db`, { method: 'GET', cache: 'no-store' }).catch(() => undefined)
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
