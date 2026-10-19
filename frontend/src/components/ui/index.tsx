import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import Illustration from './Illustration'
import type { IllustrationName } from '../../design/assets'
import { dateParts } from '../../lib/design'

export { default as Illustration } from './Illustration'

// 앱 공통 틀: 390px 안팎 단일 열 + 하단 메뉴
export function AppShell({ children, nav = true }: { children: ReactNode; nav?: boolean }) {
  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-paper text-ink">
      <div className="flex-1 pb-6">{children}</div>
      {nav && <BottomNav />}
    </div>
  )
}

export function Masthead({ tone = 'dark', right }: { tone?: 'dark' | 'light'; right?: ReactNode }) {
  return (
    <header className="flex items-baseline justify-between px-[22px] pt-4">
      <span className="font-serif text-xl font-black tracking-[-0.5px]">우연한 식탁</span>
      {right ?? <span className={`text-[10px] tracking-[2px] ${tone === 'dark' ? 'opacity-80' : ''}`}>No.12 · AUTUMN</span>}
    </header>
  )
}

const HERO_BG = { accent: 'bg-accent text-cream', mustard: 'bg-mustard text-ink', ink: 'bg-ink text-cream' }

export function HeroBlock({
  tone = 'accent',
  kicker,
  title,
  illustration,
  height = 360,
  illustrationHeight = 170,
  illustrationOffset = 0,
  sticker,
  mastheadRight,
  children,
}: {
  tone?: keyof typeof HERO_BG
  kicker: string
  title: ReactNode
  illustration?: IllustrationName
  height?: number
  illustrationHeight?: number
  illustrationOffset?: number
  sticker?: ReactNode
  mastheadRight?: ReactNode
  children?: ReactNode
}) {
  return (
    <section className={`relative overflow-hidden ${HERO_BG[tone]}`} style={{ minHeight: height }}>
      <Masthead tone={tone === 'mustard' ? 'light' : 'dark'} right={mastheadRight} />
      <div className="relative z-10 px-[22px] pt-5">
        <div className="font-display text-[17px] italic opacity-90">{kicker}</div>
        <h1 className="mt-1.5 font-serif text-[29px] leading-[1.3] font-black tracking-[-1.2px]">{title}</h1>
        {children}
      </div>
      {sticker && <div className="absolute top-[58px] right-[18px] z-20">{sticker}</div>}
      {illustration && (
        <Illustration
          name={illustration}
          className="pointer-events-none absolute left-0 w-full"
          style={{ height: illustrationHeight, bottom: illustrationOffset }}
        />
      )}
    </section>
  )
}

export function Sticker({ top, bottom }: { top: string; bottom?: string }) {
  return (
    <div className="flex h-[66px] w-[66px] rotate-[10deg] flex-col items-center justify-center rounded-full bg-mustard text-ink shadow-[0_3px_0_#1c1a17]">
      <span className="font-display text-[22px] leading-none font-black">{top}</span>
      {bottom && <span className="text-[9px] font-bold tracking-[1px]">{bottom}</span>}
    </div>
  )
}

const CHIP_BG = ['bg-mustard', 'bg-sage', 'bg-sky', 'bg-coral']
// color가 있으면 색 해시태그, 없으면 테두리 칩
export function Chip({ children, color }: { children: ReactNode; color?: number }) {
  return color == null ? (
    <span className="border-[1.5px] border-ink px-2.5 py-1 text-xs">{children}</span>
  ) : (
    <span className={`px-2.5 py-1 text-xs font-bold ${CHIP_BG[color % CHIP_BG.length]}`}>{children}</span>
  )
}

// 아바타 색 순환: mustard → sage → sky → coral → cream (본인은 cream 테두리)
const AVATAR_BG = ['bg-mustard', 'bg-sage', 'bg-sky', 'bg-coral', 'bg-cream']
export function Avatar({ label, index = 0, me, size = 40 }: { label: string; index?: number; me?: boolean; size?: number }) {
  return (
    <span
      style={{ width: size, height: size }}
      className={`flex shrink-0 items-center justify-center rounded-full text-xs font-bold text-ink ${AVATAR_BG[index % AVATAR_BG.length]} ${
        me ? 'border-2 border-cream' : ''
      }`}
    >
      {label.slice(0, 1)}
    </span>
  )
}

export function SectionHeader({ title, label }: { title: string; label?: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between border-b-[1.5px] border-ink pb-1.5">
      <h2 className="font-display text-[17px] italic">{title}</h2>
      {label && <span className="text-[10px] tracking-[1.5px] text-muted">{label}</span>}
    </div>
  )
}

const BTN = 'flex min-h-[52px] w-full items-center justify-center px-[18px] text-[15px] font-bold disabled:opacity-40'
type BtnProps = ButtonHTMLAttributes<HTMLButtonElement>
export function PrimaryButton({ className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`${BTN} bg-ink text-cream ${className}`} />
}
export function SecondaryButton({ className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`${BTN} border-[1.5px] border-ink bg-card text-ink ${className}`} />
}
export function LinkButton({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className={`${BTN} justify-between bg-ink text-cream`}>
      {children}
    </Link>
  )
}

export function ErrorText({ children }: { children: ReactNode }) {
  return <p className="text-sm font-medium text-accent">{children}</p>
}

// 티켓 카드: DATE / TIME / PLACE 3칸 + 하단 줄
export function TicketCard({
  startsAt,
  duration,
  place,
  placeSub,
  footer,
  raised,
}: {
  startsAt: string
  duration?: string
  place: string
  placeSub?: string
  footer?: ReactNode
  raised?: boolean
}) {
  const d = dateParts(startsAt)
  return (
    <section className={`border-[1.5px] border-ink bg-card ${raised ? 'shadow-print' : ''}`}>
      <div className="grid grid-cols-[1.1fr_1fr_1fr]">
        <Cell label="DATE" className="border-r-[1.5px] border-dashed border-ink">
          <Big>{d.md}</Big>
          <Sub>{d.weekday}</Sub>
        </Cell>
        <Cell label="TIME" className="border-r-[1.5px] border-dashed border-ink">
          <Big>{d.hm}</Big>
          {duration && <Sub>{duration}</Sub>}
        </Cell>
        <Cell label="PLACE">
          <div className="font-serif text-[22px] leading-tight font-black">{place}</div>
          {placeSub && <Sub>{placeSub}</Sub>}
        </Cell>
      </div>
      {footer && <div className="flex items-center justify-between gap-2 border-t-[1.5px] border-ink px-3.5 py-2.5 text-xs">{footer}</div>}
    </section>
  )
}
const Cell = ({ label, children, className = '' }: { label: string; children: ReactNode; className?: string }) => (
  <div className={`min-w-0 px-3.5 py-3 ${className}`}>
    <div className="text-[9px] tracking-[1.5px] text-muted">{label}</div>
    {children}
  </div>
)
const Big = ({ children }: { children: ReactNode }) => (
  <div className="font-display text-[26px] leading-[1.1] font-black">{children}</div>
)
const Sub = ({ children }: { children: ReactNode }) => <div className="truncate text-[11px] text-muted">{children}</div>

// 날짜 스탬프형 가로 티켓 (회차 목록·추천 일정). 선택(버튼)·링크 둘 다 가능.
export function SlotTicket({
  startsAt,
  title,
  sub,
  badge,
  selected,
  order,
  stampTone = 'accent',
  onClick,
  to,
  disabled,
}: {
  startsAt: string
  title: string
  sub?: ReactNode
  badge?: ReactNode
  selected?: boolean
  order?: number
  stampTone?: 'accent' | 'ink'
  onClick?: () => void
  to?: string
  disabled?: boolean
}) {
  const d = dateParts(startsAt)
  const inner = (
    <>
      <div
        className={`flex w-[76px] shrink-0 flex-col items-center justify-center py-2.5 text-cream ${
          stampTone === 'accent' ? 'bg-accent' : 'bg-ink'
        }`}
      >
        <span className="font-display text-[30px] leading-none font-black">{d.day}</span>
        <span className="text-[10px] tracking-[1.5px]">
          {d.month} {d.weekday}
        </span>
      </div>
      <div className="flex min-w-0 flex-1 items-center justify-between gap-2 border-l-[1.5px] border-dashed border-ink px-3.5 py-3 text-left">
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="font-serif text-base font-black">{title}</span>
          {sub && <span className="text-[11px] text-muted">{sub}</span>}
        </div>
        {order != null && order >= 0 ? (
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent text-sm font-bold text-cream">
            {order + 1}
          </span>
        ) : (
          badge
        )}
      </div>
    </>
  )
  const cls = `flex w-full min-h-[44px] border-[1.5px] border-ink bg-card text-ink ${selected ? 'shadow-print' : ''} ${
    disabled ? 'opacity-50' : ''
  }`
  if (to)
    return (
      <Link to={to} className={cls}>
        {inner}
      </Link>
    )
  if (!onClick) return <div className={cls}>{inner}</div>
  return (
    <button type="button" onClick={onClick} disabled={disabled} className={cls} aria-pressed={selected}>
      {inner}
    </button>
  )
}

const TABS = [
  { to: '/app', label: '홈', end: true },
  { to: '/app/slots', label: '일정' },
  { to: '/app/notifications', label: '알림' },
  { to: '/app/history', label: '기록' },
]
export function BottomNav() {
  return (
    <nav aria-label="하단 메뉴" className="sticky bottom-0 z-30 grid h-[58px] grid-cols-4 border-t-[1.5px] border-ink bg-card">
      {TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end}
          className={({ isActive }) =>
            `flex flex-col items-center justify-center gap-[3px] text-xs ${isActive ? 'font-bold text-ink' : 'text-muted'}`
          }
        >
          {({ isActive }) => (
            <>
              {isActive && <span className="h-[3px] w-[18px] bg-accent" />}
              {t.label}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}

// 뒤로가기가 있는 와인색 헤더 (오늘의 테이블 등)
export function BackHeader({ to, title, sub, children }: { to: string; title: string; sub?: string; children?: ReactNode }) {
  return (
    <header className="bg-accent px-3.5 pt-2 pb-3.5 text-cream">
      <div className="flex items-center gap-1.5">
        <Link to={to} aria-label="뒤로" className="flex h-11 w-11 items-center justify-center">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" />
          </svg>
        </Link>
        <div className="flex min-w-0 flex-col">
          <span className="font-serif text-lg font-black">{title}</span>
          {sub && <span className="truncate text-[11px] opacity-80">{sub}</span>}
        </div>
      </div>
      {children}
    </header>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 py-6 text-center text-sm text-muted">
      <Illustration name="emptyState" className="h-[115px] w-48" />
      {children}
    </div>
  )
}

// 히어로 없는 화면의 상단 제목
export function PageTitle({ kicker, title, right }: { kicker: string; title: string; right?: ReactNode }) {
  return (
    <header className="px-[22px] pt-4 pb-4">
      <div className="flex items-baseline justify-between">
        <span className="font-serif text-xl font-black tracking-[-0.5px]">우연한 식탁</span>
        {right}
      </div>
      <div className="mt-5 font-display text-[17px] text-accent italic">{kicker}</div>
      <h1 className="font-serif text-[28px] leading-tight font-black tracking-[-1px]">{title}</h1>
    </header>
  )
}

// 입력 공통 스타일
export const inputCls = 'min-h-[44px] w-full border-[1.5px] border-ink bg-card px-3 text-sm text-ink'
