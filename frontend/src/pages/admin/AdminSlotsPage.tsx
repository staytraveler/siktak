import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { Slot, SlotInput } from '../../api/types'
import AdminLayout from '../../components/AdminLayout'
import { formatDateTime, toIso, toLocalInput } from '../../lib/format'

const input = 'rounded border border-gray-300 bg-white px-2 py-1 text-sm'

// 회차 관리 (/admin/slots): 생성·수정 폼 + 목록 + [2.3] 매칭 실행 버튼(스케줄러 대체)
export default function AdminSlotsPage() {
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })

  return (
    <AdminLayout title="회차 관리">
      <CreateSlotForm />
      <section className="mt-6 space-y-3">
        {slots.isError && <p className="text-sm text-red-700">{errorMessage(slots.error)}</p>}
        {slots.data?.map((s) => <SlotRow key={s.id} slot={s} />)}
      </section>
    </AdminLayout>
  )
}

interface SlotFormValue {
  regionId: string
  startsAt: string
  endsAt: string
  capacity: string
  applyDeadlineAt: string
  matchRunAt: string
}

const EMPTY: SlotFormValue = { regionId: '', startsAt: '', endsAt: '', capacity: '30', applyDeadlineAt: '', matchRunAt: '' }

const fromSlot = (s: Slot): SlotFormValue => ({
  regionId: String(s.regionId),
  startsAt: toLocalInput(s.startsAt),
  endsAt: toLocalInput(s.endsAt),
  capacity: String(s.capacity),
  applyDeadlineAt: toLocalInput(s.applyDeadlineAt),
  matchRunAt: toLocalInput(s.matchRunAt),
})

// 생성·수정 공용 입력 폼
function SlotForm({ initial, submitLabel, onSubmit, pending, error }: {
  initial: SlotFormValue
  submitLabel: string
  onSubmit: (body: SlotInput) => void
  pending: boolean
  error: unknown
}) {
  const regions = useQuery({ queryKey: ['regions'], queryFn: api.regions })
  const [form, setForm] = useState(initial)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({
      regionId: Number(form.regionId || regions.data?.[0]?.id),
      startsAt: toIso(form.startsAt),
      endsAt: toIso(form.endsAt),
      capacity: Number(form.capacity),
      applyDeadlineAt: toIso(form.applyDeadlineAt),
      matchRunAt: toIso(form.matchRunAt),
    })
  }
  const dt = (name: 'startsAt' | 'endsAt' | 'applyDeadlineAt' | 'matchRunAt', text: string) => (
    <label className="flex flex-col gap-1">
      {text}
      <input type="datetime-local" required className={input} value={form[name]}
        onChange={(e) => setForm({ ...form, [name]: e.target.value })} />
    </label>
  )

  return (
    <form onSubmit={submit}>
      <div className="grid grid-cols-2 gap-3 text-sm md:grid-cols-6">
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
        {dt('startsAt', '시작')}
        {dt('endsAt', '종료')}
        {dt('applyDeadlineAt', '신청 마감')}
        {dt('matchRunAt', '매칭 실행')}
        <label className="flex flex-col gap-1">
          정원
          <input type="number" min={4} required className={input} value={form.capacity}
            onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </label>
      </div>
      {error != null && <p className="mt-2 text-sm text-red-700">{errorMessage(error)}</p>}
      <button type="submit" disabled={pending}
        className="mt-3 rounded bg-gray-900 px-4 py-2 text-sm text-white disabled:bg-gray-400">
        {submitLabel}
      </button>
    </form>
  )
}

function CreateSlotForm() {
  const queryClient = useQueryClient()
  const [formKey, setFormKey] = useState(0)
  const create = useMutation({
    mutationFn: api.createSlot,
    onSuccess: () => {
      setFormKey((k) => k + 1)
      queryClient.invalidateQueries({ queryKey: ['admin-slots'] })
    },
  })
  return (
    <div className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">회차 만들기</h2>
      <SlotForm key={formKey} initial={EMPTY} submitLabel="만들기" onSubmit={(b) => create.mutate(b)}
        pending={create.isPending} error={create.error} />
    </div>
  )
}

function SlotRow({ slot }: { slot: Slot }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
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
  const update = useMutation({
    mutationFn: (body: SlotInput) => api.updateSlot(slot.id, body),
    onSuccess: () => {
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['admin-slots'] })
    },
  })

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-sm">
          <span className="font-semibold">#{slot.id} {slot.regionName}</span> · {formatDateTime(slot.startsAt)} · 정원{' '}
          {slot.capacity} · 마감 {formatDateTime(slot.applyDeadlineAt)} · 매칭 {formatDateTime(slot.matchRunAt)} ·{' '}
          <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs">{slot.slotStatus}</span>
        </div>
        <div className="flex gap-2">
          {slot.slotStatus === 'OPEN' && (
            <button type="button" onClick={() => setEditing((e) => !e)} className="rounded border px-3 py-1 text-sm">
              {editing ? '수정 닫기' : '수정'}
            </button>
          )}
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
      {editing && (
        <div className="mt-3 border-t border-gray-100 pt-3">
          <SlotForm initial={fromSlot(slot)} submitLabel="저장" onSubmit={(b) => update.mutate(b)}
            pending={update.isPending} error={update.error} />
          <p className="mt-2 text-xs text-gray-500">매칭 실행 시각을 바꾸면 자동 매칭 일정도 새 시각으로 다시 등록됩니다.</p>
        </div>
      )}
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
