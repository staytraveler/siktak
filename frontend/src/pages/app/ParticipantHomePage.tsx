import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import DeployStatus from '../../components/DeployStatus'
import StatusBadges from '../../components/StatusBadges'
import UserPicker from '../../components/UserPicker'
import { formatDateTime } from '../../lib/format'

// 참가자 앱 홈 (/app). 모바일 우선. 위에서 내 상태, 아래에서 모집 중 회차 신청.
export default function ParticipantHomePage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: api.myApplications })
  const slots = useQuery({ queryKey: ['open-slots'], queryFn: api.openSlots })
  // 체크한 순서 = 우선순위 (matching.md MD7)
  const [picked, setPicked] = useState<number[]>([])

  const submit = useMutation({
    mutationFn: () => api.submitApplication(picked),
    onSuccess: (created) => {
      setPicked([])
      queryClient.invalidateQueries({ queryKey: ['my-applications'] })
      navigate(`/app/applications/${created.id}`)
    },
  })

  const toggle = (id: number) =>
    setPicked((prev) => (prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]))

  return (
    <main className="mx-auto min-h-screen max-w-md bg-white p-4">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-bold">우연한 식탁</h1>
        <UserPicker area="app" />
      </header>

      <section className="mt-6">
        <h2 className="text-sm font-semibold text-gray-500">내 신청</h2>
        {applications.isPending && <p className="mt-2 text-sm text-gray-500">불러오는 중…</p>}
        {applications.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(applications.error)}</p>}
        {applications.data?.length === 0 && <p className="mt-2 text-sm text-gray-500">아직 신청한 식사가 없어요.</p>}
        <ul className="mt-2 space-y-2">
          {applications.data?.map((a) => (
            <li key={a.id}>
              <Link
                to={`/app/applications/${a.id}`}
                className="block rounded-lg border border-gray-200 p-3 active:bg-gray-50"
              >
                <p className="text-sm font-medium">
                  {a.slotChoices.map((c) => c.slot && `${c.slot.regionName} ${formatDateTime(c.slot.startsAt)}`).join(' · ')}
                </p>
                <div className="mt-2">
                  <StatusBadges application={a} />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-8">
        <h2 className="text-sm font-semibold text-gray-500">모집 중인 식탁</h2>
        <p className="mt-1 text-xs text-gray-500">참가 가능한 일정을 모두 고르세요. 먼저 고른 순서대로 우선 매칭합니다.</p>
        {slots.data?.length === 0 && <p className="mt-2 text-sm text-gray-500">모집 중인 회차가 없어요.</p>}
        <ul className="mt-2 space-y-2">
          {slots.data?.map((s) => {
            const order = picked.indexOf(s.id)
            return (
              <li key={s.id}>
                <button
                  type="button"
                  onClick={() => toggle(s.id)}
                  className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left ${
                    order >= 0 ? 'border-amber-700 bg-amber-50' : 'border-gray-200'
                  }`}
                >
                  <span
                    className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs ${
                      order >= 0 ? 'bg-amber-700 text-white' : 'border border-gray-300'
                    }`}
                  >
                    {order >= 0 ? order + 1 : ''}
                  </span>
                  <span className="text-sm">
                    <span className="font-medium">{s.regionName}</span> {formatDateTime(s.startsAt)}
                    <span className="block text-xs text-gray-500">신청 마감 {formatDateTime(s.applyDeadlineAt)}</span>
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
        <p className="mt-3 text-xs text-gray-500">
          1회권 9,900원 · 행사 2일 전부터 환불 불가 · 당일 취소·노쇼 시 이용이 제한될 수 있어요.
        </p>
        {submit.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(submit.error)}</p>}
        <button
          type="button"
          disabled={picked.length === 0 || submit.isPending}
          onClick={() => submit.mutate()}
          className="mt-3 w-full rounded-lg bg-amber-800 py-3 text-sm font-semibold text-white disabled:bg-gray-300"
        >
          {picked.length === 0 ? '일정을 골라주세요' : `${picked.length}개 일정으로 신청하기`}
        </button>
      </section>

      <DeployStatus />
    </main>
  )
}
