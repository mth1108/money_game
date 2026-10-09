// 탭마다 따로 기억한다 (sessionStorage). 같은 PC 에서 탭 두 개로 두 사람을 테스트할 수 있게 하려는 것이다.
// 들어간 방도 기억해서 새로고침하면 그 방으로 돌아간다 — 서버가 현재 게임 상태를 다시 보내 준다 (§9-8).
// 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서도 화면은 동작해야 하므로 실패는 무시한다.
import type { User } from './api/types'

const USER_KEY = 'moneygame.user'
const ROOM_KEY = 'moneygame.roomId'

function read(key: string): string | null {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) sessionStorage.removeItem(key)
    else sessionStorage.setItem(key, value)
  } catch {
    // 저장하지 못해도 이번 탭에서는 계속 쓸 수 있다
  }
}

export function loadUser(): User | null {
  const raw = read(USER_KEY)
  return raw ? (JSON.parse(raw) as User) : null
}

export function saveUser(user: User | null): void {
  write(USER_KEY, user ? JSON.stringify(user) : null)
}

export function loadRoomId(): string | null {
  return read(ROOM_KEY)
}

export function saveRoomId(roomId: string | null): void {
  write(ROOM_KEY, roomId)
}

// ── 닉네임 토큰 (2026-10-09) ──
// 닉네임은 처음 만든 브라우저에 묶인다. 서버가 준 토큰을 닉네임별로 localStorage 에 둔다 —
// 탭과 상관없이 이 브라우저라면 같은 닉네임으로 다시 들어올 수 있다. 서버는 대소문자를 구분하지 않으므로 소문자로 찾는다.
// 지우면 그 닉네임은 다시 쓸 수 없다 (CLAUDE.md §9-14).

const TOKENS_KEY = 'moneygame.tokens'

function readTokens(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(TOKENS_KEY) ?? '{}') as Record<string, string>
  } catch {
    return {}
  }
}

const tokenKey = (nickname: string) => nickname.trim().toLowerCase()

export function loadToken(nickname: string): string | undefined {
  return readTokens()[tokenKey(nickname)]
}

export function saveToken(nickname: string, token: string): void {
  try {
    localStorage.setItem(TOKENS_KEY, JSON.stringify({ ...readTokens(), [tokenKey(nickname)]: token }))
  } catch {
    // 저장하지 못하면 이 닉네임은 이번에만 쓸 수 있다
  }
}
