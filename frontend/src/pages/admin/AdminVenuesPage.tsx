import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { Venue, VenueInput } from '../../api/types'
import AdminLayout from '../../components/AdminLayout'
import { formatDateTime } from '../../lib/format'

const PRICE_RANGES: Record<string, string> = { UNDER_20K: '2만원 미만', '20K_40K': '2~4만원', OVER_40K: '4만원 이상' }
const input = 'rounded border border-gray-300 bg-white px-2 py-1 text-sm'
const EMPTY: VenueInput = { regionId: 0, name: '', address: '', mapUrl: null, priceRange: null, description: null }

// 식당 재고 관리 (/admin/venues): 식당 CRUD + 회차별 수용 테이블 수 (PRD 11장, INF-01·03)
export default function AdminVenuesPage() {
  const venues = useQuery({ queryKey: ['admin-venues'], queryFn: api.adminVenues })
  const regions = useQuery({ queryKey: ['regions'], queryFn: api.regions })
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })
  const [slotId, setSlotId] = useState<number | undefined>()
  const selected = slotId ?? slots.data?.[0]?.id
  const selectedSlot = slots.data?.find((s) => s.id === selected)

  return (
    <AdminLayout title="식당 재고 관리">
      <VenueForm />
      <section className="mt-6">
        <div className="flex items-center gap-2 text-sm">
          <h2 className="font-semibold">회차별 수용 테이블</h2>
          <select className={input} value={selected ?? ''} onChange={(e) => setSlotId(Number(e.target.value))}>
            {slots.data?.map((s) => (
              <option key={s.id} value={s.id}>
                #{s.id} {s.regionName} · {formatDateTime(s.startsAt)}
              </option>
            ))}
          </select>
        </div>
        {venues.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(venues.error)}</p>}
        <ul className="mt-3 space-y-3">
          {venues.data?.map((v) => (
            <VenueRow
              key={v.id}
              venue={v}
              regionName={regions.data?.find((r) => r.id === v.regionId)?.name ?? String(v.regionId)}
              slotId={selected}
              slotRegionId={selectedSlot?.regionId}
            />
          ))}
        </ul>
      </section>
    </AdminLayout>
  )
}

function VenueFields({ value, onChange }: { value: VenueInput; onChange: (v: VenueInput) => void }) {
  const regions = useQuery({ queryKey: ['regions'], queryFn: api.regions })
  const set = (patch: Partial<VenueInput>) => onChange({ ...value, ...patch })
  return (
    <div className="grid grid-cols-2 gap-3 text-sm md:grid-cols-3">
      <label className="flex flex-col gap-1">
        지역
        <select className={input} value={value.regionId || ''} onChange={(e) => set({ regionId: Number(e.target.value) })}>
          <option value="">선택</option>
          {regions.data?.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        이름
        <input required className={input} value={value.name} onChange={(e) => set({ name: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1">
        가격대
        <select className={input} value={value.priceRange ?? ''} onChange={(e) => set({ priceRange: e.target.value || null })}>
          <option value="">미정</option>
          {Object.entries(PRICE_RANGES).map(([k, l]) => (
            <option key={k} value={k}>
              {l}
            </option>
          ))}
        </select>
      </label>
      <label className="col-span-2 flex flex-col gap-1">
        주소
        <input required className={input} value={value.address} onChange={(e) => set({ address: e.target.value })} />
      </label>
      <label className="flex flex-col gap-1">
        지도 링크
        <input className={input} value={value.mapUrl ?? ''} onChange={(e) => set({ mapUrl: e.target.value || null })} />
      </label>
      <label className="col-span-2 flex flex-col gap-1 md:col-span-3">
        큐레이션 문구
        <input className={input} value={value.description ?? ''} onChange={(e) => set({ description: e.target.value || null })} />
      </label>
    </div>
  )
}

function VenueForm() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<VenueInput>(EMPTY)
  const create = useMutation({
    mutationFn: () => api.createVenue(form),
    onSuccess: () => {
      setForm(EMPTY)
      queryClient.invalidateQueries({ queryKey: ['admin-venues'] })
    },
  })
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate()
  }
  return (
    <form onSubmit={onSubmit} className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">식당 추가</h2>
      <VenueFields value={form} onChange={setForm} />
      {create.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(create.error)}</p>}
      <button
        type="submit"
        disabled={create.isPending || !form.regionId}
        className="mt-3 rounded bg-gray-900 px-4 py-2 text-sm text-white disabled:bg-gray-400"
      >
        추가
      </button>
    </form>
  )
}

function VenueRow({ venue, regionName, slotId, slotRegionId }: {
  venue: Venue
  regionName: string
  slotId?: number
  slotRegionId?: number
}) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<VenueInput>(venue)
  const [tables, setTables] = useState('')
  const capacities = useQuery({
    queryKey: ['venue-capacities', slotId],
    queryFn: () => api.venueCapacities(slotId!),
    enabled: !!slotId,
  })
  const capacity = capacities.data?.find((c) => c.venueId === venue.id)
  const save = useMutation({
    mutationFn: () => api.updateVenue(venue.id, { ...form, active: venue.active }),
    onSuccess: () => {
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['admin-venues'] })
    },
  })
  const toggle = useMutation({
    mutationFn: () => api.updateVenue(venue.id, { ...venue, active: !venue.active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-venues'] }),
  })
  const setCapacity = useMutation({
    mutationFn: () => api.setVenueCapacity(venue.id, slotId!, Number(tables)),
    onSuccess: () => {
      setTables('')
      queryClient.invalidateQueries({ queryKey: ['venue-capacities', slotId] })
    },
  })

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p>
          <span className="font-semibold">
            #{venue.id} {venue.name}
          </span>{' '}
          · {regionName} · {PRICE_RANGES[venue.priceRange ?? ''] ?? '가격대 미정'} · {venue.address}
          {!venue.active && <span className="ml-2 rounded bg-gray-200 px-1.5 py-0.5 text-xs">비활성</span>}
        </p>
        <div className="flex gap-2">
          <button type="button" className="rounded border px-3 py-1" onClick={() => setEditing((e) => !e)}>
            {editing ? '닫기' : '수정'}
          </button>
          <button type="button" className="rounded border px-3 py-1" disabled={toggle.isPending} onClick={() => toggle.mutate()}>
            {venue.active ? '비활성화' : '활성화'}
          </button>
        </div>
      </div>
      {venue.description && <p className="mt-1 text-gray-600">{venue.description}</p>}
      {editing && (
        <form
          className="mt-3"
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate()
          }}
        >
          <VenueFields value={form} onChange={setForm} />
          {save.isError && <p className="mt-2 text-red-700">{errorMessage(save.error)}</p>}
          <button type="submit" disabled={save.isPending} className="mt-3 rounded bg-gray-900 px-4 py-1.5 text-white">
            저장
          </button>
        </form>
      )}
      {slotId && slotRegionId === venue.regionId && (
        <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
          <span>이 회차 수용 {capacity ? `${capacity.maxTables}테이블 (배정 ${capacity.reservedTables})` : '미설정'}</span>
          <input
            type="number"
            min={capacity?.reservedTables ?? 0}
            className={`${input} w-24`}
            value={tables}
            placeholder="테이블 수"
            onChange={(e) => setTables(e.target.value)}
          />
          <button
            type="button"
            disabled={tables === '' || setCapacity.isPending}
            className="rounded bg-amber-800 px-3 py-1 text-white disabled:bg-gray-300"
            onClick={() => setCapacity.mutate()}
          >
            수용량 저장
          </button>
          {setCapacity.isError && <span className="text-red-700">{errorMessage(setCapacity.error)}</span>}
        </div>
      )}
    </li>
  )
}
