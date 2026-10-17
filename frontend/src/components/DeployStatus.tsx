import { useQuery } from '@tanstack/react-query'
import { fetchDbHealth, fetchHealth } from '../api/health'

// 배포 확인용 상태 표시 (2.2). 실제 홈 화면이 생기면 제거한다.
export default function DeployStatus() {
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: 1 })
  const dbHealth = useQuery({ queryKey: ['health', 'db'], queryFn: fetchDbHealth, retry: 1 })

  const server = health.isPending ? '확인 중' : health.isError ? 'DOWN' : health.data.status
  const db = dbHealth.isPending ? '확인 중' : dbHealth.isError ? '알 수 없음' : dbHealth.data.db
  const time = health.data
    ? new Date(health.data.time).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })
    : '-'

  return (
    <section className="mt-6 rounded-lg border border-gray-200 p-4 text-sm">
      <h2 className="font-semibold">배포 상태</h2>
      <dl className="mt-2 grid grid-cols-[6rem_1fr] gap-y-1">
        <dt className="text-gray-500">서버</dt>
        <dd className={server === 'UP' ? 'text-green-700' : 'text-red-700'}>{server}</dd>
        <dt className="text-gray-500">DB</dt>
        <dd className={db === 'UP' ? 'text-green-700' : 'text-red-700'}>{db}</dd>
        <dt className="text-gray-500">서버 시각</dt>
        <dd>{time}</dd>
        <dt className="text-gray-500">API</dt>
        <dd className="break-all text-gray-500">{import.meta.env.VITE_API_BASE_URL || '(미설정)'}</dd>
      </dl>
    </section>
  )
}
