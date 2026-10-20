import { useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { setSession, type Session } from '../api/session'
import { errorMessage } from '../api/client'
import { formatDateTime } from '../lib/format'

// 시연 가이드 (/guide): PRD 18장 "해커톤 완료 조건"을 7단계로 묶어 한 단계씩 따라가게 한다.
// - 지금 누를 버튼은 하나만 켜진다. 끝난 단계는 ✓로 접히고 다음 단계는 잠겨 있다.
// - 완료 여부는 클릭이 아니라 서버(DB) 상태로 판정한다 → 중간에 실패하거나 꼬여도 정확한 곳에서 이어진다.
// - 시연 계정(demo01·user14·user23·admin)의 동작은 가이드가 대신 실행한다 (로그인 계정을 바꿀 필요 없음).

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL })
const tokens: Record<string, string> = {}
const PASSWORDS: Record<string, string> = {
  demo01: 'siktak1234',
  user14: 'siktak1234',
  user23: 'siktak1234',
  admin: 'siktak-admin!',
}

async function login(loginId: string): Promise<Session> {
  const res = await http.post<Session>('/api/v1/auth/login', { loginId, password: PASSWORDS[loginId] })
  tokens[loginId] = res.data.accessToken
  return res.data
}
async function call<T = unknown>(as: string, method: 'get' | 'post' | 'put', path: string, data?: unknown): Promise<T> {
  if (!tokens[as]) await login(as)
  try {
    return (await http.request<T>({ method, url: path, data, headers: { Authorization: `Bearer ${tokens[as]}` } })).data
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 401) {
      // 초기화 등으로 토큰이 무효가 되면 한 번 다시 로그인한다
      delete tokens[as]
      await login(as)
      return (await http.request<T>({ method, url: path, data, headers: { Authorization: `Bearer ${tokens[as]}` } })).data
    }
    throw e
  }
}

interface Progress {
  now: string
  offsetSeconds: number
  jumps: { code: string; label: string; at: string; reached: boolean }[]
  demoApplicationId: number | null
  demoPayment: string | null
  demoMatch: string | null
  demoAttendance: string | null
  demoFeedback: boolean
  demoAvoid: boolean
  user14Offered: boolean
  user14Transferred: boolean
  user23Offered: boolean
  user23Refunded: boolean
  round2Applied: boolean
  stats: {
    autoActions: number
    operatorActions: number
    participantActions: number
    matchRuns: number
    tablesConfirmed: number
    venuesAssigned: number
    chatRooms: number
    notificationsSent: number
    remindersSent: number
    contentsPosted: number
    refundsDone: number
    reallocated: number
    openExceptions: number
    venueExceptions: number
    noShowCandidates: number
    applicants: number
    paid: number
  }
}

const fetchProgress = () => call<Progress>('demo01', 'get', '/api/v1/demo/progress')
const jump = (code: string) => call('demo01', 'post', `/api/v1/demo/jump/${code}`)

// ---- 단계에서 대신 실행하는 동작들 ----

async function applyAndPay(p: Progress) {
  let appId = p.demoApplicationId
  if (appId == null) {
    const slots = await call<{ id: number; regionName: string; startsAt: string }[]>('demo01', 'get', '/api/v1/slots')
    const s1 = slots.filter((s) => s.regionName === '성수').sort((a, b) => a.startsAt.localeCompare(b.startsAt))[0]
    if (!s1) throw new Error('신청할 수 있는 성수 회차가 없어요. 위의 "처음부터 다시 시작"을 눌러 주세요.')
    appId = (await call<{ id: number }>('demo01', 'post', '/api/v1/applications', { slotIds: [s1.id], passType: 'SINGLE' })).id
  }
  await call('demo01', 'post', `/api/v1/applications/${appId}/payments/mock-approve`)
}

async function resolveFailed() {
  // user14: 추천 일정으로 이동, user23: 환불. user24는 일부러 아무것도 하지 않는다 (기한이 지나면 자동 보관).
  const apps14 = await call<{ id: number; matchStatus: string | null }[]>('user14', 'get', '/api/v1/me/applications')
  const a14 = apps14.find((a) => a.matchStatus === 'ALTERNATIVE_OFFERED')
  if (a14) {
    const r = await call<{ offeredSlots: { slot: { id: number }; available: boolean }[] }>(
      'user14', 'get', `/api/v1/me/applications/${a14.id}/resolution`)
    const target = r.offeredSlots.find((o) => o.available)
    if (!target) throw new Error('user14에게 이동할 수 있는 추천 일정이 없어요.')
    await call('user14', 'post', `/api/v1/me/applications/${a14.id}/resolution`, { choice: 'TRANSFER', slotId: target.slot.id })
  }
  const apps23 = await call<{ id: number; matchStatus: string | null }[]>('user23', 'get', '/api/v1/me/applications')
  const a23 = apps23.find((a) => a.matchStatus === 'ALTERNATIVE_OFFERED')
  if (a23) await call('user23', 'post', `/api/v1/me/applications/${a23.id}/resolution`, { choice: 'REFUND' })
}

async function checkIn(p: Progress) {
  if (p.demoApplicationId == null) throw new Error('신청 정보가 없어요.')
  await call('demo01', 'post', `/api/v1/me/applications/${p.demoApplicationId}/check-in`)
  await call('demo01', 'post', '/api/v1/demo/check-in-all')
}

async function giveFeedback(p: Progress) {
  const id = p.demoApplicationId
  if (id == null) throw new Error('신청 정보가 없어요.')
  await call('demo01', 'post', '/api/v1/feedbacks', {
    applicationId: id, tableRating: 5, conversationRating: 4, venueRating: 4, rejoinIntent: 5, recommendScore: 9,
    comment: '시연 가이드가 대신 입력한 피드백',
  })
  const companions = await call<{ companionId: number }[]>('demo01', 'get', `/api/v1/me/applications/${id}/companions`)
  if (companions.length > 0) {
    const choices = [{ companionId: companions[0].companionId, preference: 'AVOID' }]
    if (companions.length > 1) choices.push({ companionId: companions[1].companionId, preference: 'WANT_AGAIN' })
    await call('demo01', 'put', `/api/v1/me/applications/${id}/person-preferences`, { choices })
  }
  await call('demo01', 'post', '/api/v1/demo/feedback-all')
}

// ---- 단계 정의 ----

interface Sub {
  label: string
  explain: string
  done: boolean
  run: () => Promise<unknown>
}
interface View {
  label: string
  as: 'demo01' | 'admin' | 'user14' | 'user23'
  to: string
}
interface Step {
  n: number
  title: string
  prd: string
  intro: string
  subs: Sub[]
  /** 이 단계에서 순서가 꼬였을 때의 안내 (있으면 버튼 대신 초기화를 권한다) */
  blocked?: string
  result: string[]
  views: View[]
}

function buildSteps(p: Progress): Step[] {
  const reached = (code: string) => p.jumps.find((j) => j.code === code)?.reached ?? false
  const paid = p.demoPayment === 'PAID'
  const s = p.stats
  return [
    {
      n: 1,
      title: '참가자가 일정을 골라 신청하고 결제한다',
      prd: 'PRD 1 · APP-01~04 · PAY-01·02',
      intro: '참가자 "심사위원(demo01)"이 성수 회차에 신청하고 모의 결제까지 마칩니다. (결제는 버튼 한 번)',
      blocked: !paid && reached('J1') ? '이미 매칭 시간이 지나 신청할 수 없어요.' : undefined,
      subs: [
        {
          label: '성수 회차에 신청하고 결제하기',
          explain: 'demo01이 [일정 선택 → 신청 → 모의 결제]를 한 번에 진행해 드려요.',
          done: paid,
          run: () => applyAndPay(p),
        },
      ],
      result: paid ? ['신청과 결제가 완료되어 "매칭 대기" 상태가 됐어요.', `신청자 ${s.applicants}명 · 결제 완료 ${s.paid}건`] : [],
      views: [{ label: '내 신청 보기', as: 'demo01', to: '/app' }],
    },
    {
      n: 2,
      title: '마감 시각이 되면 시스템이 자동으로 매칭·확정·식당 배정·단체방 생성까지 한다',
      prd: 'PRD 2~7 · MAT-01~13 · INF-01~06',
      intro: '여기서는 사람이 아무것도 하지 않아요. 시간만 매칭 마감 시각으로 옮기면, 시스템이 스스로 돌아갑니다.',
      subs: [
        {
          label: '시간을 매칭 마감 시각으로 옮기기',
          explain: '운영자가 "매칭 시작"을 누르지 않아도 마감 시각이 되면 자동으로 실행돼요.',
          done: reached('J1'),
          run: () => jump('J1'),
        },
      ],
      result: reached('J1')
        ? [
            `자동 매칭 ${s.matchRuns}회 실행 → 테이블 ${s.tablesConfirmed}개를 운영자 승인 없이 자동 확정`,
            `조건이 안 맞아 다른 테이블로 자동 재배치된 사람 ${s.reallocated}명`,
            `식당 자동 배정 ${s.venuesAssigned}/${s.tablesConfirmed}개 테이블 · 단체방 ${s.chatRooms}개 자동 생성 · 알림 ${s.notificationsSent}건 발송`,
            s.venueExceptions > 0
              ? `식당이 부족했던 테이블 ${s.venueExceptions}개만 예외 처리함에 올라갔어요 (시연을 위해 일부러 만든 예외)`
              : '예외 없이 모두 자동 처리됐어요.',
          ]
        : [],
      views: [
        { label: '내 단체방 보기 (참가자)', as: 'demo01', to: '/app/chats' },
        { label: '매칭 모니터 보기 (운영자)', as: 'admin', to: '/admin/matching' },
        { label: '예외 처리함 (운영자)', as: 'admin', to: '/admin/exceptions' },
      ],
    },
    {
      n: 3,
      title: '매칭되지 못한 참가자에게 다른 일정을 추천하고, 이동·환불을 자동 처리한다',
      prd: 'PRD 8 · MAT-11 · STA-04~06 · PAY-03',
      intro: '성수 3회차(S3)는 신청자가 부족해 매칭되지 않아요. 실패한 참가자에게 대체 일정이 제안되고, 선택은 자동 처리됩니다.',
      subs: [
        {
          label: '3회차 매칭 마감 시각으로 옮기기',
          explain: '신청자가 부족한 회차의 참가자 3명(user14·23·24)이 "매칭 실패 → 대체 일정 제안" 상태가 돼요.',
          done: reached('J2'),
          run: () => jump('J2'),
        },
        {
          label: 'user14는 일정 이동, user23은 환불 선택하기',
          explain: '두 사람이 각자 선택한 것으로 가이드가 대신 눌러 드려요. user24는 일부러 선택하지 않아요(기한 후 이용권 자동 보관).',
          done: p.user14Transferred && p.user23Refunded,
          run: resolveFailed,
        },
      ],
      result:
        p.user14Transferred && p.user23Refunded
          ? [
              'user14: 결제를 유지한 채 새 일정의 매칭 대기로 자동 전환 (추가 결제 없음)',
              `user23: 운영자 승인 없이 원결제수단으로 자동 환불 완료 (환불 ${s.refundsDone}건)`,
              'user24: 선택하지 않아도 기한이 지나면 이용권으로 자동 보관돼요.',
            ]
          : [],
      views: [
        { label: 'user14 화면 보기', as: 'user14', to: '/app' },
        { label: 'user23 화면 보기', as: 'user23', to: '/app' },
        { label: '결제·환불 내역 (운영자)', as: 'admin', to: '/admin/payments' },
      ],
    },
    {
      n: 4,
      title: '행사 전 리마인드와 행사 중 대화 콘텐츠가 자동으로 올라온다',
      prd: 'PRD 9 · INF-07·08 · CON-01~03',
      intro: '시간을 행사 하루 전, 행사 시작으로 차례로 옮기면 안내가 알아서 게시돼요.',
      subs: [
        { label: '행사 1일 전으로 옮기기', explain: '시간·장소·예약명·취소 정책 리마인드가 자동 발송돼요.', done: reached('J3'), run: () => jump('J3') },
        { label: '행사 시작 시각으로 옮기기', explain: '단체방에 대화 주제 3개와 아이스브레이킹이 자동 게시돼요.', done: reached('J4'), run: () => jump('J4') },
      ],
      result: reached('J4')
        ? [`리마인드 알림 ${s.remindersSent}건 자동 발송`, `대화 주제·아이스브레이킹 ${s.contentsPosted}개 단체방에 자동 게시`]
        : reached('J3')
          ? [`리마인드 알림 ${s.remindersSent}건 자동 발송`]
          : [],
      views: [
        { label: '알림 보기 (참가자)', as: 'demo01', to: '/app/notifications' },
        { label: '내 단체방 보기 (참가자)', as: 'demo01', to: '/app/chats' },
      ],
    },
    {
      n: 5,
      title: '행사 후 참가자가 체크인하고 만족도와 사람별 선호를 입력한다',
      prd: 'PRD 10 · FDB-01~04',
      intro: '체크인 → 행사 종료 → 피드백 순서예요. 입력은 가이드가 대신 해 드립니다.',
      subs: [
        {
          label: '체크인하기',
          explain: 'demo01과 다른 참석자가 앱에서 체크인해요. (user13은 일부러 안 해서 노쇼 "후보"가 돼요)',
          done: p.demoAttendance === 'ATTENDED',
          run: () => checkIn(p),
        },
        { label: '행사 종료 시각으로 옮기기', explain: '체크인하지 않은 사람은 노쇼 "후보"로만 표시되고, 운영자가 확정해요.', done: reached('J5'), run: () => jump('J5') },
        {
          label: '만족도·사람별 선호 입력하기',
          explain: '테이블·대화·식당 만족도와, 같은 테이블 1명은 "다시 만나고 싶지 않음", 1명은 "다시 만나고 싶음"으로 비공개 저장해요.',
          done: p.demoFeedback && p.demoAvoid,
          run: () => giveFeedback(p),
        },
      ],
      result:
        p.demoFeedback && p.demoAvoid
          ? [`피드백 저장 완료 · 선호는 상대에게 보이지 않아요`, `체크인하지 않은 노쇼 후보 ${s.noShowCandidates}명이 예외 처리함에 올라갔어요 (운영자는 후보만 확인)`]
          : [],
      views: [
        { label: '내 신청(피드백) 보기 (참가자)', as: 'demo01', to: '/app' },
        { label: '피드백 요약 (운영자)', as: 'admin', to: '/admin/feedback' },
      ],
    },
    {
      n: 6,
      title: '다음 매칭에서 이전 만남과 피드백이 반영된다',
      prd: 'PRD 11 · MAT-05·06 · RET-01·02',
      intro: '1회차 참석자들이 2회차에 다시 신청하면, 앞에서 남긴 "피하고 싶음"·"다시 만나고 싶음"과 이전 만남이 다음 매칭에 반영돼요. 참여가 쌓일수록 매칭에 쓰는 정보가 늘어나요.',
      subs: [
        { label: '2회차 신청 넣기', explain: '1회차 참석자 전원과 새 참가자 2명이 2회차에 신청·결제해요.', done: p.round2Applied, run: () => call('demo01', 'post', '/api/v1/demo/round2-applications') },
        { label: '2회차 매칭 마감 시각으로 옮기기', explain: '자동 매칭이 다시 돌아요. "피하고 싶음"은 다른 테이블로, "다시 만나고 싶음"은 같은 테이블로 모이도록 가점을 받고, 이전 동석자는 가능한 한 분산돼요.', done: reached('J6'), run: () => jump('J6') },
      ],
      result: reached('J6')
        ? ['2회차 매칭이 자동으로 끝났어요. 피하고 싶다고 한 쌍은 같은 테이블에 앉지 않고, 다시 만나고 싶다고 한 쌍은 같은 테이블이 되도록 우선해요.', '참가 이력과 다음 일정 추천도 자동으로 만들어졌어요.']
        : [],
      views: [
        { label: '2회차 테이블 보기 (운영자)', as: 'admin', to: '/admin/matching' },
        { label: '참가 이력·다음 식탁 추천 (참가자)', as: 'demo01', to: '/app/history' },
      ],
    },
    {
      n: 7,
      title: '정상 흐름은 운영자 개입 없이 끝나고, 해결되지 않은 예외만 운영자에게 남는다',
      prd: 'PRD 12 · MAT-13',
      intro: '지금까지 모든 단계를 시스템이 자동으로 처리했어요. 운영자 화면에는 사람이 봐야 하는 예외만 남아요.',
      subs: [],
      result: reached('J6')
        ? [
            `시스템이 자동으로 처리한 상태 변경 ${s.autoActions}건 · 운영자가 직접 처리한 것 ${s.operatorActions}건`,
            `처리할 예외 ${s.openExceptions}건: 식당 부족 ${s.venueExceptions}건(일부러 만든 예외), 체크인하지 않은 노쇼 후보 ${s.noShowCandidates}건`,
          ]
        : [],
      views: [
        { label: '운영 대시보드 보기', as: 'admin', to: '/admin' },
        { label: '예외 처리함 보기', as: 'admin', to: '/admin/exceptions' },
      ],
    },
  ]
}

const EXTRAS: { title: string; body: string; view: View }[] = [
  {
    title: '신청·참석 취소와 환불',
    body: '확정 전 취소는 자동 환불, 보관 이용권은 복원돼요. 행사 2일 전 이후의 취소는 환불이 없고 이용 제한 검토 대상이 돼요.',
    view: { label: '내 신청에서 취소 안내 보기', as: 'demo01', to: '/app' },
  },
  {
    title: '취소 시 대기자 자동 충원·재배치',
    body: '확정 테이블에서 1명이 취소하면 조건에 맞는 대기자가 자동으로 들어오고, 인원이 4명 미만이 되면 다른 테이블로 옮기거나 대체 일정을 안내해요.',
    view: { label: '운영자: 매칭 모니터에서 취소 실행 기록 보기', as: 'admin', to: '/admin/matching' },
  },
  {
    title: '운영자 긴급 수정',
    body: '확정 후 안전 문제 같은 예외에서만, 사유를 적고 참가자를 다른 테이블로 옮기거나 테이블을 해제할 수 있어요. 필수 조건은 다시 검증하고, 변경은 예외 처리함에 남아요.',
    view: { label: '운영자: 테이블 상세에서 보기', as: 'admin', to: '/admin/matching' },
  },
  {
    title: '멤버십·보관 이용권',
    body: '1·3·6개월 멤버십을 모의 결제로 구매해 결제 없이 신청하고, 매칭에 실패해 보관한 이용권도 다음 신청에 쓸 수 있어요.',
    view: { label: '일정에서 이용권 보기', as: 'demo01', to: '/app/slots' },
  },
  {
    title: '앱 푸시·자동 메시지 관리',
    body: '알림은 앱 안에 쌓이고 푸시도 함께 발송돼요. 운영자는 템플릿을 고치거나 끄고, 발송 성공·실패·재시도 이력을 볼 수 있어요. 피드백 미응답 리마인드는 행사 하루 뒤 한 번만 나가요.',
    view: { label: '운영자: 자동 메시지 보기', as: 'admin', to: '/admin/messages' },
  },
  {
    title: '개인정보 삭제·탈퇴',
    body: '로그인 화면의 "새 참가자로 시작하기"로 만든 계정은 프로필 화면에서 실제로 탈퇴(개인정보 삭제)해 볼 수 있어요. 보관·삭제 정책도 같은 화면에 있어요.',
    view: { label: '내 프로필에서 정책 보기', as: 'demo01', to: '/app/profile' },
  },
]

const stepDone = (step: Step) => step.subs.length > 0 && step.subs.every((s) => s.done)

export default function GuidePage() {
  const queryClient = useQueryClient()
  const progress = useQuery({ queryKey: ['guide-progress'], queryFn: fetchProgress, refetchInterval: 8_000, retry: 1 })
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<number | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['guide-progress'] })
  const act = async (key: string, fn: () => Promise<unknown>) => {
    setBusy(key)
    setError(null)
    try {
      await fn()
      await refresh()
    } catch (e) {
      setError(errorMessage(e))
      await refresh()
    } finally {
      setBusy(null)
    }
  }
  const reset = () =>
    act('reset', async () => {
      if (!window.confirm('처음부터 다시 시작할까요?\n시간이 실제 현재 시각으로 돌아가고, 모든 데이터가 처음 시드 상태로 복원돼요.')) return
      await call('demo01', 'post', '/api/v1/demo/clock/reset')
      Object.keys(tokens).forEach((k) => delete tokens[k])
      setOpen(null)
    })
  // 해당 계정으로 이 브라우저에 로그인한 뒤 화면을 연다. 돌아올 때는 화면 아래 "시연 가이드" 알약을 누른다.
  const view = (v: View) =>
    act('view', async () => {
      const s = await login(v.as)
      setSession(s)
      try {
        localStorage.setItem('siktak.guide.active', '1')
      } catch {
        // 저장소를 못 써도 화면 이동은 된다
      }
      window.location.assign(v.to)
    })

  const p = progress.data
  const steps = p ? buildSteps(p) : []
  const currentIdx = steps.findIndex((st) => !stepDone(st) && st.subs.length > 0)
  const doneCount = steps.filter(stepDone).length
  const allDone = p != null && currentIdx === -1

  return (
    <main className="mx-auto min-h-screen max-w-2xl bg-paper px-4 pb-24 text-ink">
      <header className="flex items-center justify-between pt-4 pb-2">
        <Link to="/app" className="font-serif text-xl font-black tracking-[-0.5px]">
          우연한 식탁
        </Link>
        <button type="button" onClick={reset} disabled={busy != null} className="text-xs font-bold text-muted underline disabled:opacity-40">
          ↺ 처음부터 다시 시작
        </button>
      </header>
      <div className="mt-1 flex flex-wrap gap-2">
        <button
          type="button"
          disabled={busy != null}
          onClick={() => view({ label: '참가자 홈', as: 'demo01', to: '/app' })}
          className="min-h-[38px] rounded-full border border-line bg-card px-3.5 text-xs font-bold disabled:opacity-50"
        >
          🏠 참가자 홈으로
        </button>
        <button
          type="button"
          disabled={busy != null}
          onClick={() => view({ label: '운영자 홈', as: 'admin', to: '/admin' })}
          className="min-h-[38px] rounded-full border border-line bg-card px-3.5 text-xs font-bold disabled:opacity-50"
        >
          🛠 운영자 대시보드로
        </button>
        <Link to="/login" className="inline-flex min-h-[38px] items-center rounded-full border border-line bg-card px-3.5 text-xs font-bold">
          로그인 화면
        </Link>
      </div>
      <h1 className="mt-2 font-serif text-[26px] leading-tight font-black tracking-[-1px]">시연 가이드</h1>
      <p className="mt-1 text-sm text-body">
        <b>위에서부터 한 단계씩, 노란 버튼만 누르면</b> 됩니다. (직접 해보고 싶다면 로그인 화면의 <b>"새 참가자로 시작하기"</b>로 나만의 계정을 만들 수 있어요.) 사람이 하는 일은 거의 없고, 시스템이 스스로 돌아가는 모습을 단계마다 숫자로 보여 드려요.
      </p>

      {p && (
        <section className="mt-4 rounded-2xl border border-line bg-card p-4">
          <div className="flex items-center justify-between text-sm">
            <b>{allDone ? '모든 단계 완료 🎉' : `진행 ${doneCount} / ${steps.length - 1}`}</b>
            <span className="text-xs text-muted">서버 시각 {formatDateTime(p.now)}</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-paper">
            <div className="h-full bg-accent transition-all" style={{ width: `${(doneCount / (steps.length - 1)) * 100}%` }} />
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2 text-center">
            <div className="rounded-xl bg-sage px-2 py-2">
              <div className="font-serif text-2xl font-black">{p.stats.autoActions}</div>
              <div className="text-[11px] font-bold">시스템이 자동 처리한 상태 변경</div>
            </div>
            <div className="rounded-xl bg-gold-soft px-2 py-2">
              <div className="font-serif text-2xl font-black">{p.stats.operatorActions}</div>
              <div className="text-[11px] font-bold">운영자가 직접 처리한 것</div>
            </div>
          </div>
        </section>
      )}

      {progress.isPending && <p className="mt-4 text-sm text-muted">서버를 깨우는 중이에요… 최대 1분쯤 걸릴 수 있어요.</p>}
      {progress.isError && (
        <p className="mt-4 rounded-xl bg-gold-soft px-3 py-2 text-sm font-bold">
          서버에 연결하지 못했어요. 잠시 뒤 다시 열어 주세요. ({errorMessage(progress.error)})
        </p>
      )}
      {error && <p className="mt-3 rounded-xl bg-gold-soft px-3 py-2 text-sm font-bold text-accent">⚠ {error}</p>}

      <ol className="mt-4 flex flex-col gap-3">
        {steps.map((st, i) => {
          const done = stepDone(st)
          const isCurrent = i === currentIdx
          const locked = currentIdx !== -1 && i > currentIdx
          const final = st.subs.length === 0
          const expanded = isCurrent || open === st.n || (final && allDone)
          const nextSub = st.subs.findIndex((x) => !x.done)
          return (
            <li
              key={st.n}
              className={`rounded-2xl border p-4 ${isCurrent ? 'border-accent bg-card shadow-print' : 'border-line bg-card'} ${locked ? 'opacity-50' : ''}`}
            >
              <button
                type="button"
                disabled={locked || isCurrent}
                onClick={() => setOpen(open === st.n ? null : st.n)}
                className="flex w-full items-start gap-3 text-left"
              >
                <span
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full font-serif text-base font-black ${
                    done ? 'bg-sage text-ink' : isCurrent ? 'bg-accent text-cream' : 'bg-paper text-muted'
                  }`}
                >
                  {done ? '✓' : locked ? '🔒' : st.n}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-serif text-base leading-snug font-black">{st.title}</span>
                  <span className="mt-0.5 block text-[11px] text-muted">{st.prd}</span>
                </span>
              </button>

              {expanded && (
                <div className="mt-3 pl-11">
                  <p className="text-sm text-body">{st.intro}</p>

                  {st.blocked ? (
                    <div className="mt-3 rounded-xl bg-gold-soft p-3 text-sm">
                      <b>순서가 맞지 않아요.</b> {st.blocked}
                      <button type="button" onClick={reset} className="mt-2 block rounded-xl border border-accent px-3 py-2 font-bold text-accent">
                        ↺ 처음부터 다시 시작
                      </button>
                    </div>
                  ) : (
                    <ul className="mt-3 flex flex-col gap-2.5">
                      {st.subs.map((sub, k) => {
                        const key = `${st.n}-${k}`
                        const isNext = isCurrent && k === nextSub
                        return (
                          <li key={key} className="flex items-start gap-2">
                            <span className={`mt-0.5 text-sm ${sub.done ? 'text-accent' : 'text-muted'}`}>{sub.done ? '✓' : '•'}</span>
                            <div className="min-w-0 flex-1">
                              {isNext ? (
                                <button
                                  type="button"
                                  disabled={busy != null}
                                  onClick={() => act(key, sub.run)}
                                  className="min-h-[46px] w-full rounded-xl border border-accent bg-gold-soft px-3 text-left text-sm font-black text-accent disabled:opacity-60"
                                >
                                  {busy === key ? '진행 중… 잠시만요' : `▶ ${sub.label}`}
                                </button>
                              ) : (
                                <p className={`text-sm ${sub.done ? 'text-muted line-through' : 'text-muted'}`}>{sub.label}</p>
                              )}
                              {(isNext || !sub.done) && <p className="mt-1 text-xs text-muted">{sub.explain}</p>}
                            </div>
                          </li>
                        )
                      })}
                    </ul>
                  )}

                  {st.result.length > 0 && (
                    <div className="mt-3 rounded-xl bg-sage/60 p-3 text-sm">
                      <b>시스템이 자동으로 한 일</b>
                      <ul className="mt-1 list-disc space-y-1 pl-5">
                        {st.result.map((r) => (
                          <li key={r}>{r}</li>
                        ))}
                      </ul>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {st.views.map((v) => (
                          <button
                            key={v.label}
                            type="button"
                            disabled={busy != null}
                            onClick={() => view(v)}
                            className="min-h-[36px] rounded-lg border border-line bg-card px-2.5 text-xs font-bold disabled:opacity-50"
                          >
                            {v.label} →
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ol>

      {allDone && (
        <section className="mt-5 rounded-2xl border border-line bg-card p-4">
          <h2 className="font-serif text-base font-black">더 둘러보기 — PRD가 요구한 나머지 자동화</h2>
          <p className="mt-1 text-xs text-muted">12단계 시나리오 밖의 기능이에요. 시간 칩의 "+1일"을 쓰면 하루 뒤에 일어나는 일도 볼 수 있어요.</p>
          <ul className="mt-3 flex flex-col gap-3 text-sm">
            {EXTRAS.map((x) => (
              <li key={x.title}>
                <b>{x.title}</b>
                <p className="text-xs text-body">{x.body}</p>
                <button
                  type="button"
                  disabled={busy != null}
                  onClick={() => view(x.view)}
                  className="mt-1 min-h-[34px] rounded-lg border border-line bg-paper px-2.5 text-xs font-bold disabled:opacity-50"
                >
                  {x.view.label} →
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="mt-6 text-xs leading-relaxed text-muted">
        참가자·운영자 화면 버튼을 누르면 해당 계정으로 로그인한 화면이 열려요. 돌아올 때는 화면 아래의 <b>"시연 가이드"</b> 알약을 누르세요.
        시간은 앞으로만 가고, 모든 사용자가 같은 시간을 써요.
      </p>
    </main>
  )
}
