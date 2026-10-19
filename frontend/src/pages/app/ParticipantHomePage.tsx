import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { ApplicationItem } from '../../api/types'
import ApplicationHero from '../../components/ApplicationHero'
import SessionBar from '../../components/SessionBar'
import StatusBadges from '../../components/StatusBadges'
import { AppShell, Chip, EmptyState, HeroBlock, LinkButton, SectionHeader, SlotTicket, TicketCard } from '../../components/ui'
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
  const nowMs = useServerNow()
  const main = applications.data ? pickMain(applications.data) : undefined
  const confirmed = main?.matchStatus === 'CONFIRMED'
  const { table, messages, venue } = useTableInfo(main?.id, confirmed)
  const slot = main ? mainSlot(main) : null

  return (
    <AppShell>
      {applications.isPending && (
        <HeroBlock kicker="Just a moment." height={230} title={<>식탁을 불러오고<br />있어요</>} />
      )}
      {applications.isError && <p className="px-[22px] pt-6 text-sm font-medium text-accent">{errorMessage(applications.error)}</p>}

      {applications.isSuccess && !main && (
        <HeroBlock
          kicker="Welcome."
          height={370}
          illustration="homeTable"
          illustrationHeight={230}
          illustrationOffset={-20}
          title={<>첫 식탁을<br />차려볼까요</>}
        />
      )}

      {main && (
        <>
          <ApplicationHero application={main} nowMs={nowMs} memberCount={table.data?.memberCount} />

          {confirmed && slot && (
            <div className="relative z-10 mx-[18px] -mt-11">
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
            <section className="flex flex-col gap-2.5 px-[22px] pt-[22px]">
              <SectionHeader title="At the table" label={`${table.data.memberCount}명`} />
              {table.data.reasons.length > 0 && (
                <p className="font-serif text-[15px] leading-relaxed font-bold">{table.data.reasons.join(' · ')}</p>
              )}
              <div className="flex flex-wrap gap-1.5">
                <Chip>대화 주제는 단체방에서 공개</Chip>
              </div>
            </section>
          )}

          <div className="px-[18px] pt-5">
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

      <section className="flex flex-col gap-2.5 px-[22px] pt-7">
        <SectionHeader title="My tables" label="내 신청" />
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
          return (
            <div key={a.id} className="flex flex-col gap-1">
              {s ? (
                <SlotTicket
                  to={`/app/applications/${a.id}`}
                  startsAt={s.startsAt}
                  title={s.regionName}
                  sub={a.slotChoices.length > 1 ? `후보 ${a.slotChoices.length}개 일정` : undefined}
                  stampTone={a.id === main?.id ? 'accent' : 'ink'}
                />
              ) : (
                <Link to={`/app/applications/${a.id}`} className="border-[1.5px] border-ink bg-card p-3 text-sm">
                  신청 #{a.id}
                </Link>
              )}
              <StatusBadges application={a} />
            </div>
          )
        })}
        {!!applications.data?.length && (
          <Link to="/app/slots" className="mt-1 flex min-h-[44px] items-center justify-end text-sm font-bold text-accent">
            일정 보고 신청하기 →
          </Link>
        )}
      </section>

      <footer className="mt-4 flex flex-col gap-1 border-t-[1.5px] border-ink px-[22px] pt-3">
        <Link to="/app/profile" className="flex min-h-[44px] items-center text-sm font-bold">
          내 프로필 {profile.data && !profile.data.complete ? '(미완성 · 신청 전에 입력해주세요)' : '보기·수정'} →
        </Link>
        <SessionBar />
      </footer>
    </AppShell>
  )
}
