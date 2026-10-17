import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { Slot } from '../../api/types'
import SessionBar from '../../components/SessionBar'
import { formatDateTime } from '../../lib/format'

// 회차 관리 (/admin/slots): 생성 폼 + 목록 + [2.3] 매칭 실행 버튼(스케줄러 대체)
export default function AdminSlotsPage() {
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })

  return (
    <main className="min-h-screen bg-gray-50 p-6">
      <header className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-sm text-gray-500">
            운영자 홈
          </Link>
          <h1 className="text-2xl font-bold">회차 관리</h1>
        </div>
        <SessionBar />
      </header>
      <CreateSlotForm />
      <section className="mt-6 space-y-3">
        {slots.isError && <p className="text-sm text-red-700">{errorMessage(slots.error)}</p>}
        {slots.data?.map((s) => <SlotRow key={s.id} slot={s} />)}
      </section>
    </main>
  )
}

// datetime-local 값(서울 시각)을 +09:00 ISO로 바꾼다
const toIso = (local: string) => (local ? `${local}:00+09:00` : '')

function CreateSlotForm() {
  const queryClient = useQueryClient()
  const regions = useQuery({ queryKey: ['regions'], queryFn: api.regions })
  const [form, setForm] = useState({ regionId: '', startsAt: '', endsAt: '', capacity: '30', matchRunAt: '' })
  const create = useMutation({
    mutationFn: () =>
      api.createSlot({
        regionId: Number(form.regionId || regions.data?.[0]?.id),
        startsAt: toIso(form.startsAt),
        endsAt: toIso(form.endsAt),
        capacity: Number(form.capacity),
        // demo-scenario.md: 신청 마감 = 매칭 실행 시각
        applyDeadlineAt: toIso(form.matchRunAt),
        matchRunAt: toIso(form.matchRunAt),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-slots'] }),
  })
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate()
  }
  const input = 'rounded border border-gray-300 bg-white px-2 py-1 text-sm'

  return (
    <form onSubmit={onSubmit} className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="font-semibold">회차 만들기</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-5">
        <label className="flex flex-col gap-1">
          지역
          <select className={input} value={form.regionId} onChange={(e) => setForm({ ...form, regionId: e.target.value })}>
            {regions.data?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          시작
          <input type="datetime-local" required className={input} value={form.startsAt}
            onChange={(e) => setForm({ ...form, startsAt: e.target.value })} />
        </label>
        <label className="flex flex-col gap-1">
          종료
          <input type="datetime-local" required className={input} value={form.endsAt}
            onChange={(e) => setForm({ ...form, endsAt: e.target.value })} />
        </label>
        <label className="flex flex-col gap-1">
          매칭 실행·신청 마감
          <input type="datetime-local" required className={input} value={form.matchRunAt}
            onChange={(e) => setForm({ ...form, matchRunAt: e.target.value })} />
        </label>
        <label className="flex flex-col gap-1">
          정원
          <input type="number" min={4} required className={input} value={form.capacity}
            onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </label>
      </div>
      {create.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(create.error)}</p>}
      <button type="submit" disabled={create.isPending}
        className="mt-3 rounded bg-gray-900 px-4 py-2 text-sm text-white disabled:bg-gray-400">
        만들기
      </button>
    </form>
  )
}

function SlotRow({ slot }: { slot: Slot }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const tables = useQuery({
    queryKey: ['slot-tables', slot.id],
    queryFn: () => api.slotTables(slot.id),
    enabled: open,
  })
  const run = useMutation({
    mutationFn: () => api.runMatching(slot.id),
    onSuccess: () => {
      setOpen(true)
      queryClient.invalidateQueries({ queryKey: ['admin-slots'] })
      queryClient.invalidateQueries({ queryKey: ['slot-tables', slot.id] })
    },
  })

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm">
          <span className="font-semibold">#{slot.id} {slot.regionName}</span> · {formatDateTime(slot.startsAt)} · 정원{' '}
          {slot.capacity} · 매칭 {formatDateTime(slot.matchRunAt)} ·{' '}
          <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs">{slot.slotStatus}</span>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => setOpen((o) => !o)} className="rounded border px-3 py-1 text-sm">
            {open ? '접기' : '테이블 보기'}
          </button>
          <button type="button" disabled={run.isPending} onClick={() => run.mutate()}
            className="rounded bg-amber-800 px-3 py-1 text-sm text-white disabled:bg-gray-400"
            title="2.3 임시: 스케줄러 대신 수동 실행. 다시 눌러도 결과가 중복되지 않습니다.">
            매칭 실행 (임시)
          </button>
        </div>
      </div>
      {run.data && (
        <p className="mt-2 text-xs text-gray-600">
          실행 #{run.data.id} ({run.data.algorithmVersion}) · 후보 {run.data.candidateCount}명 · 결과{' '}
          {JSON.stringify(run.data.summary)}
        </p>
      )}
      {run.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(run.error)}</p>}
      {open && (
        <ul className="mt-3 grid gap-2 md:grid-cols-2">
          {tables.data?.length === 0 && <li className="text-sm text-gray-500">아직 테이블이 없습니다.</li>}
          {tables.data?.map((t) => (
            <li key={t.id} className="rounded border border-gray-200 p-3 text-sm">
              <p className="font-medium">
                테이블 #{t.id} · {t.memberCount}명 · {t.tableStatus} · 단체방 #{t.chatRoomId ?? '-'}
              </p>
              <p className="mt-1 text-gray-600">{t.members.map((m) => m.name ?? m.userId).join(', ')}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
