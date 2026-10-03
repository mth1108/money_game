// 방 목록 / 만들기 / 입장 (CLAUDE.md §3 M6).
// 방 만들기 선택지(모드별 배율, 판 길이, 인원·봇 한도, 기본 시드)는 서버가 내려 준다 — 화면에 하드코딩하지 않는다.
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { GameMode, HistoryEntry, RoomOptions, RoomView, User } from '../api/types'
import { ResultView } from '../components/ResultView'

export function Lobby({ user, onEnterRoom }: { user: User; onEnterRoom: (roomId: string) => void }) {
  const [options, setOptions] = useState<RoomOptions | null>(null)
  const [rooms, setRooms] = useState<RoomView[]>([])
  const [mode, setMode] = useState<GameMode>('DAILY')
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [ticks, setTicks] = useState<number | null>(null)
  const [scenarioId, setScenarioId] = useState('')
  const [seedMoney, setSeedMoney] = useState('')
  const [bots, setBots] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [openResult, setOpenResult] = useState<number | null>(null)

  // 방 만들기 선택지와 내 전적 (M8). 로비에 들어올 때 한 번 읽는다
  useEffect(() => {
    api
      .roomOptions()
      .then((o) => {
        setOptions(o)
        setTicks(o.defaultTicks)
        setMaxPlayers(o.defaultMaxPlayers)
        setSeedMoney(o.defaultSeedMoney)
      })
      .catch((e: Error) => setError(e.message))
    api
      .history(user.id)
      .then(setHistory)
      .catch((e: Error) => setError(e.message))
  }, [user.id])

  // 대기 중인 방 목록. 2초마다 새로 읽는다
  useEffect(() => {
    let alive = true
    const load = () =>
      api
        .listRooms()
        .then((r) => alive && setRooms(r))
        .catch((e: Error) => alive && setError(e.message))
    load()
    const timer = setInterval(load, 2000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [])

  async function create(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const id = scenarioId.trim() === '' ? undefined : Number(scenarioId)
      const seed = seedMoney.trim() === '' ? undefined : seedMoney.trim()
      const room = await api.createRoom(user.id, mode, maxPlayers, id, seed, bots, ticks ?? undefined)
      onEnterRoom(room.id)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const seconds = (t: number) => (options ? Math.round((t * options.tickMillis) / 1000) : t)

  return (
    <div>
      <h2>방 만들기</h2>
      {options === null ? (
        <p>선택지 읽는 중…</p>
      ) : (
        <form onSubmit={create}>
          <label>
            모드{' '}
            <select value={mode} onChange={(e) => setMode(e.target.value as GameMode)}>
              {options.modes.map((m) => (
                <option key={m.mode} value={m.mode}>
                  {m.mode} (배율 {m.leverages.join('·')})
                </option>
              ))}
            </select>
          </label>{' '}
          <label>
            판 길이{' '}
            <select value={ticks ?? options.defaultTicks} onChange={(e) => setTicks(Number(e.target.value))}>
              {options.allowedTicks.map((t) => (
                <option key={t} value={t}>
                  {t}틱 ({seconds(t)}초)
                </option>
              ))}
            </select>
          </label>{' '}
          <label>
            인원{' '}
            <input type="number" min={1} max={options.maxPlayers} value={maxPlayers} onChange={(e) => setMaxPlayers(Number(e.target.value))} />
          </label>{' '}
          <label>
            존버 봇 (인원에 포함){' '}
            <select value={bots} onChange={(e) => setBots(Number(e.target.value))}>
              {Array.from({ length: options.maxBots + 1 }, (_, n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>{' '}
          <label>
            시나리오 ID (비우면 무작위) <input value={scenarioId} onChange={(e) => setScenarioId(e.target.value)} size={4} />
          </label>{' '}
          <label>
            시드머니 <input value={seedMoney} onChange={(e) => setSeedMoney(e.target.value)} inputMode="decimal" size={12} />
          </label>{' '}
          <button type="submit">만들기</button>
        </form>
      )}
      {error && <p>오류: {error}</p>}

      <h2>대기 중인 방 ({rooms.length})</h2>
      {rooms.length === 0 && <p>없음</p>}
      <ul>
        {rooms.map((r) => (
          <li key={r.id}>
            {r.id} · {r.mode} · {r.totalTicks}틱 · {r.participants.length}/{r.maxPlayers}명 · 방장{' '}
            {r.participants.find((p) => p.userId === r.hostUserId)?.nickname}
            {' · '}
            {r.participants.map((p) => `${p.nickname}${p.bot ? '(봇)' : p.ready ? '(준비)' : ''}`).join(', ')}{' '}
            <button onClick={() => onEnterRoom(r.id)}>입장</button>
          </li>
        ))}
      </ul>

      <h2>내 전적 ({history.length})</h2>
      {history.length === 0 && <p>없음</p>}
      <ul>
        {history.map((h) => (
          <li key={h.gameId}>
            #{h.gameId} · {h.finishedAt} · {h.mode} · {h.totalTicks}틱 · 「{h.scenarioTitle}」 · {h.rank}위/{h.playerCount}명 · 최종 자산{' '}
            {h.finalAsset} · 수익률 {h.returnRate} (비율) · 청산 {h.liquidatedCount}회{' '}
            <button onClick={() => setOpenResult(openResult === h.gameId ? null : h.gameId)}>
              {openResult === h.gameId ? '닫기' : '보기'}
            </button>
            {openResult === h.gameId && <ResultView resultId={h.gameId} myUserId={user.id} />}
          </li>
        ))}
      </ul>
    </div>
  )
}
