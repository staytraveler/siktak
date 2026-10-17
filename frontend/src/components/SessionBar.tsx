import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { clearSession, getSession } from '../api/session'

// 로그인한 계정 표시 + 로그아웃 (3.1, 2.3 사용자 선택 드롭다운 대체)
export default function SessionBar() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const session = getSession()
  if (!session) return null

  const logout = () => {
    clearSession()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex items-center gap-2 text-xs text-gray-600">
      <span className="shrink-0">
        {session.user.name} ({session.user.loginId})
      </span>
      <button type="button" onClick={logout} className="rounded border border-gray-300 px-2 py-1 text-gray-900">
        로그아웃
      </button>
    </div>
  )
}
