import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'

const LABELS: Record<string, string> = {
  S1: 'S1 연령 밀집도',
  S2: 'S2 성비',
  S3: 'S3 MBTI 구성',
  S4: 'S4 공통 관심사',
  S5: 'S5 희망 상대 성향',
  S6: 'S6 새로운 사람 비율',
}

// 매칭 가중치·최소 점수 (MAT-15). 다음 매칭 실행부터 적용된다.
export default function AdminSettingsPage() {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['matching-settings'], queryFn: api.matchingSettings })
  const [weights, setWeights] = useState<Record<string, string>>({})
  const [minScore, setMinScore] = useState('')
  useEffect(() => {
    if (settings.data) {
      setWeights(Object.fromEntries(Object.entries(settings.data.weights).map(([k, v]) => [k, String(v)])))
      setMinScore(String(settings.data.minScore))
    }
  }, [settings.data])
  const sum = Object.values(weights).reduce((a, v) => a + (Number(v) || 0), 0)
  const sumOk = Math.abs(sum - 100) < 0.0001
  const save = useMutation({
    mutationFn: () =>
      api.saveMatchingSettings({
        weights: Object.fromEntries(Object.entries(weights).map(([k, v]) => [k, Number(v)])),
        minScore: Number(minScore),
      }),
    onSuccess: (data) => queryClient.setQueryData(['matching-settings'], data),
  })
  const field = 'w-24 rounded border border-gray-300 px-2 py-1 text-right'

  return (
    <AdminLayout title="매칭 설정">
      <p className="mt-2 text-sm text-gray-600">변경한 값은 다음 매칭 실행부터 적용되고, 실행 기록에 그때의 값이 남습니다.</p>
      <form
        className="mt-4 max-w-md rounded-lg border border-gray-200 bg-white p-4"
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <h2 className="font-semibold">선호 점수 가중치</h2>
        <div className="mt-3 space-y-2 text-sm">
          {Object.keys(weights).map((code) => (
            <label key={code} className="flex items-center justify-between gap-3">
              {LABELS[code] ?? code}
              <input
                type="number"
                min={0}
                max={100}
                step="any"
                className={field}
                value={weights[code]}
                onChange={(e) => setWeights({ ...weights, [code]: e.target.value })}
              />
            </label>
          ))}
        </div>
        <p className={`mt-2 text-sm ${sumOk ? 'text-gray-600' : 'text-red-700'}`}>합계 {sum} / 100</p>
        <label className="mt-4 flex items-center justify-between gap-3 text-sm">
          자동 확정 최소 점수 (0~100)
          <input
            type="number"
            min={0}
            max={100}
            step="any"
            className={field}
            value={minScore}
            onChange={(e) => setMinScore(e.target.value)}
          />
        </label>
        {settings.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(settings.error)}</p>}
        {save.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(save.error)}</p>}
        {save.isSuccess && <p className="mt-2 text-sm text-green-700">저장했습니다.</p>}
        <button
          type="submit"
          disabled={save.isPending || !sumOk}
          className="mt-4 rounded bg-gray-900 px-4 py-2 text-sm text-white disabled:bg-gray-400"
        >
          저장
        </button>
      </form>
    </AdminLayout>
  )
}
