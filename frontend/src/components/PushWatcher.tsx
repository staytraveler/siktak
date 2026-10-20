import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api } from '../api/endpoints'
import { getSession } from '../api/session'

const lastKey = (userId: number) => `siktak.push.lastId.${userId}` // 계정마다 따로 (계정 전환 시 섞이지 않게)

export const pushSupported = () => typeof window !== 'undefined' && 'Notification' in window

// 앱 푸시(INF-01·07)의 브라우저 쪽: 서버가 보낸 알림이 새로 생기면 시스템 알림으로 띄운다.
// 외부 푸시 서비스 없이 알림함을 15초마다 읽는 방식이라 앱이 열려 있을 때 동작한다. 권한이 허용된 경우에만.
export default function PushWatcher() {
  const session = getSession()
  const enabled = pushSupported() && Notification.permission === 'granted' && !!session
  const notifications = useQuery({
    queryKey: ['notifications'],
    queryFn: api.myNotifications,
    enabled,
    refetchInterval: 15_000,
  })

  useEffect(() => {
    if (!enabled || !notifications.data) return
    const maxId = notifications.data.reduce((m, n) => Math.max(m, n.id), 0)
    let last: number | null = null
    try {
      const raw = localStorage.getItem(lastKey(session!.user.id))
      last = raw == null ? null : Number(raw)
    } catch {
      // 저장소를 못 쓰면 이번 세션에서는 알리지 않는다
      return
    }
    if (last != null) {
      notifications.data
        .filter((n) => n.id > (last as number) && !n.readAt)
        .sort((a, b) => a.id - b.id)
        .forEach((n) => new Notification(n.title, { body: n.body, tag: `siktak-${n.id}` }))
    }
    try {
      localStorage.setItem(lastKey(session!.user.id), String(maxId))
    } catch {
      // 무시
    }
  }, [enabled, notifications.data, session])

  return null
}
