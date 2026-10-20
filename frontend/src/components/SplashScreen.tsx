import { useState } from 'react'

// 페이지를 새로 불러올 때(첫 접속·새로고침)마다 한 번 보여준다. 앱 안에서 화면을 옮길 때는 다시 뜨지 않는다.
function shouldShow(): boolean {
  // 운영자 웹(/admin)에는 띄우지 않는다
  return !location.pathname.startsWith('/admin')
}

// 앱 진입 스플래시: 이미지가 페이드인했다가 페이드아웃한 뒤 사라진다.
export default function SplashScreen() {
  const [visible, setVisible] = useState(shouldShow)
  if (!visible) return null

  return (
    <div
      className="splash-fade fixed inset-0 z-[100] flex justify-center bg-[#1b1510]"
      aria-hidden="true"
      onAnimationEnd={() => setVisible(false)}
    >
      <img src="/illustrations/splash.jpg" alt="" className="h-full w-full max-w-[520px] object-cover" />
      <p className="absolute top-5 left-0 right-0 text-center text-xs tracking-[2px] text-white/80 [text-shadow:0_1px_4px_rgba(0,0,0,0.8)]">
        제작자 : 김용현
      </p>
    </div>
  )
}
