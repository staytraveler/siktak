import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Bell, CalendarCheck, CalendarDays, ChevronLeft, ChevronRight, FileText, House, User, type LucideIcon } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import Illustration from './Illustration'
import DemoClock from '../DemoClock'
import PushWatcher from '../PushWatcher'
import { dateParts } from '../../lib/design'

export { default as Illustration } from './Illustration'

// 디자인 톤: 둥근 모서리 + 연한 테두리(border-line) + 옅은 그림자 (docs/design/design-guide.md)

// 앱 공통 틀: 390px 안팎 단일 열 + 하단 메뉴
export function AppShell({ children, nav = true }: { children: ReactNode; nav?: boolean }) {
  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-paper text-ink">
      <PushWatcher />
      <div className="flex-1 pb-6">{children}</div>
      {nav && <BottomNav />}
    </div>
  )
}

// 상단 헤더: 왼쪽 로고(세리프), 오른쪽 원형 프로필 버튼
export function AppHeader({ right }: { right?: ReactNode }) {
  return (
    <header className="flex items-center justify-between px-5 pt-4 pb-3">
      <Link to="/app" className="font-serif text-xl font-black tracking-[-0.5px]">
        우연한 식탁
      </Link>
      <DemoClock />
      {right ?? (
        <Link
          to="/app/profile"
          aria-label="내 프로필"
          className="flex h-10 w-10 items-center justify-center rounded-full border border-line bg-card text-ink"
        >
          <User size={20} strokeWidth={1.6} />
        </Link>
      )}
    </header>
  )
}

// 히어로 카드: 머스터드 배경 이미지(그림은 오른쪽) 위에 왼쪽 글자. 상태별로 라벨·제목·설명만 바뀐다.
export function HeroBlock({
  kicker,
  title,
  badge,
  children,
}: {
  kicker: string
  title: ReactNode
  badge?: ReactNode
  children?: ReactNode
}) {
  return (
    <section className="relative mx-4 overflow-hidden rounded-2xl bg-mustard text-ink">
      <Illustration name="homeHero" className="pointer-events-none absolute inset-0 h-full w-full object-cover object-right" />
      <div className="relative z-10 flex min-h-[200px] w-[68%] flex-col justify-center gap-1 px-5 py-5">
        <div className="flex items-center gap-2 text-[10px] font-medium tracking-[2.5px] uppercase">
          <span>{kicker}</span>
          <span className="h-px flex-1 bg-ink/30" />
        </div>
        <h1 className="mt-1 font-serif text-[25px] leading-[1.25] font-black tracking-[-1px]">{title}</h1>
        {children}
      </div>
      {badge && <div className="absolute top-3 right-3 z-20">{badge}</div>}
    </section>
  )
}

// 히어로 안 한 줄 설명 / 아이콘 붙은 날짜
export const HeroDesc = ({ children }: { children: ReactNode }) => <p className="mt-1 text-[13px] leading-snug">{children}</p>
export const HeroDate = ({ children }: { children: ReactNode }) => (
  <p className="mt-2 flex items-center gap-1.5 text-[13px]">
    <CalendarCheck size={16} strokeWidth={1.6} className="shrink-0" />
    <span>{children}</span>
  </p>
)

export function Sticker({ top, bottom }: { top: string; bottom?: string }) {
  return (
    <div className="flex h-[52px] w-[52px] flex-col items-center justify-center rounded-full bg-accent text-cream shadow-print">
      <span className="font-display text-[15px] leading-none font-black">{top}</span>
      {bottom && <span className="text-[9px] font-bold tracking-[1px]">{bottom}</span>}
    </div>
  )
}

const CHIP_BG = ['bg-mustard', 'bg-sage', 'bg-sky', 'bg-coral']
// color가 있으면 색 해시태그, 없으면 테두리 칩 (둘 다 pill)
export function Chip({ children, color }: { children: ReactNode; color?: number }) {
  return color == null ? (
    <span className="rounded-full border border-line bg-card px-3 py-1 text-xs">{children}</span>
  ) : (
    <span className={`rounded-full px-3 py-1 text-xs font-bold ${CHIP_BG[color % CHIP_BG.length]}`}>{children}</span>
  )
}

// 아이콘 달린 상태 pill. tone: wine(진행) / gold(대기) / sage(완료·확정) / plain(기타)
const PILL_TONE = {
  wine: 'bg-wine-soft text-accent',
  gold: 'bg-gold-soft text-[#8a5a00]',
  sage: 'bg-sage-soft text-[#3f5a2e]',
  plain: 'bg-card text-body border border-line',
}
export function Pill({ children, icon: Icon, tone = 'wine' }: { children: ReactNode; icon?: LucideIcon; tone?: keyof typeof PILL_TONE }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-medium whitespace-nowrap ${PILL_TONE[tone]}`}>
      {Icon && <Icon size={13} strokeWidth={2} />}
      {children}
    </span>
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

// 영문 이탤릭 제목 + 오른쪽 "전체 보기 →" 링크(또는 보조 글자) + 아래 얇은 선
export function SectionHeader({ title, label, to, linkText = '전체 보기 →' }: { title: string; label?: ReactNode; to?: string; linkText?: string }) {
  return (
    <div className="flex items-baseline justify-between border-b border-line pb-2">
      <h2 className="font-display text-[22px] italic">{title}</h2>
      {to ? (
        <Link to={to} className="flex min-h-[44px] items-center text-[13px] font-medium text-accent">
          {linkText}
        </Link>
      ) : (
        label && <span className="text-[11px] tracking-[1px] text-muted">{label}</span>
      )}
    </div>
  )
}

const BTN = 'flex min-h-[52px] w-full items-center justify-center rounded-xl px-[18px] text-[15px] font-bold disabled:opacity-40'
type BtnProps = ButtonHTMLAttributes<HTMLButtonElement>
export function PrimaryButton({ className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`${BTN} bg-ink text-cream ${className}`} />
}
export function SecondaryButton({ className = '', ...p }: BtnProps) {
  return <button type="button" {...p} className={`${BTN} border border-ink bg-card text-ink ${className}`} />
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

// 확정 티켓 카드: DATE / TIME / PLACE 3칸 + 하단 줄
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
    <section className={`overflow-hidden rounded-2xl border border-line bg-card ${raised ? 'shadow-print' : ''}`}>
      <div className="grid grid-cols-[1.1fr_1fr_1fr]">
        <Cell label="DATE" className="border-r border-dashed border-line">
          <Big>{d.md}</Big>
          <Sub>{d.weekday}</Sub>
        </Cell>
        <Cell label="TIME" className="border-r border-dashed border-line">
          <Big>{d.hm}</Big>
          {duration && <Sub>{duration}</Sub>}
        </Cell>
        <Cell label="PLACE">
          <div className="font-serif text-[22px] leading-tight font-black">{place}</div>
          {placeSub && <Sub>{placeSub}</Sub>}
        </Cell>
      </div>
      {footer && <div className="flex items-center justify-between gap-2 border-t border-line px-3.5 py-2.5 text-xs">{footer}</div>}
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

// 날짜 블록형 가로 카드 (회차 목록·내 신청·추천). 왼쪽 와인색 날짜 + 지역 + 설명 + 상태 pill + `>`.
// 선택(버튼)·링크 둘 다 가능.
export function SlotTicket({
  startsAt,
  title,
  sub,
  badge,
  pills,
  time,
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
  pills?: ReactNode // 제목 아래 상태 pill 줄
  time?: string // 식사 시작 시각(예: 19:30). 있으면 제목 위에 크게 표시
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
        className={`flex w-[76px] shrink-0 flex-col items-center justify-center py-3 text-cream ${
          stampTone === 'accent' ? 'bg-accent' : 'bg-ink'
        }`}
      >
        <span className="font-serif text-[30px] leading-none font-black">{d.day}</span>
        <span className="mt-1 text-[10px] tracking-[2px]">{d.month}</span>
        <span className="text-[10px] tracking-[2px]">{d.weekday}</span>
      </div>
      <div className="flex min-w-0 flex-1 items-center justify-between gap-2 border-l border-dashed border-line px-3.5 py-3 text-left">
        <div className="flex min-w-0 flex-col gap-1">
          {time && <span className="font-display text-[26px] leading-none font-black">{time}</span>}
          <span className="font-serif text-[17px] font-black">{title}</span>
          {sub && <span className="text-[12px] text-muted">{sub}</span>}
          {pills && <div className="mt-0.5 flex flex-wrap gap-1.5">{pills}</div>}
        </div>
        {order != null && order >= 0 ? (
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent text-sm font-bold text-cream">
            {'①②③④⑤'[order] ?? order + 1}
          </span>
        ) : (
          (badge ?? (to ? <ChevronRight size={20} className="shrink-0 text-ink" /> : null))
        )}
      </div>
    </>
  )
  const cls = `flex w-full min-h-[44px] overflow-hidden rounded-2xl border bg-card text-ink shadow-print ${
    selected ? 'border-accent ring-2 ring-accent' : 'border-line'
  } ${disabled ? 'opacity-50' : ''}`
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

const TABS: { to: string; label: string; icon: LucideIcon; end?: boolean }[] = [
  { to: '/app', label: '홈', icon: House, end: true },
  { to: '/app/slots', label: '일정', icon: CalendarDays },
  { to: '/app/notifications', label: '알림', icon: Bell },
  { to: '/app/history', label: '기록', icon: FileText },
]
export function BottomNav() {
  return (
    <nav aria-label="하단 메뉴" className="sticky bottom-0 z-30 grid h-[64px] grid-cols-4 border-t border-line bg-card">
      {TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end}
          className={({ isActive }) =>
            `flex flex-col items-center justify-center gap-0.5 text-[11px] ${isActive ? 'font-bold text-accent' : 'text-muted'}`
          }
        >
          {({ isActive }) => (
            <>
              <t.icon size={22} strokeWidth={1.6} />
              {t.label}
              <span className={`h-1 w-1 rounded-full ${isActive ? 'bg-accent' : 'bg-transparent'}`} />
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
    <header className="rounded-b-2xl bg-accent px-3.5 pt-2 pb-3.5 text-cream">
      <div className="flex items-center gap-1.5">
        <Link to={to} aria-label="뒤로" className="flex h-11 w-11 items-center justify-center">
          <ChevronLeft size={22} strokeWidth={1.8} />
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

// 히어로 없는 화면의 상단: 공통 헤더 + 영문 라벨 + 제목
export function PageTitle({ kicker, title, right }: { kicker: string; title: string; right?: ReactNode }) {
  return (
    <>
      <AppHeader />
      <div className="px-5 pt-2 pb-4">
        <div className="flex items-center justify-between">
          <div className="font-display text-[17px] text-accent italic">{kicker}</div>
          {right}
        </div>
        <h1 className="font-serif text-[26px] leading-tight font-black tracking-[-1px]">{title}</h1>
      </div>
    </>
  )
}

// 입력 공통 스타일
export const inputCls = 'min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm text-ink'
