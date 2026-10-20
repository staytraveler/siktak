import { Link } from 'react-router-dom'

// 시연 가이드에서 화면을 열고 온 경우에만, 가이드로 돌아가는 작은 알약을 화면 아래에 띄운다.
export default function GuidePill() {
  let active = false
  try {
    active = localStorage.getItem('siktak.guide.active') === '1'
  } catch {
    // 저장소를 못 쓰면 알약을 띄우지 않는다
  }
  if (!active) return null
  return (
    <Link
      to="/guide"
      className="fixed right-3 bottom-[76px] z-40 rounded-full border border-accent bg-gold-soft px-3.5 py-2 text-xs font-black text-accent shadow-lg"
    >
      📋 시연 가이드로 돌아가기
    </Link>
  )
}
