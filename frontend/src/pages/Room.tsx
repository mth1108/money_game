// 방 화면 — 대기실과 게임 (CLAUDE.md §3 M6·M7). WebSocket 으로 JOIN 한 뒤 메시지로만 화면을 갱신한다.
// 연결이 끊기면 잠시 뒤 다시 붙어 JOIN 한다 — 서버가 지금까지의 판을 다시 보내 준다 (§9-8, GAME_START.playedBars).
import { useEffect, useReducer, useRef, useState } from 'react'
import { api } from '../api/client'
import type { User } from '../api/types'
import { FinalRanking } from '../components/GameSide'
import { Button, ErrorText, Panel, Tag } from '../components/ui'
import { connectGame, type GameSocket, type SocketStatus } from '../socket/gameSocket'
import { gameReducer, initialGameState, type GameState } from '../socket/gameReducer'
import type { ClientMessage } from '../socket/messages'
import { Game } from './Game'
import { MODE_TEXT } from './Lobby'

const RECONNECT_MILLIS = 2000

function Waiting({
  state,
  user,
  onReady,
}: {
  state: GameState
  user: User
  onReady: (ready: boolean) => void
}) {
  const room = state.room
  if (!room) return <p className="py-10 text-center text-sm text-faint">방에 들어가는 중…</p>
  const me = room.participants.find((p) => p.userId === String(user.id))
  const empty = Math.max(0, room.maxPlayers - room.participants.length)
  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Panel
        title={
          <span className="flex items-center gap-2">
            대기실 <Tag tone={room.mode === 'DAILY' ? 'brand' : 'ok'}>{MODE_TEXT[room.mode]}</Tag>
            <Tag>{room.totalTicks}틱</Tag>
          </span>
        }
        right={`방 코드 ${room.id}`}
      >
        <ul className="grid gap-2 sm:grid-cols-2">
          {room.participants.map((p) => (
            <li
              key={p.userId}
              className={`flex items-center gap-3 rounded-xl border p-3 ${p.userId === String(user.id) ? 'border-brand/50 bg-brand/5' : 'border-line bg-bg'}`}
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-panel-2 font-black text-muted">
                {p.nickname.slice(0, 1)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold">{p.nickname}</span>
                <span className="mt-0.5 flex gap-1">
                  {p.userId === room.hostUserId && <Tag tone="warn">방장</Tag>}
                  {p.bot && <Tag>존버 봇</Tag>}
                  {p.userId === String(user.id) && <Tag tone="brand">나</Tag>}
                </span>
              </span>
              <span className={`text-sm font-bold ${p.ready ? 'text-ok' : 'text-faint'}`}>{p.ready ? '준비' : '대기'}</span>
            </li>
          ))}
          {Array.from({ length: empty }, (_, i) => (
            <li key={`empty-${i}`} className="flex items-center justify-center rounded-xl border border-dashed border-line p-3 text-sm text-faint">
              빈 자리
            </li>
          ))}
        </ul>
      </Panel>
      <div className="flex flex-col items-center gap-2">
        <Button variant={me?.ready ? 'ghost' : 'primary'} size="lg" className="w-full max-w-sm" onClick={() => onReady(!me?.ready)}>
          {me?.ready ? '준비 취소' : '준비 완료'}
        </Button>
        <p className="text-sm text-muted">전원이 준비하면 바로 시작합니다. 다른 사람은 로비의 방 목록에서 들어옵니다.</p>
      </div>
    </div>
  )
}

function GameOver({
  state,
  user,
  onResult,
  onLobby,
  onClose,
}: {
  state: GameState
  user: User
  onResult: (id: number) => void
  onLobby: () => void
  onClose: () => void
}) {
  const end = state.end!
  const mine = end.rankings.find((r) => r.userId === String(user.id))
  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border border-line bg-panel p-6 shadow-2xl">
        <p className="text-sm font-semibold text-muted">게임 종료</p>
        <h2 className="mt-1 text-2xl font-black">
          {mine ? (
            <>
              {mine.rank}위 <span className="text-base font-semibold text-muted">/ {end.rankings.length}명</span>
            </>
          ) : (
            '최종 순위'
          )}
        </h2>
        <div className="mt-4 max-h-[50vh] overflow-y-auto">
          <FinalRanking rankings={end.rankings} myUserId={String(user.id)} />
        </div>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <Button variant="primary" disabled={end.resultId === null} onClick={() => end.resultId !== null && onResult(end.resultId)}>
            결과 · 종목 공개
          </Button>
          <Button onClick={onLobby}>로비로</Button>
        </div>
        {end.resultId === null && <p className="mt-2 text-xs text-up">결과를 저장하지 못해 상세 결과가 없습니다.</p>}
        <button className="mt-3 w-full text-xs text-faint hover:text-ink" onClick={onClose}>
          차트 다시 보기
        </button>
      </div>
    </div>
  )
}

export function Room({
  user,
  roomId,
  onExit,
  onOpenResult,
}: {
  user: User
  roomId: string
  onExit: () => void
  onOpenResult: (resultId: number) => void
}) {
  const [state, dispatch] = useReducer(gameReducer, String(user.id), initialGameState)
  const [status, setStatus] = useState<SocketStatus>('연결 중')
  const [attempt, setAttempt] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [hideOver, setHideOver] = useState(false)
  const socket = useRef<GameSocket | null>(null)
  const gone = useRef(false)
  gone.current = state.gone !== null

  useEffect(() => {
    let closing = false
    let retry: ReturnType<typeof setTimeout> | undefined
    const s = connectGame({
      onOpen: () => s.send({ type: 'JOIN', roomId, userId: user.id }),
      onMessage: (msg) => dispatch({ type: 'MESSAGE', msg }),
      onStatus: (st) => {
        if (closing) return // 우리가 닫은 옛 연결이다. 새 연결의 상태를 덮지 않는다
        setStatus(st)
        if (st === '끊김' && !closing && !gone.current) retry = setTimeout(() => setAttempt((a) => a + 1), RECONNECT_MILLIS)
      },
    })
    socket.current = s
    return () => {
      closing = true
      clearTimeout(retry)
      s.close()
    }
  }, [roomId, user.id, attempt])

  const send = (msg: ClientMessage) => {
    socket.current?.send(msg)
    if (msg.type === 'ORDER') dispatch({ type: 'SENT', action: msg.action })
  }

  const room = state.room
  const playing = room?.status === 'PLAYING' || room?.status === 'FINISHED' || state.game !== null

  async function exit() {
    setError(null)
    if (room?.status === 'PLAYING' && !window.confirm('진행 중에 나가도 보유 포지션은 판이 끝날 때 정리됩니다. 나갈까요?')) return
    try {
      // 대기 중에만 방에서 빠진다. 진행 중이면 연결만 끊는다
      if (room?.status === 'WAITING' && !state.gone) await api.leave(roomId, user.id)
      onExit()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Button size="sm" onClick={exit}>
          ← {room?.status === 'WAITING' ? '방 나가기' : '로비로'}
        </Button>
        <span className={`text-xs ${status === '연결됨' ? 'text-faint' : 'text-warn'}`}>
          {status === '연결됨' ? '실시간 연결됨' : status === '연결 중' ? '연결 중…' : `연결 끊김 — ${RECONNECT_MILLIS / 1000}초 뒤 다시 연결합니다`}
        </span>
      </div>
      <ErrorText>{error}</ErrorText>
      {state.gone ? (
        <Panel>
          <div className="flex flex-col items-center gap-3 py-6 text-center">
            <p className="text-muted">{state.gone}</p>
            <Button variant="primary" onClick={onExit}>
              로비로
            </Button>
          </div>
        </Panel>
      ) : playing && state.game ? (
        <Game state={state} user={user} send={send} />
      ) : (
        <Waiting state={state} user={user} onReady={(ready) => send({ type: 'READY', ready })} />
      )}
      {state.end && !hideOver && (
        <GameOver state={state} user={user} onResult={onOpenResult} onLobby={onExit} onClose={() => setHideOver(true)} />
      )}
    </div>
  )
}
