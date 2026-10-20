import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock } from 'lucide-react'
import { useState } from 'react'
import { errorMessage } from '../api/client'
import { api } from '../api/endpoints'

const KST_MS = 9 * 60 * 60 * 1000

// ISO(UTC) → datetime-local 값(KST, "YYYY-MM-DDTHH:mm")
const toKstInput = (ms: number) => new Date(ms + KST_MS).toISOString().slice(0, 16)
// datetime-local 값(KST) → ISO(UTC)
const fromKstInput = (value: string) => new Date(`${value}:00+09:00`).toISOString()

const label = (ms: number) =>
  new Date(ms).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })

const QUICK = [
  { text: '+1시간', minutes: 60 },
  { text: '+6시간', minutes: 360 },
  { text: '+1일', minutes: 1440 },
  { text: '+3일', minutes: 4320 },
]

// 헤더 가운데의 테스트용 임시 시간. 눌러서 날짜·시간을 옮기면 서버 시계가 바뀌고,
// 그 시각까지 도래한 시간 기반 작업(매칭·확정·안내 등)이 실행된다. 서버 데모 모드가 꺼져 있으면 보이지 않는다.
export default function DemoClock({ align = 'center' }: { align?: 'center' | 'right' }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState('')
  const clock = useQuery({ queryKey: ['demo-clock'], queryFn: api.demoClock, refetchInterval: 30_000, retry: 1 })

  const move = useMutation({
    mutationFn: (iso: string) => api.demoSetClock(iso),
    onSuccess: () => {
      setOpen(false)
      // 시간에 따라 달라지는 화면 데이터를 모두 다시 불러온다
      queryClient.invalidateQueries()
    },
  })

  const reset = useMutation({
    mutationFn: () => api.demoResetAll(),
    onSuccess: () => {
      setOpen(false)
      // 데이터가 시드로 돌아가 계정 ID가 바뀔 수 있다 — 캐시를 비우고 다시 불러온다 (로그인이 무효면 로그인 화면으로 이동)
      queryClient.clear()
      window.location.reload()
    },
  })

  if (!clock.data?.enabled) return null
  const nowMs = Date.parse(clock.data.now)
  // 실제 현재 시각보다 얼마나 앞서 있는지 (offsetSeconds)
  const aheadMin = Math.round(clock.data.offsetSeconds / 60)
  const aheadText =
    aheadMin <= 0
      ? '지금은 실제 현재 시각이에요.'
      : `실제 현재 시각보다 ${Math.floor(aheadMin / 1440) > 0 ? `${Math.floor(aheadMin / 1440)}일 ` : ''}${Math.floor((aheadMin % 1440) / 60)}시간 ${aheadMin % 60}분 앞서 있어요.`

  const openPanel = () => {
    move.reset()
    setValue(toKstInput(nowMs))
    setOpen((o) => !o)
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={openPanel}
        aria-label="테스트용 시간 변경"
        className="flex flex-col items-center rounded-xl border border-dashed border-accent bg-gold-soft px-2.5 py-1 leading-tight"
      >
        <span className="flex items-center gap-1 text-[11px] font-bold text-ink">
          <Clock size={11} strokeWidth={2} />
          {label(nowMs)}
        </span>
        <span className="text-[9px] tracking-wide text-accent">테스트용 시간 · 눌러서 변경</span>
      </button>

      {open && (
        <div className={`absolute top-full z-50 mt-2 w-[280px] max-w-[calc(100vw-24px)] rounded-2xl border border-line bg-card p-3 shadow-lg ${align === 'right' ? 'right-0' : 'left-1/2 -translate-x-1/2'}`}>
          <b className="text-sm">테스트용 시간 이동</b>
          <a href="/guide" className="ml-2 text-[11px] font-bold text-accent underline">
            시연 가이드 열기
          </a>
          <p className="mt-1 rounded-lg border border-accent bg-gold-soft px-2 py-1 text-[11px] font-bold text-accent">
            ⚠ 시간은 앞으로만 갈 수 있어요. 되돌릴 수 없어요.
          </p>
          <p className="mt-1 text-[11px] font-bold text-ink">{aheadText}</p>
          <p className="mt-1 text-[11px] leading-relaxed text-muted">
            실제 시간 대신 이 시간으로 서비스가 동작해요. 옮기면 그 시각까지의 매칭·확정·안내가 자동 실행돼요.
            서버 전체 공통 시간이라 모든 사용자에게 적용돼요.
          </p>
          <input
            type="datetime-local"
            step={60}
            min={toKstInput(nowMs)}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="mt-2 w-full rounded-lg border border-line bg-paper px-2 py-2 text-sm"
          />
          <div className="mt-2 flex flex-wrap gap-1.5">
            {QUICK.map((q) => (
              <button
                key={q.minutes}
                type="button"
                onClick={() => setValue(toKstInput(Math.max(nowMs, value ? Date.parse(`${value}:00+09:00`) : nowMs) + q.minutes * 60_000))}
                className="rounded-full border border-line bg-paper px-2.5 py-1 text-[11px] font-bold"
              >
                {q.text}
              </button>
            ))}
          </div>
          {move.isError && <p className="mt-2 text-xs text-accent">{errorMessage(move.error)}</p>}
          <button
            type="button"
            disabled={reset.isPending}
            onClick={() => {
              if (
                window.confirm(
                  '처음 상태로 초기화할까요?\n\n· 시간이 실제 현재 시각으로 돌아가요.\n· 모든 신청·매칭·알림 데이터가 처음 시드 상태로 돌아가요.\n· 다른 사용자의 화면에도 똑같이 적용돼요.',
                )
              )
                reset.mutate()
            }}
            className="mt-3 min-h-[40px] w-full rounded-xl border border-red-300 bg-paper text-sm font-bold text-red-700 disabled:opacity-50"
          >
            {reset.isPending ? '초기화하는 중…' : '↺ 실제 현재 시각으로 초기화 (데이터도 처음 상태로)'}
          </button>
          {reset.isError && <p className="mt-2 text-xs text-accent">{errorMessage(reset.error)}</p>}
          <div className="mt-2 flex gap-2">
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="min-h-[40px] flex-1 rounded-xl border border-line bg-paper text-sm"
            >
              닫기
            </button>
            <button
              type="button"
              disabled={!value || move.isPending}
              onClick={() => move.mutate(fromKstInput(value))}
              className="min-h-[40px] flex-[2] rounded-xl bg-ink text-sm font-bold text-cream disabled:opacity-50"
            >
              {move.isPending ? '이동 중…' : '이 시간으로 이동'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
