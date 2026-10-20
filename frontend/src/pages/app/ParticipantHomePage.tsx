import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import { formatDateTime } from '../../lib/format'
import type { ApplicationItem } from '../../api/types'
import ApplicationHero from '../../components/ApplicationHero'
import SessionBar from '../../components/SessionBar'
import StatusBadges from '../../components/StatusBadges'
import { AppHeader, AppShell, Chip, EmptyState, HeroBlock, HeroDesc, LinkButton, SectionHeader, SlotTicket, TicketCard } from '../../components/ui'
import { durationLabel, mainSlot, RESOLUTION_STATUSES } from '../../lib/design'
import { useServerNow, useTableInfo } from '../../lib/useTableInfo'

// 홈에서 가장 먼저 보여줄 신청: 확정 → 매칭 실패 선택 대기 → 결제 대기 → 매칭 대기 → 나머지
function pickMain(list: ApplicationItem[]): ApplicationItem | undefined {
  const alive = list.filter((a) => a.applicationStatus !== 'CANCELED')
  const rank = (a: ApplicationItem) =>
    a.matchStatus === 'CONFIRMED' ? 0 : RESOLUTION_STATUSES.includes(a.matchStatus ?? '') ? 1 : a.paymentStatus === 'PENDING' ? 2 : 3
  return [...alive].sort((x, y) => rank(x) - rank(y))[0]
}

// 참가자 앱 홈 (/app). 모바일 우선. 가장 중요한 신청 하나를 히어로로 보여주고, 신청 내역은 아래에 둔다.
export default function ParticipantHomePage() {
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: api.myApplications })
  const profile = useQuery({ queryKey: ['my-profile'], queryFn: api.myProfile })
  const passes = useQuery({ queryKey: ['my-passes'], queryFn: api.myPasses })
  const nowMs = useServerNow()
  const main = applications.data ? pickMain(applications.data) : undefined
  const confirmed = main?.matchStatus === 'CONFIRMED'
  const { table, messages, venue } = useTableInfo(main?.id, confirmed)
  const slot = main ? mainSlot(main) : null

  return (
    <AppShell>
      {applications.isPending && (
        <>
          <AppHeader />
          <HeroBlock kicker="Just a moment" title={<>식탁을 불러오고<br />있어요</>} />
        </>
      )}
      {applications.isError && <p className="px-[22px] pt-6 text-sm font-medium text-accent">{errorMessage(applications.error)}</p>}

      {applications.isSuccess && !main && (
        <>
          <AppHeader />
          <HeroBlock kicker="Welcome" title={<>첫 식탁을<br />차려볼까요</>}>
            <HeroDesc>이번 주 저녁, 새로운 사람들과</HeroDesc>
          </HeroBlock>
          <div className="flex flex-col gap-3 px-4 pt-4">
            {profile.data && !profile.data.complete && (
              <Link to="/app/profile?next=/app/slots" className="flex min-h-[44px] items-center justify-between rounded-2xl bg-gold-soft px-4 py-3 text-sm">
                <span>
                  <b className="font-serif">프로필 먼저 채우기</b>
                  <span className="block text-xs">신청 전에 프로필이 필요해요</span>
                </span>
                <span className="font-bold">→</span>
              </Link>
            )}
            <LinkButton to="/app/slots">
              <span>이번 주 식탁 고르기</span>
              <span>→</span>
            </LinkButton>
          </div>
        </>
      )}

      {main && (
        <>
          <ApplicationHero application={main} nowMs={nowMs} memberCount={table.data?.memberCount} />

          {confirmed && slot && (
            <div className="px-4 pt-4">
              <TicketCard
                raised
                startsAt={slot.startsAt}
                duration={durationLabel(slot.startsAt, slot.endsAt)}
                place={slot.regionName}
                placeSub={venue.venueName ?? '식당 안내 준비 중'}
                footer={
                  <>
                    <span>
                      예약명 <b>{venue.reservation ?? '확정 후 안내'}</b>
                    </span>
                    {venue.mapUrl && (
                      <a href={venue.mapUrl} target="_blank" rel="noreferrer" className="font-bold text-accent">
                        지도 →
                      </a>
                    )}
                  </>
                }
              />
            </div>
          )}

          {confirmed && table.data && (
            <section className="flex flex-col gap-2.5 px-5 pt-6">
              <SectionHeader title="At the table" label={`${table.data.memberCount}명`} />
              {table.data.reasons.length > 0 && (
                <p className="font-serif text-[15px] leading-relaxed font-bold">{table.data.reasons.join(' · ')}</p>
              )}
              <div className="flex flex-wrap gap-1.5">
                <Chip>대화 주제는 단체방에서 공개</Chip>
              </div>
            </section>
          )}

          <div className="px-4 pt-4">
            {confirmed ? (
              <LinkButton to={`/app/applications/${main.id}/table`}>
                <span>단체방 입장하기</span>
                <span className="flex items-center gap-1.5 text-xs font-medium">
                  {messages.data && messages.data.length > 0 && (
                    <span className="rounded-full bg-coral px-2 py-0.5 font-bold text-ink">메시지 {messages.data.length}</span>
                  )}
                  →
                </span>
              </LinkButton>
            ) : (
              <LinkButton to={`/app/applications/${main.id}`}>
                <span>{main.paymentStatus === 'PENDING' ? '결제하러 가기' : '신청 상세 보기'}</span>
                <span>→</span>
              </LinkButton>
            )}
          </div>
        </>
      )}

      <section className="flex flex-col gap-3 px-5 pt-7">
        <SectionHeader title="My tables" to="/app/history" />
        {applications.data?.length === 0 && (
          <EmptyState>
            아직 신청한 식사가 없어요.
            <Link to="/app/slots" className="min-h-[44px] content-center font-bold text-accent">
              일정 보러 가기 →
            </Link>
          </EmptyState>
        )}
        {applications.data?.map((a) => {
          const s = mainSlot(a)
          return s ? (
            <SlotTicket
              key={a.id}
              to={`/app/applications/${a.id}`}
              startsAt={s.startsAt}
              title={s.regionName}
              sub={a.slotChoices.length > 1 ? `후보 ${a.slotChoices.length}개 일정` : '함께할 사람을 기다리고 있어요'}
              pills={<StatusBadges application={a} />}
              stampTone="accent"
            />
          ) : (
            <Link key={a.id} to={`/app/applications/${a.id}`} className="rounded-2xl border border-line bg-card p-3 text-sm">
              신청 #{a.id}
            </Link>
          )
        })}
        {!!applications.data?.length && (
          <Link to="/app/slots" className="mt-1 flex min-h-[44px] items-center justify-end text-sm font-bold text-accent">
            일정 보고 신청하기 →
          </Link>
        )}
      </section>

      <footer className="mx-5 mt-4 flex flex-col gap-2 border-t border-line pt-3">
        <Link to="/app/profile" className="flex min-h-[44px] items-center text-[13px] font-bold">
          내 프로필 {profile.data && !profile.data.complete ? '(미완성 · 신청 전에 입력해주세요)' : '보기 · 수정'} →
        </Link>
        {passes.data?.membership && (
          <p className="text-xs text-body">
            멤버십 이용 중 · <b className="text-accent">{formatDateTime(passes.data.membership.expiresAt)}</b>까지
          </p>
        )}
        {(passes.data?.creditCount ?? 0) > 0 && (
          <p className="text-xs text-body">
            보관 이용권 <b className="text-accent">{passes.data?.creditCount}장</b> · 다음 신청에 추가 결제 없이 쓸 수 있어요
          </p>
        )}
        <Link to="/guide" className="flex min-h-[44px] items-center text-[13px] font-bold text-accent">
          📋 시연 가이드 보기 →
        </Link>
        <SessionBar />
      </footer>
    </AppShell>
  )
}
