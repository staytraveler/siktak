import type { ReactNode } from 'react'
import type { ApplicationItem } from '../api/types'
import { dateParts, dayPart, daysUntil, mainSlot, RESOLUTION_STATUSES, weekdayKo } from '../lib/design'
import { formatDateTime } from '../lib/format'
import { AppHeader, HeroBlock, HeroDate, HeroDesc, Sticker } from './ui'

// 홈·신청 상세 맨 위 (헤더 + 히어로). 상태마다 라벨·제목·설명이 바뀐다.
export default function ApplicationHero({
  application,
  nowMs,
  memberCount,
  children,
}: {
  application: ApplicationItem
  nowMs: number
  memberCount?: number
  children?: ReactNode
}) {
  return (
    <>
      <AppHeader />
      <Hero application={application} nowMs={nowMs} memberCount={memberCount}>
        {children}
      </Hero>
    </>
  )
}

function Hero({ application, nowMs, memberCount, children }: { application: ApplicationItem; nowMs: number; memberCount?: number; children?: ReactNode }) {
  const slot = mainSlot(application)
  const match = application.matchStatus

  if (match === 'CONFIRMED' && slot) {
    const d = dateParts(slot.startsAt)
    const n = daysUntil(slot.startsAt, nowMs)
    return (
      <HeroBlock
        kicker="Your table is ready"
        badge={<Sticker top={n > 0 ? `D-${n}` : n === 0 ? 'D-DAY' : '다녀옴'} bottom="확정" />}
        title={
          <>
            {weekdayKo(d.weekday)}요일 {dayPart(d.hour)},<br />
            {memberCount ? `${numKo(memberCount)} 사람의 식탁이` : '식탁이'}
            <br />
            차려졌어요
          </>
        }
      >
        <HeroDate>{formatDateTime(slot.startsAt)}</HeroDate>
        {children}
      </HeroBlock>
    )
  }

  if (match && RESOLUTION_STATUSES.includes(match)) {
    const moved = match === 'TRANSFERRED'
    return (
      <HeroBlock
        kicker={moved ? 'Moved over' : 'Saving your seat'}
        title={
          moved ? (
            <>
              다른 일정으로
              <br />
              자리를 옮겼어요
            </>
          ) : (
            <>
              이번엔 꼭 맞는
              <br />
              자리가 없었어요
            </>
          )
        }
      >
        <HeroDesc>{moved ? '옮긴 일정에서 새 인연을 기다려요' : '다음 선택지를 골라주세요'}</HeroDesc>
        {children}
      </HeroBlock>
    )
  }

  if (application.paymentStatus === 'PENDING') {
    return (
      <HeroBlock
        kicker="One more step"
        title={
          <>
            결제만 하면
            <br />
            자리가 예약돼요
          </>
        }
      >
        <HeroDate>결제 기한 {formatDateTime(application.paymentDueAt)}</HeroDate>
        {children}
      </HeroBlock>
    )
  }

  if (match === 'WAITING' || match === 'MATCHING' || match === 'REALLOCATING') {
    return (
      <HeroBlock
        kicker="Setting the table"
        title={
          <>
            테이블을
            <br />
            차리는 중이에요
          </>
        }
      >
        <HeroDesc>새로운 인연을 기다리고 있어요</HeroDesc>
        {match === 'WAITING' && slot ? (
          <HeroDate>매칭 발표 {formatDateTime(slot.matchRunAt)}</HeroDate>
        ) : (
          <HeroDesc>조건에 맞는 분들을 찾고 있어요.</HeroDesc>
        )}
        {children}
      </HeroBlock>
    )
  }

  return (
    <HeroBlock
      kicker="Hold on"
      title={
        <>
          신청 내용을
          <br />
          확인하고 있어요
        </>
      }
    >
      {children}
    </HeroBlock>
  )
}

const NUM_KO = ['', '한', '두', '세', '네', '다섯', '여섯', '일곱', '여덟']
const numKo = (n: number) => (NUM_KO[n] ? `${NUM_KO[n]}` : `${n}`)
