import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import { AppShell, EmptyState, ErrorText, PageTitle } from '../../components/ui'
import { formatDateTime } from '../../lib/format'

// 참가자 알림함 (/app/notifications, INF-01). 눌러서 읽음 처리하고, 연결된 신청으로 이동한다.
export default function NotificationsPage() {
  const queryClient = useQueryClient()
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: api.myNotifications })
  const read = useMutation({
    mutationFn: (id: number) => api.readNotification(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const unread = notifications.data?.filter((n) => !n.readAt).length ?? 0

  return (
    <AppShell>
      <PageTitle kicker="Letters for you." title="알림" right={unread > 0 ? <span className="rounded-full bg-coral px-2.5 py-0.5 text-[11px] font-bold">새 알림 {unread}</span> : undefined} />
      <div className="flex flex-col gap-2.5 px-[18px]">
        {notifications.isPending && <p className="text-sm text-muted">불러오는 중…</p>}
        {notifications.isError && <ErrorText>{errorMessage(notifications.error)}</ErrorText>}
        {notifications.data?.length === 0 && <EmptyState>아직 알림이 없어요.</EmptyState>}
        {notifications.data?.map((n) => {
          const link = typeof n.payload?.link === 'string' ? n.payload.link : null
          return (
            <article
              key={n.id}
              className={`rounded-xl border border-line p-3.5 ${n.readAt ? 'bg-card' : 'bg-card shadow-print'}`}
            >
              <div className="flex items-center justify-between text-[10px] tracking-[1.5px] text-muted">
                <span>{formatDateTime(n.sentAt)}</span>
                {!n.readAt && <span className="rounded-full bg-coral px-2.5 py-0.5 font-bold text-ink">NEW</span>}
              </div>
              <h2 className="mt-1.5 font-serif text-base font-black">{n.title}</h2>
              <p className="mt-1 text-sm leading-relaxed whitespace-pre-line text-body">{n.body}</p>
              <div className="mt-1 flex gap-4 text-xs font-bold">
                {link && (
                  <Link
                    to={link}
                    className="flex min-h-[44px] items-center text-accent"
                    onClick={() => read.mutate(n.id)}
                  >
                    자세히 보기 →
                  </Link>
                )}
                {!n.readAt && (
                  <button type="button" className="min-h-[44px] text-muted" onClick={() => read.mutate(n.id)}>
                    읽음 표시
                  </button>
                )}
              </div>
            </article>
          )
        })}
      </div>
    </AppShell>
  )
}
