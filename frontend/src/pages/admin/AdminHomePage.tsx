import { Link } from 'react-router-dom'
import UserPicker from '../../components/UserPicker'

// 운영자 웹 홈 (/admin). 데스크톱 우선. 운영 대시보드는 8.x에서 채운다.
export default function AdminHomePage() {
  return (
    <main className="min-h-screen bg-gray-50 p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">우연한 식탁 운영자</h1>
        <UserPicker area="admin" />
      </header>
      <nav className="mt-6 flex flex-wrap gap-3">
        <Link to="/admin/slots" className="inline-block rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
          회차 관리 · 매칭 실행 →
        </Link>
        <Link to="/admin/demo" className="inline-block rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
          데모 도구 (시간 앞당기기 · 시드 초기화) →
        </Link>
      </nav>
    </main>
  )
}
