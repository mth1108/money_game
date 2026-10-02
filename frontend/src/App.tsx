// P0 테스트 하네스 (CLAUDE.md §4). 백엔드가 제대로 도는지 눈으로 확인하는 것, 그 이상 아무것도 아니다.
// 금지: 차트 라이브러리, CSS 프레임워크, 애니메이션, 반응형, 스타일링 자체.
import { useEffect, useState } from 'react'
import { api } from './api/client'
import type { User } from './api/types'
import { Enter } from './pages/Enter'
import { loadUser, saveUser } from './session'

type ServerStatus = '확인 중' | '연결됨' | '끊김'

export default function App() {
  const [user, setUser] = useState<User | null>(loadUser)
  const [server, setServer] = useState<ServerStatus>('확인 중')

  // 서버 연결 표시. 5초마다 방 목록을 불러 본다
  useEffect(() => {
    let alive = true
    const ping = () =>
      api
        .listRooms()
        .then(() => alive && setServer('연결됨'))
        .catch(() => alive && setServer('끊김'))
    ping()
    const timer = setInterval(ping, 5000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [])

  function enter(u: User | null) {
    saveUser(u)
    setUser(u)
  }

  return (
    <div>
      <h1>머니게임 P0 테스트 하네스</h1>
      <p>서버: {server}</p>
      {user ? (
        <p>
          입장: {user.nickname} (userId {user.id}) <button onClick={() => enter(null)}>닉네임 바꾸기</button>
        </p>
      ) : (
        <Enter onEnter={enter} />
      )}
    </div>
  )
}
