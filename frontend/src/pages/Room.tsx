// 방 화면 — 대기실과 게임 (CLAUDE.md §4 P0). WebSocket 으로 JOIN 한 뒤 메시지로만 화면을 갱신한다.
import { useEffect, useReducer, useRef, useState } from 'react'
import { api } from '../api/client'
import type { User } from '../api/types'
import { GameEnd } from '../components/GameEnd'
import { GamePanel } from '../components/GamePanel'
import { OrderForm } from '../components/OrderForm'
import { Ranking } from '../components/Ranking'
import { connectGame, type GameSocket, type SocketStatus } from '../socket/gameSocket'
import type { ClientMessage } from '../socket/messages'
import { initialRoomState, roomReducer } from '../socket/roomReducer'

export function Room({ user, roomId, onExit }: { user: User; roomId: string; onExit: () => void }) {
  const [state, dispatch] = useReducer(roomReducer, initialRoomState)
  const [status, setStatus] = useState<SocketStatus>('연결 중')
  const [error, setError] = useState<string | null>(null)
  const socket = useRef<GameSocket | null>(null)

  useEffect(() => {
    const s = connectGame({
      onOpen: () => s.send({ type: 'JOIN', roomId, userId: user.id }),
      onMessage: (msg, raw) => dispatch({ msg, raw }),
      onStatus: setStatus,
    })
    socket.current = s
    return () => s.close()
  }, [roomId, user.id])

  const send = (msg: ClientMessage) => socket.current?.send(msg)
  const room = state.room
  const me = room?.participants.find((p) => p.userId === String(user.id))

  async function exit() {
    setError(null)
    try {
      // 대기 중에만 나갈 수 있다. 진행 중이면 연결만 끊는다 — 남은 포지션은 종료 시 정리된다
      if (room?.status === 'WAITING' && !state.gone) await api.leave(roomId, user.id)
      onExit()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div>
      <h2>방 {roomId}</h2>
      <p>
        연결: {status} · 상태: {room?.status ?? '-'} · 모드: {room?.mode ?? '-'} · 인원 {room?.participants.length ?? 0}/
        {room?.maxPlayers ?? '-'} <button onClick={exit}>나가기</button>
      </p>
      {error && <p>오류: {error}</p>}
      {state.gone && (
        <p>
          {state.gone} <button onClick={onExit}>로비로</button>
        </p>
      )}

      <h3>참가자</h3>
      <ul>
        {room?.participants.map((p) => (
          <li key={p.userId}>
            {p.nickname} (userId {p.userId}){p.userId === room.hostUserId ? ' · 방장' : ''} · {p.ready ? '준비됨' : '대기'}
            {p.userId === String(user.id) ? ' · 나' : ''}
          </li>
        ))}
      </ul>
      {room?.status === 'WAITING' && (
        <p>
          <button onClick={() => send({ type: 'READY', ready: !me?.ready })}>{me?.ready ? '준비 취소' : '준비'}</button>{' '}
          전원이 준비하면 바로 시작합니다
        </p>
      )}

      {state.game && (
        <>
          {state.end && <GameEnd end={state.end} myUserId={user.id} />}
          <GamePanel game={state.game} tick={state.tick} me={state.me} />
          {room?.status === 'PLAYING' && <OrderForm game={state.game} lastOrder={state.lastOrder} onSend={send} />}
          <Ranking ranking={state.ranking} tickIndex={state.rankingTick} myUserId={String(user.id)} />
        </>
      )}

      <h3>알림</h3>
      {state.events.length === 0 ? <p>없음</p> : <ul>{state.events.map((e, i) => <li key={i}>{e}</li>)}</ul>}

      <h3>받은 메시지 (최근 {state.raw.length}개, 원본 JSON)</h3>
      <pre>{state.raw.join('\n')}</pre>
    </div>
  )
}
