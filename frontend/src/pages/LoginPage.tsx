import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { api } from '../api/endpoints'
import { clearSession, homeOf, setSession, type Session } from '../api/session'
import { ErrorText, HeroBlock, HeroDesc, inputCls, PrimaryButton, SectionHeader } from '../components/ui'

// 테스트 계정 (docs/demo-scenario.md 6장). 심사위원이 버튼 한 번으로 들어가도록 비밀번호를 함께 둔다.
const TEST_ACCOUNTS = [
  { loginId: 'admin', password: 'siktak-admin!', label: '운영자', note: '데모 도구·매칭 관리' },
  { loginId: 'demo01', password: 'siktak1234', label: '시연 참가자', note: '신청 전 상태' },
  { loginId: 'user01', password: 'siktak1234', label: '참가자 user01', note: 'S1 신청·결제 완료' },
  { loginId: 'user14', password: 'siktak1234', label: '참가자 user14', note: '연령 이탈 → 대기 → 일정 이동' },
  { loginId: 'user23', password: 'siktak1234', label: '참가자 user23', note: 'S3 매칭 실패 → 환불 선택' },
]

// 로그인 (/login)
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
    <main className="mx-auto min-h-screen max-w-md bg-paper text-ink">
      <header className="flex items-center justify-between px-5 pt-4 pb-3">
        <span className="font-serif text-xl font-black tracking-[-0.5px]">우연한 식탁</span>
        <Link to="/guide" className="rounded-full border border-accent bg-gold-soft px-3 py-1 text-xs font-bold text-accent">
          📋 시연 가이드
        </Link>
      </header>
      <HeroBlock kicker="Welcome" title={<>우연한 식탁에<br />어서 오세요</>}>
        <HeroDesc>로그인하고 이번 주 식탁을 골라보세요</HeroDesc>
      </HeroBlock>

      {/* 아이디·비밀번호 로그인은 MVP에서 미연동 (테스트 계정으로만 진입). 오버레이로 막고 입력도 비활성 */}
      <div className="relative mx-4 mt-5">
      <form onSubmit={submit} className="space-y-3" aria-disabled="true">
        <fieldset disabled className="space-y-3 border-0 p-0">
        <label className="block text-sm">
          <span className="text-[11px] tracking-[1.5px] text-muted">아이디</span>
          <input
            className={`mt-1 ${inputCls}`}
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            autoComplete="username"
          />
        </label>
        <label className="block text-sm">
          <span className="text-[11px] tracking-[1.5px] text-muted">비밀번호</span>
          <input
            type="password"
            className={`mt-1 ${inputCls}`}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <PrimaryButton type="submit" disabled={login.isPending || !loginId || !password}>
          로그인
        </PrimaryButton>
        </fieldset>
      </form>
      <div className="absolute -inset-1 flex flex-col items-center justify-center gap-1 rounded-2xl bg-paper/80 text-center backdrop-blur-[2px]">
        <span className="rounded-full border border-line bg-card px-3 py-1 text-[11px] font-bold tracking-[1.5px] text-accent">
          MVP 미연동
        </span>
        <p className="text-xs text-body">아래 테스트 계정으로 들어가 주세요.</p>
      </div>
      </div>

      <section className="flex flex-col gap-2 px-4 pt-7 pb-10">
        <SectionHeader title="Test accounts" />
        <p className="text-xs text-muted">탭하면 바로 로그인</p>
        {login.isError && <ErrorText>{errorMessage(login.error)}</ErrorText>}
        <ul className="flex flex-col gap-2">
          {TEST_ACCOUNTS.map((account) => (
            <li key={account.loginId}>
              <button
                type="button"
                disabled={login.isPending}
                onClick={() => login.mutate(account)}
                className={`min-h-[44px] w-full rounded-xl border border-line px-4 py-3 text-left text-sm active:translate-y-px active:bg-gold-soft ${
                  login.isPending && login.variables?.loginId === account.loginId ? 'bg-gold-soft' : 'bg-card disabled:opacity-50'
                }`}
              >
                <span className="font-serif font-black">{account.label}</span>
                <span className="ml-2 text-muted">{account.loginId}</span>
                <span className="block text-xs text-muted">
                  {login.isPending && login.variables?.loginId === account.loginId ? '로그인 중…' : account.note}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
