import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { DemoStatus } from '../../api/types'
import SessionBar from '../../components/SessionBar'
import { formatDateTime } from '../../lib/format'

// 데모 도구 (/admin/demo, docs/demo-scenario.md 1장). 시계를 앞당기면 같은 스케줄러 코드가 도래한 작업을 바로 실행한다.
export default function AdminDemoPage() {
  const queryClient = useQueryClient()
  const status = useQuery({ queryKey: ['demo-status'], queryFn: api.demoStatus })
  const onDone = (data: DemoStatus) => {
    queryClient.setQueryData(['demo-status'], data)
    // 매칭 결과 등 다른 화면 데이터도 새로 읽는다
    queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'demo-status' })
  }
  const reset = useMutation({ mutationFn: api.demoReset, onSuccess: onDone })
  const jump = useMutation({ mutationFn: api.demoJump, onSuccess: onDone })
  const advance = useMutation({ mutationFn: api.demoAdvance, onSuccess: onDone })
  const busy = reset.isPending || jump.isPending || advance.isPending
  const error = reset.error ?? jump.error ?? advance.error ?? status.error

  const offset = status.data?.offsetSeconds ?? 0
  const offsetText = `${Math.floor(offset / 86400)}일 ${Math.floor((offset % 86400) / 3600)}시간 ${Math.floor((offset % 3600) / 60)}분`

  return (
    <main className="min-h-screen bg-gray-50 p-6">
      <header className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-sm text-gray-500">
            운영자 홈
          </Link>
          <h1 className="text-2xl font-bold">데모 도구</h1>
        </div>
        <SessionBar />
      </header>

      <section className="mt-4 grid gap-3 md:grid-cols-3">
        <Card title="데모 시각">
          <p className="text-xl font-semibold">{formatDateTime(status.data?.now)}</p>
          <p className="text-xs text-gray-500">실제 시각보다 {offsetText} 앞섬</p>
        </Card>
        <Card title="시드">
          <p className="text-xl font-semibold">{status.data?.seedVersion ?? '없음'}</p>
          <p className="text-xs text-gray-500">T0 {formatDateTime(status.data?.t0)}</p>
        </Card>
        <Card title="다음 예정 작업">
          <ul className="space-y-0.5 text-sm">
            {status.data?.upcomingJobs.length === 0 && <li className="text-gray-500">없음</li>}
            {status.data?.upcomingJobs.map((j) => (
              <li key={`${j.jobType}-${j.targetId}`}>
                {j.jobType} #{j.targetId} · {formatDateTime(j.dueAt)}
              </li>
            ))}
          </ul>
        </Card>
      </section>

      {error && <p className="mt-3 text-sm text-red-700">{errorMessage(error)}</p>}

      <section className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
        <h2 className="font-semibold">시나리오 점프</h2>
        <p className="mt-1 text-xs text-gray-500">
          버튼을 누르면 데모 시각이 해당 시점으로 이동하고, 그때까지 도래한 작업(매칭 등)이 바로 실행됩니다. 앞으로만 이동할 수 있어요.
        </p>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          {status.data?.jumps.map((j) => (
            <button
              key={j.code}
              type="button"
              disabled={busy || j.reached}
              onClick={() => jump.mutate(j.code)}
              className="rounded-lg border border-gray-300 p-3 text-left text-sm hover:bg-amber-50 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
            >
              <span className="font-semibold">⏩ {j.code}</span> {j.label}
              <span className="block text-xs">{formatDateTime(j.at)} {j.reached && '· 지남'}</span>
            </button>
          ))}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" disabled={busy} onClick={() => advance.mutate(60)}
            className="rounded border border-gray-300 px-3 py-2 text-sm disabled:text-gray-400">
            +1시간
          </button>
          <button type="button" disabled={busy} onClick={() => advance.mutate(1440)}
            className="rounded border border-gray-300 px-3 py-2 text-sm disabled:text-gray-400">
            +1일
          </button>
          <Link to="/admin/slots" className="rounded border border-gray-300 px-3 py-2 text-sm">
            회차·매칭 결과 보기 →
          </Link>
        </div>
      </section>

      <section className="mt-4 rounded-lg border border-red-200 bg-white p-4">
        <h2 className="font-semibold">시드 초기화</h2>
        <p className="mt-1 text-xs text-gray-500">
          모든 데이터를 지우고 demo-v1 시드(참가자 30명·회차 5개)를 다시 넣습니다. 데모 시각도 지금으로 돌아갑니다.
        </p>
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            if (window.confirm('모든 데이터를 지우고 시드를 다시 넣을까요?')) reset.mutate()
          }}
          className="mt-3 rounded bg-red-700 px-4 py-2 text-sm text-white disabled:bg-gray-400"
        >
          {reset.isPending ? '초기화 중…' : '시드 초기화'}
        </button>
      </section>
    </main>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <p className="text-xs text-gray-500">{title}</p>
      <div className="mt-1">{children}</div>
    </div>
  )
}
