export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 상태 라벨 (PRD 12장). 신청·결제·매칭을 따로 보여준다.
export const APPLICATION_STATUS: Record<string, string> = {
  DRAFT: '작성 중',
  SUBMITTED: '신청 완료',
  CANCELED: '신청 취소',
}

export const PAYMENT_STATUS: Record<string, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  FAILED: '결제 실패',
  REFUND_AVAILABLE: '환불 선택 가능',
  REFUND_REQUESTED: '환불 요청',
  REFUND_PROCESSING: '환불 처리 중',
  REFUNDED: '환불 완료',
  REFUND_FAILED: '환불 실패',
}

export const MATCH_STATUS: Record<string, string> = {
  WAITING: '매칭 대기',
  MATCHING: '매칭 중',
  REALLOCATING: '재배치 중',
  CONFIRMED: '매칭 확정',
  ALTERNATIVE_OFFERED: '대체 일정 선택 대기',
  TRANSFERRED: '일정 이동 완료',
  NO_MATCH: '매칭 종료',
  EXCEPTION: '확인 중',
}

// 운영자 어드민 (8.1)
export const CASE_TYPE: Record<string, string> = {
  PAYMENT_ERROR: '결제 오류',
  REFUND_FAILED: '환불 실패',
  DATA_MISSING: '데이터 누락',
  MATCH_CONFLICT: '조건 충돌',
  VENUE_UNAVAILABLE: '식당 없음',
  NOTIFICATION_FAILED: '발송 실패',
  SAFETY_REPORT: '미처리 신고',
  NO_SHOW_CANDIDATE: '노쇼 후보',
}

export const CASE_STATUS: Record<string, string> = {
  OPEN: '처리 대기',
  IN_PROGRESS: '처리 중',
  RESOLVED: '해결됨',
}

export const DECISION: Record<string, string> = {
  PLACED: '배치',
  REALLOCATED: '재배치',
  CONFIRMED: '확정',
  FAILED: '실패',
  DEFERRED: '보류(다음 일정)',
  EXCLUDED: '제외',
}

export const TABLE_STATUS: Record<string, string> = {
  CONFIRMED: '확정',
  DISSOLVED: '해산',
  COMPLETED: '종료',
}

export const SEND_STATUS: Record<string, string> = {
  PENDING: '발송 대기',
  SENT: '발송됨',
  FAILED: '발송 실패',
  CANCELED: '취소됨',
}

export function percent(rate: number | null | undefined): string {
  return rate == null ? '-' : `${Math.round(rate * 100)}%`
}

export function label(map: Record<string, string>, value: string | null | undefined): string {
  if (!value) return '-'
  return map[value] ?? value
}

// datetime-local 값(서울 시각) ↔ ISO. 서울은 +09:00 고정 (서머타임 없음).
export const toIso = (local: string) => (local ? `${local}:00+09:00` : '')
export const toLocalInput = (iso: string) => new Date(new Date(iso).getTime() + 9 * 3600_000).toISOString().slice(0, 16)
