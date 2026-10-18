import { NavLink } from 'react-router-dom'
import SessionBar from './SessionBar'
import type { ReactNode } from 'react'

// 운영자 웹 공통 레이아웃: 좌측 메뉴 + 본문. 데스크톱 우선.
// [해석] 8.1(V2)의 레이아웃이 main에 들어오면 이 컴포넌트를 그쪽으로 합친다 (메뉴 항목만 맞추면 된다).
const MENU = [
  { to: '/admin', label: '홈', end: true },
  { to: '/admin/slots', label: '회차 관리' },
  { to: '/admin/venues', label: '식당 재고' },
  { to: '/admin/applicants', label: '신청자 목록' },
  { to: '/admin/feedback', label: '피드백 · 신고' },
  { to: '/admin/settings', label: '매칭 설정' },
  { to: '/admin/demo', label: '데모 도구' },
]

export default function AdminLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-gray-50">
      <nav className="w-48 shrink-0 border-r border-gray-200 bg-white p-4">
        <p className="mb-4 font-bold">우연한 식탁 운영</p>
        <ul className="space-y-1 text-sm">
          {MENU.map((m) => (
            <li key={m.to}>
              <NavLink
                to={m.to}
                end={m.end}
                className={({ isActive }) =>
                  `block rounded px-2 py-1.5 ${isActive ? 'bg-gray-900 text-white' : 'text-gray-700 hover:bg-gray-100'}`
                }
              >
                {m.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className="min-w-0 flex-1 p-6">
        <header className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">{title}</h1>
          <SessionBar />
        </header>
        {children}
      </main>
    </div>
  )
}
