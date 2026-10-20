import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import { AppShell, EmptyState, ErrorText, PageTitle } from '../../components/ui'
import { formatDateTime } from '../../lib/format'

const TYPE_LABEL: Record<string, string> = {
  NOTICE: '공지',
  CURATION: '큐레이션',
  REMINDER: '리마인드',
  CONTENT: '대화 주제',
  USER: '',
}

// 대화 탭 (/app/chats): 내가 참여 중인 단체방 목록 (INF-04). 누르면 그 테이블의 단체방으로 들어간다.
export default function ChatListPage() {
  const rooms = useQuery({ queryKey: ['chat-rooms'], queryFn: api.myChatRooms, refetchInterval: 15_000 })

  return (
    <AppShell>
      <PageTitle kicker="Table talk." title="대화" />
      <div className="flex flex-col gap-2.5 px-[18px]">
        {rooms.isPending && <p className="text-sm text-muted">불러오는 중…</p>}
        {rooms.isError && <ErrorText>{errorMessage(rooms.error)}</ErrorText>}
        {rooms.data?.length === 0 && (
          <EmptyState>참여 중인 대화방이 없어요. 매칭이 확정되면 같은 테이블 사람들과의 단체방이 여기에 생겨요.</EmptyState>
        )}
        {rooms.data?.map((r) => {
          const body = (
            <article className="rounded-2xl border border-line bg-card p-3.5">
              <div className="flex items-center justify-between gap-2">
                <h2 className="font-serif text-base font-black">
                  {r.regionName} · {formatDateTime(r.startsAt)}
                </h2>
                {r.roomStatus === 'CLOSED' && (
                  <span className="rounded-full bg-paper px-2 py-0.5 text-[11px] font-bold text-muted">닫힘</span>
                )}
              </div>
              <p className="mt-0.5 text-xs text-muted">
                {r.venueName ? `${r.venueName} · ` : '장소 안내 준비 중 · '}
                {r.memberCount}명 · 메시지 {r.messageCount}
              </p>
              {r.lastMessage && (
                <p className="mt-2 line-clamp-2 text-sm text-body">
                  {TYPE_LABEL[r.lastMessageType ?? ''] ? <b>[{TYPE_LABEL[r.lastMessageType ?? '']}] </b> : null}
                  {r.lastMessage}
                </p>
              )}
              {r.lastMessageAt && <p className="mt-1 text-[11px] text-muted">{formatDateTime(r.lastMessageAt)}</p>}
            </article>
          )
          return r.applicationId != null ? (
            <Link key={r.chatRoomId} to={`/app/applications/${r.applicationId}/table`} className="block">
              {body}
            </Link>
          ) : (
            <div key={r.chatRoomId}>{body}</div>
          )
        })}
      </div>
    </AppShell>
  )
}
