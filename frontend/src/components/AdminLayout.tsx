import { NavLink } from 'react-router-dom'
import SessionBar from './SessionBar'
import DemoClock from './DemoClock'
import GuidePill from './GuidePill'
import type { ReactNode } from 'react'

// 운영자 웹 공통 레이아웃: 좌측 메뉴 + 본문. 데스크톱 우선.
// [해석] 8.1(V2)의 레이아웃이 main에 들어오면 이 컴포넌트를 그쪽으로 합친다 (메뉴 항목만 맞추면 된다).
const MENU = [
  { to: '/guide', label: '📋 시연 가이드' },
  { to: '/admin', label: '대시보드', end: true },
  { to: '/admin/exceptions', label: '예외 처리함' },
  { to: '/admin/matching', label: '매칭 모니터' },
  { to: '/admin/slots', label: '회차 관리' },
  { to: '/admin/venues', label: '식당 재고' },
  { to: '/admin/applicants', label: '신청자 목록' },
  { to: '/admin/payments', label: '결제·환불' },
  { to: '/admin/attendance', label: '참석 관리' },
  { to: '/admin/messages', label: '자동 메시지' },
  { to: '/admin/feedback', label: '피드백 요약' },
  { to: '/admin/reports', label: '안전 신고' },
  { to: '/admin/settings', label: '매칭 설정' },
  { to: '/admin/demo', label: '데모 도구' },
]

export default function AdminLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-paper">
      <GuidePill />
      <nav className="w-48 shrink-0 border-r-[1.5px] border-ink bg-card p-4">
        <p className="mb-4 font-serif text-lg font-black">우연한 식탁 운영</p>
        <ul className="space-y-1 text-sm">
          {MENU.map((m) => (
            <li key={m.to}>
              <NavLink
                to={m.to}
                end={m.end}
                className={({ isActive }) =>
                  `block rounded px-2 py-1.5 ${isActive ? 'bg-ink text-cream' : 'text-gray-700 hover:bg-gray-100'}`
                }
              >
                {m.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main className="min-w-0 flex-1 p-6 [&_h2]:font-serif [&_h3]:font-serif">
        <header className="flex items-center justify-between">
          <h1 className="font-serif text-2xl font-black">{title}</h1>
          <DemoClock />
          <SessionBar />
        </header>
        {children}
      </main>
    </div>
  )
}
