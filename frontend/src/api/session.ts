// 로그인 세션 (3.1). Access 토큰은 localStorage에 두고 axios 인터셉터가 Authorization 헤더로 붙인다.
export type Role = 'PARTICIPANT' | 'OPERATOR'

export interface SessionUser {
  id: number
  loginId: string
  name: string
  role: Role
}

export interface Session {
  accessToken: string
  expiresAt: string
  user: SessionUser
}

const KEY = 'siktak.session'

export function getSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function setSession(session: Session) {
  try {
    localStorage.setItem(KEY, JSON.stringify(session))
  } catch {
    // 저장 불가 환경(시크릿 창 등)에서는 이번 탭에서만 유지되지 않는다
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}

export function homeOf(role: Role) {
  return role === 'OPERATOR' ? '/admin' : '/app'
}
