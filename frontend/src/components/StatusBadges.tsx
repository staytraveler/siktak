import { CircleCheck, Clock, CreditCard } from 'lucide-react'
import type { ApplicationItem } from '../api/types'
import { APPLICATION_STATUS, label, MATCH_STATUS, PAYMENT_STATUS } from '../lib/format'
import { Pill } from './ui'

// 신청·결제·매칭 상태를 각각 표시한다 (PRD 12장: 하나로 합치지 않는다)
export default function StatusBadges({ application }: { application: ApplicationItem }) {
  const paid = application.paymentStatus === 'PAID'
  const confirmed = application.matchStatus === 'CONFIRMED'
  return (
    <div className="flex flex-wrap gap-1.5">
      <Pill icon={CircleCheck}>{label(APPLICATION_STATUS, application.applicationStatus)}</Pill>
      <Pill icon={CreditCard} tone={paid ? 'wine' : 'gold'}>
        {label(PAYMENT_STATUS, application.paymentStatus)}
      </Pill>
      <Pill icon={Clock} tone={confirmed ? 'sage' : 'gold'}>
        {label(MATCH_STATUS, application.matchStatus)}
      </Pill>
    </div>
  )
}
