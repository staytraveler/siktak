import type { ReactNode } from 'react'
import type { ApplicationItem } from '../api/types'
import { dateParts, dayPart, daysUntil, mainSlot, RESOLUTION_STATUSES, weekdayKo } from '../lib/design'
import { formatDateTime } from '../lib/format'
import { HeroBlock, Sticker } from './ui'


// 홈·신청 상세 맨 위 히어로. 상태마다 색과 문구가 바뀐다.
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
  const slot = mainSlot(application)
  const match = application.matchStatus

  if (match === 'CONFIRMED' && slot) {
    const d = dateParts(slot.startsAt)
    const n = daysUntil(slot.startsAt, nowMs)
    return (
      <HeroBlock
        kicker="Your table is ready."
        height={440}
        illustration="homeTable"
        illustrationHeight={300}
        illustrationOffset={-60}
        sticker={<Sticker top={n > 0 ? `D-${n}` : n === 0 ? 'D-DAY' : '다녀옴'} bottom="확정" />}
        title={
          <>
            {weekdayKo(d.weekday)}요일 {dayPart(d.hour)},<br />
            {memberCount ? `${numKo(memberCount)} 사람의 식탁이` : '식탁이'}
            <br />
            차려졌어요
          </>
        }
      >
        {children}
      </HeroBlock>
    )
  }

  if (match && RESOLUTION_STATUSES.includes(match)) {
    const moved = match === 'TRANSFERRED'
    return (
      <HeroBlock
        tone="mustard"
        kicker={moved ? 'Moved over.' : 'Saving your seat.'}
        height={330}
        illustration="resolveTable"
        illustrationHeight={170}
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
              자리를 찾지 못했어요
            </>
          )
        }
      >
        {children}
      </HeroBlock>
    )
  }

  if (application.paymentStatus === 'PENDING') {
    return (
      <HeroBlock
        tone="mustard"
        kicker="One more step."
        height={250}
        title={
          <>
            결제만 하면
            <br />
            자리가 예약돼요
          </>
        }
      >
        <p className="mt-3 text-[13px] leading-relaxed">결제 기한 {formatDateTime(application.paymentDueAt)}</p>
        {children}
      </HeroBlock>
    )
  }

  if (match === 'WAITING' || match === 'MATCHING' || match === 'REALLOCATING') {
    return (
      <HeroBlock
        tone="mustard"
        kicker="Setting the table."
        height={250}
        title={
          <>
            테이블을
            <br />
            차리는 중이에요
          </>
        }
      >
        <p className="mt-3 text-[13px] leading-relaxed">
          {match === 'WAITING' && slot ? `매칭 발표 예정 ${formatDateTime(slot.matchRunAt)}` : '조건에 맞는 분들을 찾고 있어요.'}
        </p>
        {children}
      </HeroBlock>
    )
  }

  return (
    <HeroBlock
      tone="mustard"
      kicker="Hold on."
      height={230}
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
