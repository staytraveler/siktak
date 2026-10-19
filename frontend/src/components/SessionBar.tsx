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
    <div className="flex items-center gap-2 text-xs text-muted">
      <span className="min-w-0 truncate">
        {session.user.name} ({session.user.loginId})
      </span>
      <button
        type="button"
        onClick={logout}
        className="min-h-[44px] shrink-0 border-[1.5px] border-ink bg-card px-3 font-bold text-ink"
      >
        로그아웃
      </button>
    </div>
  )
}
