import { useState } from 'react'

const SEEN_KEY = 'siktak.splash.seen'

function shouldShow(): boolean {
  // 운영자 웹(/admin)에는 띄우지 않는다. 세션당 한 번만 보여준다
  if (location.pathname.startsWith('/admin')) return false
  try {
    return sessionStorage.getItem(SEEN_KEY) !== '1'
  } catch {
    return true
  }
}

// 앱 첫 진입 스플래시: 이미지가 페이드인했다가 페이드아웃한 뒤 사라진다.
export default function SplashScreen() {
  const [visible, setVisible] = useState(shouldShow)
  if (!visible) return null

  return (
    <div
      className="splash-fade fixed inset-0 z-[100] flex justify-center bg-[#1b1510]"
      aria-hidden="true"
      onAnimationEnd={() => {
        try {
          sessionStorage.setItem(SEEN_KEY, '1')
        } catch {
          // 저장 실패해도 이번 화면만 닫으면 된다
        }
        setVisible(false)
      }}
    >
      <img src="/illustrations/splash.jpg" alt="" className="h-full w-full max-w-[520px] object-cover" />
    </div>
  )
}
