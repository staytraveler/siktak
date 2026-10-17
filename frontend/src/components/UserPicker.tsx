import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../api/endpoints'
import { getUserId, setUserId, type Area } from '../api/currentUser'

// [임시 — 2.3] 로그인 대신 테스트 계정을 고른다. 3.1 로그인 화면으로 교체.
export default function UserPicker({ area }: { area: Area }) {
  const role = area === 'admin' ? 'OPERATOR' : 'PARTICIPANT'
  const queryClient = useQueryClient()
  const { data: users = [] } = useQuery({ queryKey: ['dev-users'], queryFn: api.devUsers })
  const candidates = users.filter((u) => u.role === role)
  const [stored, setStored] = useState<string | null>(() => getUserId(area))
  // 저장된 값이 없거나 목록에 없으면 첫 계정을 쓴다
  const selected = candidates.some((u) => String(u.id) === stored)
    ? stored
    : candidates.length > 0
      ? String(candidates[0].id)
      : null

  // 화면에서 고른 값을 헤더용 저장소(localStorage)에 맞춘다
  useEffect(() => {
    if (selected && getUserId(area) !== selected) {
      setUserId(area, selected)
      queryClient.invalidateQueries()
    }
  }, [area, selected, queryClient])

  return (
    <label className="flex items-center gap-2 text-xs text-gray-600">
      <span className="shrink-0">테스트 계정</span>
      <select
        className="rounded border border-gray-300 bg-white px-2 py-1 text-sm text-gray-900"
        value={selected ?? ''}
        onChange={(e) => {
          setUserId(area, e.target.value)
          setStored(e.target.value)
          queryClient.invalidateQueries()
        }}
      >
        {candidates.map((u) => (
          <option key={u.id} value={u.id}>
            {u.name} ({u.loginId})
          </option>
        ))}
      </select>
    </label>
  )
}
