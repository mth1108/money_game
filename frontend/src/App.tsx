// P1 화면 (CLAUDE.md §4). 입장 → 로비(방 목록·만들기·전적) → 방(대기실·게임) → 결과.
// 라우터 없이 상태로 화면을 바꾼다. 들어간 방은 탭에 기억해 새로고침하면 돌아간다 (session.ts).
import { useState } from 'react'
import type { User } from './api/types'
import './index.css'
import { Enter } from './pages/Enter'
import { Lobby } from './pages/Lobby'
import { Result } from './pages/Result'
import { Room } from './pages/Room'
import { loadRoomId, loadUser, saveRoomId, saveUser } from './session'

type View = { kind: 'lobby' } | { kind: 'room'; roomId: string } | { kind: 'result'; resultId: number; back: View }

export default function App() {
  const [user, setUser] = useState<User | null>(loadUser)
  const [view, setView] = useState<View>(() => {
    const roomId = loadRoomId()
    return roomId ? { kind: 'room', roomId } : { kind: 'lobby' }
  })

  function go(next: View) {
    saveRoomId(next.kind === 'room' ? next.roomId : null)
    setView(next)
    window.scrollTo(0, 0)
  }

  function enter(u: User | null) {
    saveUser(u)
    setUser(u)
    go({ kind: 'lobby' })
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-20 border-b border-line bg-bg/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-[1400px] items-center justify-between gap-3 px-4">
          <button className="flex items-center gap-2" onClick={() => user && view.kind !== 'room' && go({ kind: 'lobby' })}>
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand text-sm font-black text-white">₩</span>
            <span className="text-base font-black tracking-tight">머니게임</span>
          </button>
          {user && (
            <div className="flex items-center gap-3 text-sm">
              <span className="text-muted">
                <span className="font-semibold text-ink">{user.nickname}</span> 님
              </span>
              {view.kind === 'lobby' && (
                <button className="text-xs text-faint hover:text-ink" onClick={() => enter(null)}>
                  닉네임 바꾸기
                </button>
              )}
            </div>
          )}
        </div>
      </header>

      <main className="mx-auto max-w-[1400px] px-4 py-5">
        {!user ? (
          <Enter onEnter={enter} />
        ) : view.kind === 'lobby' ? (
          <Lobby
            user={user}
            onEnterRoom={(roomId) => go({ kind: 'room', roomId })}
            onOpenResult={(resultId) => go({ kind: 'result', resultId, back: { kind: 'lobby' } })}
          />
        ) : view.kind === 'room' ? (
          <Room
            user={user}
            roomId={view.roomId}
            onExit={() => go({ kind: 'lobby' })}
            onOpenResult={(resultId) => go({ kind: 'result', resultId, back: { kind: 'lobby' } })}
          />
        ) : (
          <Result resultId={view.resultId} user={user} onBack={() => go(view.back)} />
        )}
      </main>
    </div>
  )
}
