import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AxiosError } from 'axios'
import { errorMessage, type ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import { AppShell, EmptyState, ErrorText, PageTitle, PrimaryButton, SlotTicket } from '../../components/ui'
import { dateParts, daysUntil } from '../../lib/design'
import { formatDateTime } from '../../lib/format'
import { useServerNow } from '../../lib/useTableInfo'

// 신청 마감까지 "D-3 마감" / "오늘 마감" 같은 짧은 표기
function deadlineLabel(deadlineIso: string, nowMs: number): string {
  if (Date.parse(deadlineIso) <= nowMs) return '마감'
  const d = daysUntil(deadlineIso, nowMs)
  return d <= 0 ? '오늘 마감' : `D-${d} 마감`
}

const MAX_PICKS = 5 // APP-02: 서버 상한과 같다

// 일정 탭 (/app/slots): 모집 중 회차를 고르고 신청한다. 기존 홈의 신청 기능을 그대로 옮겼다.
export default function SlotsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const slots = useQuery({ queryKey: ['open-slots'], queryFn: api.openSlots })
  const nowMs = useServerNow()
  // 체크한 순서 = 우선순위 (matching.md MD7)
  const [picked, setPicked] = useState<number[]>([])

  const submit = useMutation({
    mutationFn: () => api.submitApplication(picked),
    onError: (error) => {
      // 프로필 미완성이면 프로필 화면으로 안내 (APP-02)
      if (error instanceof AxiosError && (error.response?.data as ApiError | undefined)?.code === 'PROFILE_INCOMPLETE') {
        navigate('/app/profile?next=' + encodeURIComponent('/app/slots'))
      }
    },
    onSuccess: (created) => {
      setPicked([])
      queryClient.invalidateQueries({ queryKey: ['my-applications'] })
      navigate(`/app/applications/${created.id}`)
    },
  })

  const toggle = (id: number) =>
    setPicked((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : prev.length >= MAX_PICKS ? prev : [...prev, id],
    )

  return (
    <AppShell>
      <PageTitle kicker="Pick your evenings." title="모집 중인 식탁" />
      <section className="flex flex-col gap-2.5 px-[18px]">
        <p className="px-1 text-xs leading-relaxed text-body">
          참가 가능한 일정을 모두 고르세요. 먼저 고른 순서대로 우선 매칭합니다. (최대 {MAX_PICKS}개)
        </p>
        {slots.isPending && <p className="text-sm text-muted">불러오는 중…</p>}
        {slots.isError && <ErrorText>{errorMessage(slots.error)}</ErrorText>}
        {slots.data?.length === 0 && <EmptyState>모집 중인 회차가 없어요.</EmptyState>}
        {slots.data?.map((s) => {
          const order = picked.indexOf(s.id)
          return (
            <SlotTicket
              key={s.id}
              startsAt={s.startsAt}
              time={dateParts(s.startsAt).hm}
              title={s.regionName}
              sub={
                <>
                  <b className="text-ink">{deadlineLabel(s.applyDeadlineAt, nowMs)}</b>
                  {s.remainingSeats != null && ` · 남은 자리 ${s.remainingSeats}`}
                  <br />
                  <span className="text-[10px]">매칭 {formatDateTime(s.matchRunAt)}</span>
                </>
              }
              selected={order >= 0}
              order={order}
              onClick={() => toggle(s.id)}
            />
          )
        })}

        <div className="mt-2 flex flex-wrap gap-2 text-sm">
          <span className="rounded-xl border border-line bg-mustard px-3 py-2 font-bold">1회권 9,900원</span>
        </div>
        <p className="text-[11px] leading-relaxed text-muted">
          진행 2일 전부터 환불 불가, 당일 취소 및 단체방 퇴장 불가. 노쇼 시 이용이 제한될 수 있어요.
        </p>
        {submit.isError && <ErrorText>{errorMessage(submit.error)}</ErrorText>}
      </section>

      {/* 하단 고정 선택 바: 메뉴 바로 위. 결제 수단은 신청 직후 상세 화면에서 고른다 */}
      <div className="sticky bottom-[58px] z-20 mt-4 flex items-center justify-between gap-3 border-t border-line bg-card px-[18px] py-2.5">
        <span className="text-sm font-bold">{picked.length}개 선택</span>
        <PrimaryButton className="!w-auto min-w-[120px]" disabled={picked.length === 0 || submit.isPending} onClick={() => submit.mutate()}>
          {submit.isPending ? '신청 중…' : '다음 →'}
        </PrimaryButton>
      </div>
    </AppShell>
  )
}
