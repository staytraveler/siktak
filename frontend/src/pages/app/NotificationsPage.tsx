import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import { formatDateTime } from '../../lib/format'

// 참가자 알림함 (/app/notifications, INF-01). 눌러서 읽음 처리하고, 연결된 신청으로 이동한다.
export default function NotificationsPage() {
  const queryClient = useQueryClient()
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: api.myNotifications })
  const read = useMutation({
    mutationFn: (id: number) => api.readNotification(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })

  return (
    <main className="mx-auto min-h-screen max-w-md bg-white p-4">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-bold">알림</h1>
        <Link to="/app" className="text-sm text-gray-500">
          홈
        </Link>
      </header>
      {notifications.isPending && <p className="mt-4 text-sm text-gray-500">불러오는 중…</p>}
      {notifications.isError && <p className="mt-4 text-sm text-red-700">{errorMessage(notifications.error)}</p>}
      {notifications.data?.length === 0 && <p className="mt-4 text-sm text-gray-500">아직 알림이 없어요.</p>}
      <ul className="mt-4 space-y-2">
        {notifications.data?.map((n) => {
          const link = typeof n.payload?.link === 'string' ? n.payload.link : null
          return (
            <li key={n.id} className={`rounded-lg border p-3 ${n.readAt ? 'border-gray-200' : 'border-green-300 bg-green-50'}`}>
              <p className="text-xs text-gray-500">{formatDateTime(n.sentAt)}</p>
              <p className="mt-1 text-sm font-semibold">{n.title}</p>
              <p className="mt-1 whitespace-pre-line text-sm text-gray-700">{n.body}</p>
              <div className="mt-2 flex gap-3 text-xs">
                {link && (
                  <Link to={link} className="text-green-800 underline" onClick={() => read.mutate(n.id)}>
                    자세히 보기
                  </Link>
                )}
                {!n.readAt && (
                  <button type="button" className="text-gray-500 underline" onClick={() => read.mutate(n.id)}>
                    읽음 표시
                  </button>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </main>
  )
}
