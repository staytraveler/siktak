import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { api } from '../api/endpoints'
import { getSession, setSession, type Session } from '../api/session'
import DemoClock from '../components/DemoClock'
import { formatDateTime } from '../lib/format'

// 시연 가이드 (/guide): PRD 18장 "해커톤 완료 조건" 12단계를 누르는 순서대로 따라가게 한다.
// 로그인 없이도 읽을 수 있고, 로그인하면 단계에 필요한 시간 이동·일괄 실행 버튼이 켜진다.

type Account = { loginId: string; password: string; label: string }
const DEMO: Account = { loginId: 'demo01', password: 'siktak1234', label: '심사위원 참가자 (demo01)' }
const ADMIN: Account = { loginId: 'admin', password: 'siktak-admin!', label: '운영자 (admin)' }
const USER14: Account = { loginId: 'user14', password: 'siktak1234', label: 'user14 (대기→일정 이동)' }
const USER23: Account = { loginId: 'user23', password: 'siktak1234', label: 'user23 (환불)' }
const USER24: Account = { loginId: 'user24', password: 'siktak1234', label: 'user24 (미응답→보관)' }

type Action =
  | { kind: 'link'; to: string; label: string }
  | { kind: 'jump'; code: string; label: string }
  | { kind: 'bulk'; id: 'checkin' | 'feedback' | 'round2'; label: string }
  | { kind: 'login'; account: Account }

interface Step {
  n: number
  title: string
  prd: string
  who: string
  todo: string[]
  expect: string
  actions: Action[]
  /** 이 단계가 끝났다고 볼 수 있는 점프 코드 (도달했으면 "지나감" 표시) */
  doneAfter?: string
}

const STEPS: Step[] = [
  {
    n: 1,
    title: '참가자가 날짜·지역을 골라 신청한다',
    prd: 'APP-01~04 · PAY-01·02',
    who: '참가자 (일반 창)',
    todo: [
      '참가자(demo01)로 로그인합니다.',
      '"일정"에서 성수(S1)를 고르고 [다음 →] → [결제하기]를 누릅니다. (결제는 모의라 버튼 한 번이면 완료돼요)',
    ],
    expect: '"결제 완료 · 매칭 대기"와 매칭 예정 시각, 환불 규정(2일 전 이후 환불 불가)이 보입니다.',
    actions: [
      { kind: 'login', account: DEMO },
      { kind: 'link', to: '/app/slots', label: '일정 고르러 가기 →' },
    ],
  },
  {
    n: 2,
    title: '매칭 마감 시각이 되면 자동으로 매칭이 실행된다',
    prd: 'MAT-01',
    who: '운영자 (시크릿 창) 또는 아무 계정',
    todo: [
      '[1회차 매칭 마감으로 이동]을 누르면 서버 시간이 매칭 마감 시각으로 옮겨지고, 운영자가 아무것도 누르지 않아도 매칭이 실행돼요.',
    ],
    expect: '매칭 모니터에 실행 1건이 "스케줄러(자동)"로 생겼습니다.',
    doneAfter: 'J1',
    actions: [
      { kind: 'jump', code: 'J1', label: '⏩ 1회차 매칭 마감으로 이동' },
      { kind: 'link', to: '/admin/matching', label: '매칭 모니터 보기 →' },
    ],
  },
  {
    n: 3,
    title: '필수 조건과 우선순위로 4~6인 테이블이 만들어진다',
    prd: 'MAT-02~07 · MAT-09',
    who: '운영자',
    todo: ['매칭 모니터에서 실행을 열고, 테이블 상세에서 구성원·연령 차·성비·MBTI·공통 관심사·점수를 봅니다.'],
    expect: 'S1에 테이블 3개, S2에 1개. 연령 차 8살 이내, user05와 user06은 서로 다른 테이블.',
    actions: [{ kind: 'link', to: '/admin/matching', label: '매칭 모니터 →' }],
  },
  {
    n: 4,
    title: '4명 미만·조건 미충족은 해제하고 다른 테이블로 재배치한다',
    prd: 'STA-03 · MAT-08 · MAT-12',
    who: '운영자',
    todo: ['매칭 모니터 실행 상세의 "배치 로그"에서 재배치된 사람을 봅니다.'],
    expect: 'user15가 연령 조건으로 S2에서 S1로 재배치, user14는 대기 복귀, user16은 미결제라 제외.',
    actions: [{ kind: 'link', to: '/admin/matching', label: '배치 로그 보기 →' }],
  },
  {
    n: 5,
    title: '조건을 통과한 테이블은 운영자 승인 없이 자동 확정된다',
    prd: 'MAT-10',
    who: '운영자',
    todo: ['대시보드의 자동 확정률·운영자 개입률을 봅니다.'],
    expect: '모든 테이블이 확정 상태이고, 확정에 운영자가 개입한 건은 0%입니다.',
    actions: [{ kind: 'link', to: '/admin', label: '운영 대시보드 →' }],
  },
  {
    n: 6,
    title: '식당을 자동 배정하고 단체방을 자동 생성한다',
    prd: 'INF-02·04 · MAT-13',
    who: '운영자',
    todo: [
      '테이블 상세에서 배정된 식당·예약명과 단체방 메시지를 봅니다.',
      '예외 처리함의 "식당 부족 1건"은 시연을 위해 일부러 만든 예외예요. 식당 재고에서 수용 수를 올리면 해결돼요.',
    ],
    expect: '식당이 자동 배정되고, 식당이 부족한 테이블 1개만 예외로 올라옵니다(운영자가 해결하는 유일한 예외).',
    actions: [
      { kind: 'link', to: '/admin/exceptions', label: '예외 처리함 →' },
      { kind: 'link', to: '/admin/venues', label: '식당 재고 →' },
    ],
  },
  {
    n: 7,
    title: '참가자가 단체방에서 결과·식당·큐레이션·안내를 확인한다',
    prd: 'INF-01·03·05·06',
    who: '참가자',
    todo: ['참가자(demo01)로 알림에서 "테이블이 확정됐어요"를 열고 단체방에 들어갑니다.'],
    expect: '식당·집합 시간·예약명, 큐레이션(연령대·성비·공통 관심사), 취소 정책 공지. 다른 참가자의 상세 정보와 점수는 보이지 않아요.',
    actions: [
      { kind: 'login', account: DEMO },
      { kind: 'link', to: '/app/notifications', label: '알림 →' },
      { kind: 'link', to: '/app', label: '내 신청(단체방) →' },
    ],
  },
  {
    n: 8,
    title: '매칭 실패자에게 대체 일정을 추천하고 이동·환불을 자동 처리한다',
    prd: 'MAT-11 · STA-04~06 · PAY-03',
    who: '참가자 3명 (user14·23·24)',
    todo: [
      '[S3 매칭 마감으로 이동]을 누릅니다.',
      'user14로 로그인해 추천 일정(강남)으로 [이동], user23으로 [환불], user24는 아무것도 하지 않습니다.',
    ],
    expect: 'user14는 결제 유지·추가 결제 없이 새 일정 대기, user23은 운영자 승인 없이 환불 완료, user24는 선택 대기(기한이 지나면 이용권으로 자동 보관).',
    doneAfter: 'J2',
    actions: [
      { kind: 'jump', code: 'J2', label: '⏩ S3 매칭 마감으로 이동' },
      { kind: 'login', account: USER14 },
      { kind: 'login', account: USER23 },
      { kind: 'login', account: USER24 },
      { kind: 'link', to: '/app', label: '내 신청 보기 →' },
    ],
  },
  {
    n: 9,
    title: '행사 전 리마인드와 행사 중 대화 콘텐츠가 자동 게시된다',
    prd: 'INF-07·08 · CON-01~03',
    who: '참가자',
    todo: ['[행사 1일 전]과 [행사 시작]으로 차례로 이동한 뒤 참가자(demo01) 알림과 단체방을 봅니다.'],
    expect: '리마인드(시간·장소·예약명·취소 정책) 알림, 단체방에 대화 주제 3개 + 아이스브레이킹 게시.',
    doneAfter: 'J4',
    actions: [
      { kind: 'jump', code: 'J3', label: '⏩ 행사 1일 전으로 이동' },
      { kind: 'jump', code: 'J4', label: '⏩ 행사 시작으로 이동' },
      { kind: 'login', account: DEMO },
      { kind: 'link', to: '/app/notifications', label: '알림 →' },
    ],
  },
  {
    n: 10,
    title: '행사 후 참가자가 체크인하고 만족도·사람별 선호를 입력한다',
    prd: 'FDB-01~04',
    who: '참가자 + 운영자',
    todo: [
      '참가자(demo01) 신청 상세에서 [체크인]을 누릅니다.',
      '[체크인 일괄] → [행사 종료로 이동] 후, 참가자가 피드백(테이블·대화·식당 만족도)과 동석자 선호(다시 만나고 싶지 않은 사람 1명)를 입력합니다.',
      '[피드백 일괄]로 나머지 참석자의 기본 피드백을 채웁니다.',
    ],
    expect: '체크인하지 않은 user13은 노쇼 "후보"로만 표시되고 운영자가 확정합니다. 선호 선택은 상대에게 보이지 않아요.',
    doneAfter: 'J5',
    actions: [
      { kind: 'login', account: DEMO },
      { kind: 'bulk', id: 'checkin', label: '체크인 일괄' },
      { kind: 'jump', code: 'J5', label: '⏩ 행사 종료로 이동' },
      { kind: 'bulk', id: 'feedback', label: '피드백 일괄' },
      { kind: 'link', to: '/app', label: '내 신청(피드백) →' },
      { kind: 'link', to: '/admin/feedback', label: '피드백 요약 →' },
    ],
  },
  {
    n: 11,
    title: '다음 매칭에서 이전 만남과 피드백이 반영된다',
    prd: 'MAT-05·06 · RET-01·02',
    who: '운영자 + 참가자',
    todo: [
      '[2회차 신청 투입] 후 [2회차 매칭 마감으로 이동]을 누릅니다. (demo01은 1회차 참석자라 자동 포함돼요)',
      '매칭 모니터에서 S4 테이블을 열어 "피하고 싶은 사람" 쌍이 다른 테이블인지 봅니다.',
    ],
    expect: '"다시 만나고 싶지 않음" 쌍이 분리되고 1회차 동석자가 분산됩니다. 참가 이력과 다음 식탁 추천(S6·S7)도 보여요.',
    doneAfter: 'J6',
    actions: [
      { kind: 'bulk', id: 'round2', label: '2회차 신청 투입' },
      { kind: 'jump', code: 'J6', label: '⏩ 2회차 매칭 마감으로 이동' },
      { kind: 'link', to: '/admin/matching', label: '매칭 모니터 →' },
      { kind: 'link', to: '/app/history', label: '참가 이력·추천 →' },
    ],
  },
  {
    n: 12,
    title: '정상 흐름은 운영자 개입 없이 끝나고, 해결되지 않은 예외만 운영자 화면에 나타난다',
    prd: 'MAT-13',
    who: '운영자',
    todo: ['대시보드의 자동화 지표와 예외 처리함을 봅니다.'],
    expect: '예외 처리함에는 식당 부족(6단계에서 처리)과 체크인하지 않은 노쇼 후보만 있습니다. 매칭·확정·환불·안내는 사람이 건드리지 않았어요.',
    actions: [
      { kind: 'link', to: '/admin', label: '운영 대시보드 →' },
      { kind: 'link', to: '/admin/exceptions', label: '예외 처리함 →' },
    ],
  },
]

const btn =
  'inline-flex min-h-[40px] items-center justify-center rounded-xl border border-line px-3 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-40'

export default function GuidePage() {
  const queryClient = useQueryClient()
  const [session, setSessionState] = useState<Session | null>(() => getSession())
  const [message, setMessage] = useState<string | null>(null)
  const status = useQuery({
    queryKey: ['guide-status', session?.user.id],
    queryFn: api.guideStatus,
    enabled: !!session,
    retry: false,
    refetchInterval: 20_000,
  })
  const reached = new Set(status.data?.jumps.filter((j) => j.reached).map((j) => j.code))

  const login = useMutation({
    mutationFn: (a: Account) => api.login(a.loginId, a.password),
    onSuccess: (s) => {
      setSession(s)
      setSessionState(s)
      queryClient.clear()
      setMessage(`${s.user.name}(${s.user.loginId})으로 로그인했어요.`)
    },
  })
  const run = useMutation({
    mutationFn: async (action: Extract<Action, { kind: 'jump' | 'bulk' }>) => {
      if (action.kind === 'jump') {
        await api.guideJump(action.code)
        return `${action.label} 완료`
      }
      const fn = action.id === 'checkin' ? api.guideCheckInAll : action.id === 'feedback' ? api.guideFeedbackAll : api.guideRound2
      const r = await fn()
      return `${action.label}: ${r.message}`
    },
    onSuccess: (text) => {
      setMessage(text)
      queryClient.invalidateQueries()
    },
    onError: (e) => setMessage(errorMessage(e)),
  })
  const busy = login.isPending || run.isPending

  return (
    <main className="mx-auto min-h-screen max-w-3xl bg-paper px-4 pb-16 text-ink">
      <header className="flex items-center justify-between gap-2 pt-4 pb-2">
        <Link to="/login" className="font-serif text-xl font-black tracking-[-0.5px]">
          우연한 식탁
        </Link>
        {session && <DemoClock align="right" />}
      </header>
      <h1 className="mt-2 font-serif text-[26px] leading-tight font-black tracking-[-1px]">시연 가이드</h1>
      <p className="mt-1 text-sm text-body">
        PRD 18장 "해커톤 완료 조건" 12단계를 순서대로 따라가는 안내예요. 단계마다 <b>무엇을 누르고 무엇이 보여야 하는지</b> 적어 두었어요.
      </p>

      <section className="mt-4 rounded-2xl border border-line bg-card p-4 text-sm">
        <b>시작하기 전에</b>
        <ul className="mt-2 list-disc space-y-1.5 pl-5 text-body">
          <li>
            <b>창을 두 개</b> 쓰세요. 참가자는 일반 창, 운영자는 <b>시크릿 창</b>. 같은 브라우저에서 계정을 바꾸면 다른 창도 같은 계정이 돼요.
          </li>
          <li>
            이 서비스의 시간은 <b>헤더 가운데 점선 칩(테스트용 시간)</b>으로 옮겨요. 매칭 마감·행사 시작 같은 시각을 기다리지 않고 지나가면서
            그때 자동으로 돌아가는 일들이 실행돼요. <b>시간은 앞으로만 가고, 모든 사용자가 같이 써요.</b>
          </li>
          <li>
            처음부터 다시 보려면 칩을 눌러 <b>"실제 현재 시각으로 초기화"</b>를 누르세요. 시간과 데이터가 처음 상태로 돌아가요.
          </li>
          <li>
            운영자 화면의 "예외 처리함"은 정상 흐름에서는 거의 비어 있어요. 시연 중 보이는 <b>식당 부족 1건</b>(6단계)과{' '}
            <b>체크인하지 않은 노쇼 후보</b>(10단계)만 예상된 예외예요.
          </li>
        </ul>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button type="button" disabled={busy} className={`${btn} bg-paper`} onClick={() => login.mutate(DEMO)}>
            참가자(demo01)로 시작
          </button>
          <button type="button" disabled={busy} className={`${btn} bg-paper`} onClick={() => login.mutate(ADMIN)}>
            운영자(admin)로 시작
          </button>
          <span className="text-xs text-muted">
            {session ? `현재: ${session.user.name} (${session.user.loginId})` : '로그인 전 — 로그인하면 단계별 실행 버튼이 켜져요'}
          </span>
        </div>
      </section>

      {(message || login.isError || status.isError) && (
        <p className="mt-3 rounded-xl bg-gold-soft px-3 py-2 text-sm font-bold">
          {message ?? errorMessage(login.error ?? status.error)}
        </p>
      )}

      <ol className="mt-4 flex flex-col gap-3">
        {STEPS.map((s) => {
          const done = s.doneAfter ? reached.has(s.doneAfter) : false
          return (
            <li key={s.n} className="rounded-2xl border border-line bg-card p-4">
              <div className="flex items-start gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent font-serif text-base font-black text-cream">
                  {s.n}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-serif text-base leading-snug font-black">{s.title}</h2>
                    {done && <span className="rounded-full bg-sage px-2 py-0.5 text-[11px] font-bold">시간 지나감</span>}
                  </div>
                  <p className="mt-0.5 text-[11px] text-muted">
                    {s.prd} · {s.who}
                  </p>
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-body">
                    {s.todo.map((t) => (
                      <li key={t}>{t}</li>
                    ))}
                  </ul>
                  <p className="mt-2 rounded-lg bg-paper px-3 py-2 text-sm">
                    <b>이렇게 보여야 해요 </b>
                    {s.expect}
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {s.actions.map((a, i) => {
                      if (a.kind === 'link') {
                        return (
                          <Link key={i} to={a.to} className={`${btn} bg-paper`}>
                            {a.label}
                          </Link>
                        )
                      }
                      if (a.kind === 'login') {
                        return (
                          <button key={i} type="button" disabled={busy} className={`${btn} bg-paper`} onClick={() => login.mutate(a.account)}>
                            {a.account.label}로 로그인
                          </button>
                        )
                      }
                      return (
                        <button
                          key={i}
                          type="button"
                          disabled={busy || !session}
                          title={session ? undefined : '먼저 로그인해 주세요'}
                          className={`${btn} border-accent bg-gold-soft text-accent`}
                          onClick={() => run.mutate(a)}
                        >
                          {a.label}
                        </button>
                      )
                    })}
                  </div>
                </div>
              </div>
            </li>
          )
        })}
      </ol>

      {status.data && (
        <p className="mt-4 text-xs text-muted">
          서버 시각 {formatDateTime(status.data.now)} · 이동한 시간 {Math.round(status.data.offsetSeconds / 3600)}시간
        </p>
      )}
    </main>
  )
}
