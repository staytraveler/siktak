import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { api } from '../api/endpoints'
import { clearSession, homeOf, setSession, type Session } from '../api/session'

// 테스트 계정 (docs/demo-scenario.md 6장). 심사위원이 버튼 한 번으로 들어가도록 비밀번호를 함께 둔다.
const TEST_ACCOUNTS = [
  { loginId: 'admin', password: 'siktak-admin!', label: '운영자', note: '데모 도구·매칭 관리' },
  { loginId: 'demo01', password: 'siktak1234', label: '시연 참가자', note: '신청 전 상태' },
  { loginId: 'user01', password: 'siktak1234', label: '참가자 user01', note: 'S1 신청·결제 완료' },
  { loginId: 'user14', password: 'siktak1234', label: '참가자 user14', note: '연령 이탈 → 대기 → 일정 이동' },
  { loginId: 'user23', password: 'siktak1234', label: '참가자 user23', note: 'S3 매칭 실패 → 환불 선택' },
]

// 로그인 (/login). 디자인은 9.x에서 입힌다.
export default function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const queryClient = useQueryClient()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')

  const login = useMutation({
    mutationFn: (account: { loginId: string; password: string }) => {
      clearSession()
      return api.login(account.loginId, account.password)
    },
    onSuccess: (session: Session) => {
      setSession(session)
      queryClient.clear()
      const next = params.get('next')
      const home = homeOf(session.user.role)
      // 참가자가 운영자 화면으로 돌아가지 않게 next는 같은 영역일 때만 따른다
      const target = next && next.startsWith('/') && (next.startsWith('/admin') === (home === '/admin')) ? next : home
      navigate(target, { replace: true })
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    login.mutate({ loginId, password })
  }

  return (
    <main className="mx-auto min-h-screen max-w-md bg-white p-4">
      <h1 className="text-xl font-bold">우연한 식탁 로그인</h1>

      <form onSubmit={submit} className="mt-6 space-y-3">
        <label className="block text-sm">
          <span className="text-gray-600">아이디</span>
          <input
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            autoComplete="username"
          />
        </label>
        <label className="block text-sm">
          <span className="text-gray-600">비밀번호</span>
          <input
            type="password"
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button
          type="submit"
          disabled={login.isPending || !loginId || !password}
          className="w-full rounded bg-gray-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          로그인
        </button>
        {login.isError && <p className="text-sm text-red-700">{errorMessage(login.error)}</p>}
      </form>

      <section className="mt-8">
        <h2 className="text-sm font-semibold text-gray-500">테스트 계정으로 로그인</h2>
        <ul className="mt-2 space-y-2">
          {TEST_ACCOUNTS.map((account) => (
            <li key={account.loginId}>
              <button
                type="button"
                disabled={login.isPending}
                onClick={() => login.mutate(account)}
                className="w-full rounded-lg border border-gray-200 px-4 py-3 text-left text-sm disabled:opacity-50"
              >
                <span className="font-semibold">{account.label}</span>
                <span className="ml-2 text-gray-500">{account.loginId}</span>
                <span className="block text-xs text-gray-500">{account.note}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
