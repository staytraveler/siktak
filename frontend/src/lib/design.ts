import type { ApplicationItem, Slot } from '../api/types'
// 디자인용 날짜 조각 (티켓 카드 DATE/TIME/PLACE)와 확정 안내 파싱.
const KST = 'Asia/Seoul'
const part = (iso: string, opts: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('en-US', { timeZone: KST, hourCycle: 'h23', ...opts }).format(new Date(iso))

export function dateParts(iso: string) {
  const day = Number(part(iso, { day: 'numeric' }))
  return {
    md: `${Number(part(iso, { month: 'numeric' }))}.${String(day).padStart(2, '0')}`,
    day,
    month: part(iso, { month: 'short' }).toUpperCase(),
    weekday: part(iso, { weekday: 'short' }).toUpperCase(),
    hm: `${part(iso, { hour: '2-digit' }).padStart(2, '0')}:${part(iso, { minute: '2-digit' }).padStart(2, '0')}`,
    hour: Number(part(iso, { hour: 'numeric' })),
  }
}

export function durationLabel(startIso: string, endIso: string): string {
  const h = Math.round(((new Date(endIso).getTime() - new Date(startIso).getTime()) / 3600_000) * 2) / 2
  return `${h}시간`
}

// 달력 날짜(서울) 기준 남은 일수. 0이면 오늘.
export function daysUntil(startIso: string, nowMs: number): number {
  const ymd = (ms: number) => new Intl.DateTimeFormat('en-CA', { timeZone: KST }).format(new Date(ms))
  return Math.round((Date.parse(ymd(new Date(startIso).getTime())) - Date.parse(ymd(nowMs))) / 86400_000)
}

export function dayPart(hour: number): string {
  return hour >= 17 ? '저녁' : hour >= 11 ? '점심' : '아침'
}

const WEEKDAY_KO: Record<string, string> = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' }
export const weekdayKo = (w: string) => WEEKDAY_KO[w] ?? w

// 확정 안내(NOTICE) 메시지에서 식당·예약명·지도 링크를 꺼낸다.
// [해석] 테이블 API에 식당 정보가 없어 TableGuidanceService.venueNotice 문구("장소:/지도:/예약명:")를 파싱한다.
export function parseVenueNotice(bodies: string[]): {
  venueName?: string
  reservation?: string
  mapUrl?: string
} {
  const find = (key: string) => {
    for (const body of bodies) {
      const line = body.split('\n').find((l) => l.startsWith(`${key}: `))
      if (line) return line.slice(key.length + 2).trim()
    }
    return undefined
  }
  const place = find('장소')
  const reservation = find('예약명')
  return {
    venueName: place?.replace(/\s*\(.*\)\s*$/, ''),
    reservation: reservation && reservation !== '확정 후 안내' ? reservation : undefined,
    mapUrl: find('지도'),
  }
}

export const RESOLUTION_STATUSES = ['ALTERNATIVE_OFFERED', 'NO_MATCH', 'TRANSFERRED']

// 신청의 대표 회차: 확정된 회차 → 없으면 1순위
export function mainSlot(a: ApplicationItem): Slot | null {
  const assigned = a.slotChoices.find((c) => c.slot?.id === a.assignedSlotId)?.slot
  return assigned ?? a.slotChoices[0]?.slot ?? null
}
