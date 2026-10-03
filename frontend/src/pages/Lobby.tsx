// 로비 — 방 만들기 / 대기 중인 방 / 내 전적 (CLAUDE.md §3 M6, M8).
// 방 만들기 선택지(모드별 배율, 판 길이, 인원·봇 한도, 기본 시드)는 서버가 내려 준다 — 화면에 하드코딩하지 않는다.
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { GameMode, HistoryEntry, RoomOptions, RoomView, User } from '../api/types'
import { Button, ErrorText, Field, Panel, Segmented, Tag, inputClass } from '../components/ui'
import { digitsOnly, ratioPct, toneOf, won } from '../format'

export const MODE_TEXT: Record<GameMode, string> = { DAILY: '일봉', MINUTE: '분봉' }
const MODE_HINT: Record<GameMode, string> = {
  DAILY: '1년치 일봉을 몇 분에 — 큰 추세와 청산',
  MINUTE: '하루치 분봉을 몇 분에 — 잔파도와 높은 배율',
}

function seconds(ticks: number, tickMillis: number) {
  const s = Math.round((ticks * tickMillis) / 1000)
  return s % 60 === 0 ? `${s / 60}분` : `${s}초`
}

function CreateRoom({ user, options, onCreated }: { user: User; options: RoomOptions; onCreated: (roomId: string) => void }) {
  const [mode, setMode] = useState<GameMode>(options.modes[0]?.mode ?? 'DAILY')
  const [ticks, setTicks] = useState(options.defaultTicks)
  const [maxPlayers, setMaxPlayers] = useState(options.defaultMaxPlayers)
  const [bots, setBots] = useState(0)
  const [scenarioId, setScenarioId] = useState('')
  const [seedMoney, setSeedMoney] = useState(options.defaultSeedMoney)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const leverages = options.modes.find((m) => m.mode === mode)?.leverages ?? []
  const botLimit = Math.min(options.maxBots, maxPlayers - 1)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const id = scenarioId.trim() === '' ? undefined : Number(scenarioId)
      const seed = seedMoney.trim() === '' ? undefined : seedMoney.trim()
      const room = await api.createRoom(user.id, mode, maxPlayers, id, seed, Math.min(bots, botLimit), ticks)
      onCreated(room.id)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-5">
      <div>
        <div className="mb-1.5 text-xs font-medium text-muted">모드</div>
        <div className="grid grid-cols-2 gap-2">
          {options.modes.map((m) => (
            <button
              type="button"
              key={m.mode}
              onClick={() => setMode(m.mode)}
              className={`rounded-xl border p-3 text-left transition ${
                mode === m.mode ? 'border-brand bg-brand/10' : 'border-line bg-bg hover:border-faint'
              }`}
            >
              <div className="font-bold">{MODE_TEXT[m.mode]}</div>
              <div className="mt-0.5 text-xs text-muted">{MODE_HINT[m.mode]}</div>
              <div className="mt-2 text-xs text-faint">배율 {m.leverages.map((l) => `×${l}`).join(' ')}</div>
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <div className="mb-1.5 text-xs font-medium text-muted">판 길이</div>
          <Segmented
            value={ticks}
            onChange={setTicks}
            options={options.allowedTicks.map((t) => ({ value: t, label: seconds(t, options.tickMillis) }))}
          />
        </div>
        <div>
          <div className="mb-1.5 text-xs font-medium text-muted">인원 (봇 포함)</div>
          <div className="inline-flex items-center gap-1 rounded-lg bg-bg p-1">
            <Button type="button" size="sm" variant="subtle" onClick={() => setMaxPlayers(Math.max(1, maxPlayers - 1))}>
              −
            </Button>
            <span className="w-10 text-center font-bold">{maxPlayers}</span>
            <Button
              type="button"
              size="sm"
              variant="subtle"
              onClick={() => setMaxPlayers(Math.min(options.maxPlayers, maxPlayers + 1))}
            >
              +
            </Button>
          </div>
        </div>
      </div>

      <div>
        <div className="mb-1.5 text-xs font-medium text-muted">존버 봇 — 0틱에 종목마다 똑같이 나눠 사고 끝까지 보유 (배율 1)</div>
        <Segmented
          value={Math.min(bots, botLimit)}
          onChange={setBots}
          options={Array.from({ length: botLimit + 1 }, (_, n) => ({ value: n, label: n === 0 ? '없음' : `${n}개` }))}
        />
      </div>

      <details className="group rounded-lg border border-line bg-bg px-3 py-2">
        <summary className="cursor-pointer text-xs font-medium text-muted select-none">고급 설정</summary>
        <div className="mt-3 grid gap-3 pb-1 sm:grid-cols-2">
          <Field label="시나리오 ID" hint="비우면 같은 모드에서 무작위">
            <input className={inputClass} value={scenarioId} onChange={(e) => setScenarioId(digitsOnly(e.target.value))} placeholder="무작위" />
          </Field>
          <Field label="시드머니 (원)">
            <input className={inputClass} value={seedMoney ? won(seedMoney) : ''} onChange={(e) => setSeedMoney(digitsOnly(e.target.value))} inputMode="numeric" />
          </Field>
        </div>
      </details>

      <ErrorText>{error}</ErrorText>
      <Button type="submit" variant="primary" size="lg" className="w-full" disabled={busy}>
        {MODE_TEXT[mode]} 방 만들기 · {seconds(ticks, options.tickMillis)} · 배율 최대 ×{Math.max(...leverages)}
      </Button>
    </form>
  )
}

function RoomList({ rooms, onEnter }: { rooms: RoomView[]; onEnter: (roomId: string) => void }) {
  if (rooms.length === 0) {
    return <p className="py-6 text-center text-sm text-faint">대기 중인 방이 없습니다. 하나 만들어 보세요.</p>
  }
  return (
    <ul className="space-y-2">
      {rooms.map((r) => {
        const host = r.participants.find((p) => p.userId === r.hostUserId)
        const full = r.participants.length >= r.maxPlayers
        return (
          <li key={r.id} className="flex items-center gap-3 rounded-xl border border-line bg-bg p-3">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <Tag tone={r.mode === 'DAILY' ? 'brand' : 'ok'}>{MODE_TEXT[r.mode]}</Tag>
                <span className="truncate font-semibold">{host?.nickname ?? '?'} 님의 방</span>
              </div>
              <div className="mt-1 truncate text-xs text-muted">
                {r.totalTicks}틱 · {r.participants.map((p) => p.nickname + (p.bot ? '(봇)' : p.ready ? '✓' : '')).join(', ')}
              </div>
            </div>
            <span className="text-sm font-semibold text-muted">
              {r.participants.length}/{r.maxPlayers}
            </span>
            <Button variant="primary" size="sm" disabled={full} onClick={() => onEnter(r.id)}>
              {full ? '가득 참' : '입장'}
            </Button>
          </li>
        )
      })}
    </ul>
  )
}

function History({ history, onOpen }: { history: HistoryEntry[]; onOpen: (gameId: number) => void }) {
  if (history.length === 0) return <p className="py-4 text-sm text-faint">아직 끝낸 판이 없습니다.</p>
  return (
    <div className="-mx-4 overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            <th className="px-4 py-1.5 font-medium">끝난 시각</th>
            <th className="py-1.5 font-medium">시나리오</th>
            <th className="py-1.5 text-right font-medium">순위</th>
            <th className="py-1.5 text-right font-medium">최종 자산</th>
            <th className="py-1.5 text-right font-medium">수익률</th>
            <th className="px-4 py-1.5" />
          </tr>
        </thead>
        <tbody>
          {history.map((h) => (
            <tr key={h.gameId} className="border-t border-line">
              <td className="px-4 py-2 text-muted">{h.finishedAt.replace('T', ' ').slice(0, 16)}</td>
              <td className="py-2">
                <Tag tone={h.mode === 'DAILY' ? 'brand' : 'ok'}>{MODE_TEXT[h.mode]}</Tag> <span className="ml-1">{h.scenarioTitle}</span>
              </td>
              <td className="py-2 text-right font-semibold">
                {h.rank}
                <span className="text-muted">/{h.playerCount}</span>
              </td>
              <td className="py-2 text-right">{won(h.finalAsset)}</td>
              <td className={`py-2 text-right font-semibold ${toneOf(h.returnRate)}`}>{ratioPct(h.returnRate)}</td>
              <td className="px-4 py-2 text-right">
                <Button size="sm" variant="subtle" onClick={() => onOpen(h.gameId)}>
                  결과
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Lobby({
  user,
  onEnterRoom,
  onOpenResult,
}: {
  user: User
  onEnterRoom: (roomId: string) => void
  onOpenResult: (resultId: number) => void
}) {
  const [options, setOptions] = useState<RoomOptions | null>(null)
  const [rooms, setRooms] = useState<RoomView[]>([])
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  // 방 만들기 선택지와 내 전적 (M8). 로비에 들어올 때 한 번 읽는다
  useEffect(() => {
    api.roomOptions().then(setOptions).catch((e: Error) => setError(e.message))
    api.history(user.id).then(setHistory).catch((e: Error) => setError(e.message))
  }, [user.id])

  // 대기 중인 방 목록. 2초마다 새로 읽는다
  useEffect(() => {
    let alive = true
    const load = () =>
      api
        .listRooms()
        .then((r) => {
          if (!alive) return
          setRooms(r)
          setError(null)
        })
        .catch(() => alive && setError('서버에 연결할 수 없습니다'))
    load()
    const timer = setInterval(load, 2000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [])

  return (
    <div className="space-y-5">
      <ErrorText>{error}</ErrorText>
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <Panel title="방 만들기">
          {options ? <CreateRoom user={user} options={options} onCreated={onEnterRoom} /> : <p className="text-sm text-faint">불러오는 중…</p>}
        </Panel>
        <Panel title="대기 중인 방" right={`${rooms.length}개 · 2초마다 새로고침`}>
          <RoomList rooms={rooms} onEnter={onEnterRoom} />
        </Panel>
      </div>
      <Panel title="내 전적" right={history.length ? `최근 ${history.length}판` : undefined}>
        <History history={history} onOpen={onOpenResult} />
      </Panel>
    </div>
  )
}
