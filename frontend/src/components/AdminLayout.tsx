import { NavLink, Outlet } from 'react-router-dom'
import SessionBar from './SessionBar'

const MENU = [
  { to: '/admin', label: '대시보드', end: true },
  { to: '/admin/exceptions', label: '예외 처리함' },
  { to: '/admin/matching', label: '매칭 모니터' },
  { to: '/admin/reports', label: '안전 신고' },
  { to: '/admin/slots', label: '회차 관리' },
  { to: '/admin/demo', label: '데모 도구' },
]

// 운영자 웹 공통 틀 (데스크톱 우선): 좌측 메뉴 + 본문. 톤앤매너는 9.x에서 입힌다.
export default function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-gray-50 text-gray-900">
      <aside className="w-52 shrink-0 border-r border-gray-200 bg-white p-4">
        <p className="mb-4 text-sm font-bold">우연한 식탁 운영자</p>
        <nav className="flex flex-col gap-1 text-sm">
          {MENU.map((m) => (
            <NavLink
              key={m.to}
              to={m.to}
              end={m.end}
              className={({ isActive }) =>
                `rounded px-3 py-2 ${isActive ? 'bg-gray-900 font-medium text-white' : 'text-gray-700 hover:bg-gray-100'}`
              }
            >
              {m.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <div className="flex justify-end border-b border-gray-200 bg-white px-6 py-2">
          <SessionBar />
        </div>
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
