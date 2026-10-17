import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { getSession, homeOf, type Role } from '../api/session'

// 로그인·역할 확인. 실제 권한 검사는 백엔드가 한다 (참가자의 운영자 API 호출은 403).
export default function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const location = useLocation()
  const session = getSession()
  if (!session) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (role && session.user.role !== role) {
    return <Navigate to={homeOf(session.user.role)} replace />
  }
  return <>{children}</>
}
