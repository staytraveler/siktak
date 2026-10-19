import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../../api/endpoints'
import type { ChatMessageItem } from '../../api/types'
import { Avatar, BackHeader } from '../../components/ui'

import { dateParts, durationLabel, mainSlot } from '../../lib/design'
import { formatDateTime } from '../../lib/format'
import { useTableInfo } from '../../lib/useTableInfo'
import { getSession } from '../../api/session'

const TOPIC_BG = ['bg-mustard', 'bg-sage', 'bg-sky']
const MESSAGE_LABEL: Record<string, string> = {
  NOTICE: '모임 안내',
  CURATION: "Editor's note",
  REMINDER: '리마인드',
  CONTENT: '대화 주제',
}

// 오늘의 테이블 (/app/applications/:id/table): 확정 후 단체방. 시스템 안내 메시지를 읽는다. (Table.html)
// 하단 입력창으로 구성원이 메시지를 보낸다 (POST /tables/{id}/messages).
export default function TablePage() {
  const id = Number(useParams().id)
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: api.myApplications })
  const application = applications.data?.find((a) => a.id === id)
  const { table, messages, venue } = useTableInfo(id, !!application)
  const companions = useQuery({
    queryKey: ['companions', id],
    queryFn: () => api.companions(id),
    enabled: !!application,
    retry: false,
  })
  const slot = application ? mainSlot(application) : null
  const me = getSession()?.user.name ?? '나'
  const d = slot ? dateParts(slot.startsAt) : null
  const sub = slot && d ? `${d.md} ${d.weekday} ${d.hm} · ${venue.venueName ?? slot.regionName}` : undefined
  const names = companions.data?.map((c) => c.name) ?? []

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-paper text-ink">
      <BackHeader to="/app" title="오늘의 테이블" sub={sub}>
        <div className="flex gap-2.5 overflow-x-auto px-1.5 pt-2.5">
          <Person label={me} caption="나" index={0} me />
          {names.map((n, i) => (
            <Person key={n + i} label={n} caption={n} index={i + 1} />
          ))}
          {names.length === 0 &&
            table.data &&
            Array.from({ length: Math.max(table.data.memberCount - 1, 0) }, (_, i) => (
              <Person key={i} label="·" caption="동석자" index={i + 1} />
            ))}
        </div>
      </BackHeader>

      <div className="flex flex-1 flex-col gap-3.5 px-4 pt-4 pb-8">
        {(applications.isPending || table.isPending) && <p className="text-sm text-muted">불러오는 중…</p>}
        {applications.isSuccess && !application && (
          <p className="text-sm text-muted">이 계정의 신청이 아니에요. 테스트 계정을 확인해주세요.</p>
        )}
        {application && table.isError && <p className="text-sm text-muted">아직 확정된 테이블이 없어요.</p>}
        {table.data && slot && (
          <p className="text-[11px] tracking-[1.5px] text-muted">
            {table.data.memberCount}명 · {durationLabel(slot.startsAt, slot.endsAt)} ·{' '}
            {venue.reservation ? `예약명 ${venue.reservation}` : '예약명은 확정 후 안내'}
          </p>
        )}
        {messages.data?.length === 0 && <p className="text-sm text-muted">아직 도착한 안내가 없어요.</p>}
        {messages.data?.map((m) => <Message key={m.id} m={m} />)}
      </div>
      {table.data && <Composer tableId={table.data.tableId} chatRoomId={table.data.chatRoomId} />}
    </div>
  )
}

function Composer({ tableId, chatRoomId }: { tableId: number; chatRoomId: number | null }) {
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const send = useMutation({
    mutationFn: (content: string) => api.sendTableMessage(tableId, content),
    onSuccess: () => {
      setText('')
      return queryClient.invalidateQueries({ queryKey: ['chat', chatRoomId] })
    },
  })
  const content = text.trim()
  return (
    <form
      className="sticky bottom-0 border-t-[1.5px] border-ink bg-paper px-4 py-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (content && !send.isPending) send.mutate(content)
      }}
    >
      {send.isError && <p className="mb-1.5 text-[11px] text-accent">메시지를 보내지 못했어요. 이용 제한 상태인지 확인해주세요.</p>}
      <div className="flex gap-2">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={500}
          placeholder="동석자에게 메시지 보내기"
          className="min-w-0 flex-1 border-[1.5px] border-ink bg-card px-3 py-2.5 text-sm outline-none"
        />
        <button
          type="submit"
          disabled={!content || send.isPending}
          className="shrink-0 border-[1.5px] border-ink bg-ink px-4 text-sm font-bold text-cream disabled:opacity-40"
        >
          보내기
        </button>
      </div>
    </form>
  )
}

function Person({ label, caption, index, me }: { label: string; caption: string; index: number; me?: boolean }) {
  return (
    <div className="flex w-12 shrink-0 flex-col items-center gap-1">
      <Avatar label={label} index={index} me={me} />
      <span className="max-w-full truncate text-[10px]">{caption}</span>
    </div>
  )
}

function Message({ m }: { m: ChatMessageItem }) {
  if (m.senderType === 'USER') {
    return (
      <div className={`flex items-end gap-2 ${m.mine ? 'flex-row-reverse' : ''}`}>
        {!m.mine && (
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sage text-[11px] font-bold">
            {m.senderLabel ?? '?'}
          </span>
        )}
        <div className="flex min-w-0 flex-col gap-0.5">
          <div
            className={`max-w-[260px] border-[1.5px] border-ink px-3 py-2.5 text-sm leading-normal whitespace-pre-line [overflow-wrap:anywhere] ${m.mine ? 'bg-mustard' : 'bg-card'}`}
          >
            {m.body}
          </div>
          <span className={`text-[10px] text-muted ${m.mine ? 'text-right' : ''}`}>{formatDateTime(m.createdAt)}</span>
        </div>
      </div>
    )
  }
  const topics = Array.isArray(m.payload?.topics) ? (m.payload.topics as { title: string; body?: string }[]) : []

  if (m.messageType === 'CURATION') {
    return (
      <article className="border-[1.5px] border-ink bg-card p-4 shadow-print">
        <span className="font-display text-base text-accent italic">{MESSAGE_LABEL.CURATION}</span>
        <p className="mt-2 font-serif text-base leading-relaxed font-bold whitespace-pre-line">{m.body}</p>
      </article>
    )
  }

  if (m.messageType === 'CONTENT') {
    return (
      <section>
        <div className="mb-2 text-[10px] tracking-[2px] text-muted">오늘의 대화 주제</div>
        {topics.length > 0 ? (
          <div className="flex gap-2">
            {topics.slice(0, 3).map((t, i) => (
              <div key={i} className={`flex min-h-[104px] flex-1 flex-col gap-1.5 px-2.5 py-3 ${TOPIC_BG[i % 3]}`}>
                <span className="font-display text-[22px] font-black">{String(i + 1).padStart(2, '0')}</span>
                <span className="text-xs leading-[1.45] font-medium">{t.title}</span>
              </div>
            ))}
          </div>
        ) : (
          <p className="border-[1.5px] border-ink bg-card p-3 text-sm leading-relaxed whitespace-pre-line">{m.body}</p>
        )}
      </section>
    )
  }

  // 공지·리마인드 등 시스템 메시지
  return (
    <div className="flex flex-col gap-2">
      <div className="self-center text-[11px] text-muted">
        — 우연한 식탁 {MESSAGE_LABEL[m.messageType] ?? '공지'} · {formatDateTime(m.createdAt)} —
      </div>
      <div className="flex items-end gap-2">
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink text-[11px] font-bold text-cream">
          {m.senderType === 'SYSTEM' ? '식' : '참'}
        </span>
        <div className="min-w-0 max-w-[300px] border-[1.5px] border-ink bg-card px-3 py-2.5 text-sm leading-normal whitespace-pre-line [overflow-wrap:anywhere]">
          {m.body}
        </div>
      </div>
    </div>
  )
}
