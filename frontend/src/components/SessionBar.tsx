import { useQueryClient } from '@tanstack/react-query'
import { User } from 'lucide-react'
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
    <div className="flex items-center gap-3 rounded-2xl border border-line bg-card px-3 py-2.5 text-sm">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold-soft">
        <User size={20} strokeWidth={1.6} />
      </span>
      <span className="min-w-0 flex-1 truncate">
        <b>{session.user.name}</b> <span className="text-muted">({session.user.loginId})</span>
      </span>
      <button
        type="button"
        onClick={logout}
        className="min-h-[44px] shrink-0 rounded-xl border border-ink bg-card px-4 text-xs font-bold text-ink"
      >
        로그아웃
      </button>
    </div>
  )
}
