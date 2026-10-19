import type { ApplicationItem } from '../api/types'
import { APPLICATION_STATUS, label, MATCH_STATUS, PAYMENT_STATUS } from '../lib/format'

// 신청·결제·매칭 상태를 각각 표시한다 (PRD 12장: 하나로 합치지 않는다)
export default function StatusBadges({ application }: { application: ApplicationItem }) {
  return (
    <div className="flex flex-wrap gap-1 text-[11px]">
      <Badge title="신청" value={label(APPLICATION_STATUS, application.applicationStatus)} />
      <Badge title="결제" value={label(PAYMENT_STATUS, application.paymentStatus)} />
      <Badge
        title="매칭"
        value={label(MATCH_STATUS, application.matchStatus)}
        highlight={application.matchStatus === 'CONFIRMED'}
      />
    </div>
  )
}

function Badge({ title, value, highlight }: { title: string; value: string; highlight?: boolean }) {
  return (
    <span className={`px-2 py-0.5 ${highlight ? 'bg-sage font-bold text-ink' : 'border border-ink/40 text-body'}`}>
      {title} · {value}
    </span>
  )
}
