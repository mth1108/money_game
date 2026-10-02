// 탭마다 따로 기억한다 (sessionStorage). 같은 PC 에서 탭 두 개로 두 사람을 테스트할 수 있게 하려는 것이다.
// 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서도 화면은 동작해야 하므로 실패는 무시한다.
import type { User } from './api/types'

const KEY = 'moneygame.user'

export function loadUser(): User | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function saveUser(user: User | null): void {
  try {
    if (user) sessionStorage.setItem(KEY, JSON.stringify(user))
    else sessionStorage.removeItem(KEY)
  } catch {
    // 저장하지 못해도 이번 탭에서는 계속 쓸 수 있다
  }
}
