import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/endpoints'
import { fetchHealth } from '../api/health'

// 확정된 테이블(식당·예약명·지도 포함) + 단체방 메시지. 단체방은 10초마다 새로고침한다.
export function useTableInfo(applicationId: number | undefined, enabled = true) {
  const on = enabled && applicationId != null
  const table = useQuery({
    queryKey: ['my-table', applicationId],
    queryFn: () => api.myTable(applicationId!),
    enabled: on,
  })
  const chatRoomId = table.data?.chatRoomId
  const messages = useQuery({
    queryKey: ['chat', chatRoomId],
    queryFn: () => api.chatMessages(chatRoomId!),
    enabled: on && chatRoomId != null,
    refetchInterval: 10_000,
  })
  const venue = {
    venueName: table.data?.venueName ?? undefined,
    reservation: table.data?.reservationName ?? undefined,
    mapUrl: table.data?.mapUrl ?? undefined,
  }
  return { table, messages, venue }
}

// 데모 시각 제어 때문에 D-n은 브라우저 시계가 아니라 서버 시각으로 센다.
export function useServerNow(): number {
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: 1 })
  const [fallback] = useState(() => Date.now())
  return health.data ? new Date(health.data.time).getTime() : fallback
}
