// [임시 — 2.3] 로그인 대신 화면에서 고른 테스트 계정을 X-User-Id 헤더로 보낸다. 3.1에서 JWT로 교체.
export type Area = 'app' | 'admin'

const KEY: Record<Area, string> = {
  app: 'siktak.participantId',
  admin: 'siktak.operatorId',
}

export function areaOfPath(pathname: string): Area {
  return pathname.startsWith('/admin') ? 'admin' : 'app'
}

export function getUserId(area: Area): string | null {
  try {
    return localStorage.getItem(KEY[area])
  } catch {
    return null
  }
}

export function setUserId(area: Area, id: string) {
  try {
    localStorage.setItem(KEY[area], id)
  } catch {
    // 저장 불가 환경(시크릿 창 등)에서는 무시
  }
}
