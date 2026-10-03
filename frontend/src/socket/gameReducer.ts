// P1 방 화면 상태. WebSocket 메시지 타입마다 한 갈래씩 처리한다 (CLAUDE.md §4 「상태 관리」).
// 차트를 그리려고 라벨별 봉을 틱 순서대로 쌓는다 — 과거 봉(history)과 판 봉(played)을 따로 둔다.
import type { RoomView } from '../api/types'
import { won } from '../format'
import type {
  Bar,
  GameEnd,
  GameStart,
  OrderResult,
  PlayerSnapshot,
  RankingEntry,
  ServerMessage,
  Tick,
} from './messages'

const FEED_LIMIT = 40

/** 차트에 찍을 내 매매. 서버가 틱을 알려주지 않으므로 결과를 받은 순간의 틱으로 남긴다 */
export interface MyTrade {
  tick: number
  label: string
  kind: 'BUY' | 'SELL' | 'LIQUIDATION'
  price: number
}

export interface FeedItem {
  id: number
  tick: number | null
  kind: 'LIQUIDATED' | 'NEWS' | 'ERROR'
  text: string
  mine: boolean
}

export interface GameState {
  myUserId: string
  room: RoomView | null
  game: GameStart | null
  /** 라벨별 시작 전 봉 */
  history: Record<string, Bar[]>
  /** 라벨별 판 봉. 인덱스 = 틱 (0 = 시작가). 아직 안 온 틱은 비어 있다 */
  played: Record<string, Bar[]>
  tick: Tick | null
  me: PlayerSnapshot | null
  /** 보낸 주문의 종류. 결과가 오면 앞에서부터 짝짓는다 — 한 사람의 주문은 순서대로 처리된다 (§1.6) */
  pending: ('BUY' | 'SELL')[]
  lastOrder: (OrderResult & { action: 'BUY' | 'SELL' | null; seq: number }) | null
  myTrades: MyTrade[]
  ranking: RankingEntry[]
  rankingTick: number
  feed: FeedItem[]
  end: GameEnd | null
  /** 방이 닫혔거나 없어서 더 있을 수 없다. 이유를 보여주고 로비로 보낸다 */
  gone: string | null
  seq: number
}

export function initialGameState(myUserId: string): GameState {
  return {
    myUserId,
    room: null,
    game: null,
    history: {},
    played: {},
    tick: null,
    me: null,
    pending: [],
    lastOrder: null,
    myTrades: [],
    ranking: [],
    rankingTick: 0,
    feed: [],
    end: null,
    gone: null,
    seq: 0,
  }
}

export type GameAction = { type: 'MESSAGE'; msg: ServerMessage } | { type: 'SENT'; action: 'BUY' | 'SELL' }

function feed(s: GameState, item: Omit<FeedItem, 'id'>): Pick<GameState, 'feed' | 'seq'> {
  const seq = s.seq + 1
  return { feed: [{ ...item, id: seq }, ...s.feed].slice(0, FEED_LIMIT), seq }
}

function currentTick(s: GameState): number {
  return s.tick?.tickIndex ?? 0
}

export function gameReducer(s: GameState, action: GameAction): GameState {
  if (action.type === 'SENT') {
    return { ...s, pending: [...s.pending, action.action] }
  }
  const msg = action.msg
  switch (msg.type) {
    case 'ROOM_STATE':
      return msg.payload.status === 'CLOSED'
        ? { ...s, room: msg.payload, gone: '활동 없이 시간이 지나 방이 닫혔습니다' }
        : { ...s, room: msg.payload }

    case 'GAME_START': {
      const g = msg.payload
      const played: Record<string, Bar[]> = {}
      for (const label of g.labels) {
        played[label] = [g.initialBars[label], ...(g.playedBars?.[label] ?? [])]
      }
      // 한 방의 판은 하나뿐이라, 이미 게임을 보고 있었다면 재접속이다 — 내 매매 표시와 알림은 남긴다
      const again = s.game !== null
      return {
        ...s,
        game: g,
        history: g.history ?? {},
        played,
        tick: null,
        end: null,
        pending: [],
        lastOrder: again ? s.lastOrder : null,
        myTrades: again ? s.myTrades : [],
        feed: again ? s.feed : [],
        ranking: again ? s.ranking : [],
      }
    }

    case 'TICK': {
      const t = msg.payload
      const played = { ...s.played }
      for (const [label, bar] of Object.entries(t.bars)) {
        const list = (played[label] ?? []).slice()
        list[t.tickIndex] = bar
        played[label] = list
      }
      return { ...s, tick: t, played }
    }

    case 'PLAYER_STATE':
      return { ...s, me: msg.payload }

    case 'ORDER_RESULT': {
      const r = msg.payload.result
      const [sent, ...rest] = s.pending
      const kind = sent ?? null
      const myTrades =
        r.accepted && kind && r.price !== null
          ? [...s.myTrades, { tick: currentTick(s), label: r.symbolLabel, kind, price: Number(r.price) }]
          : s.myTrades
      return { ...s, pending: rest, myTrades, lastOrder: { ...r, action: kind, seq: s.seq + 1 }, seq: s.seq + 1 }
    }

    case 'RANKING':
      return { ...s, ranking: msg.payload.rankings, rankingTick: msg.payload.tickIndex }

    case 'LIQUIDATED': {
      const l = msg.payload
      const mine = l.userId === s.myUserId
      const myTrades = mine
        ? [...s.myTrades, { tick: l.tickIndex, label: l.symbolLabel, kind: 'LIQUIDATION' as const, price: Number(l.price) }]
        : s.myTrades
      const text = mine
        ? `내 ${l.symbolLabel} 포지션이 청산됐습니다 — 증거금 ${won(l.lostMargin)}원 소멸`
        : `${l.nickname} 님의 ${l.symbolLabel} 포지션이 청산됐습니다`
      return { ...s, myTrades, ...feed(s, { tick: l.tickIndex, kind: 'LIQUIDATED', text, mine }) }
    }

    case 'NEWS':
      return {
        ...s,
        ...feed(s, { tick: msg.payload.tickIndex, kind: 'NEWS', text: msg.payload.headlines.join(' · '), mine: false }),
      }

    case 'GAME_END':
      return { ...s, end: msg.payload }

    case 'ERROR': {
      const m = msg.payload.message
      const next = { ...s, ...feed(s, { tick: null, kind: 'ERROR', text: m, mine: true }) }
      // 새로고침했는데 그사이 방이 사라졌다 (결과 보관 시간 경과, 대기방 만료 등)
      return m.startsWith('방이 없습니다') ? { ...next, gone: m } : next
    }
  }
}
