// 방 화면 상태. WebSocket 메시지 타입마다 한 갈래씩 처리한다 (CLAUDE.md §4 「상태 관리」).
import type { Rank, RoomView } from '../api/types'
import type { GameStart, OrderResult, PlayerSnapshot, RankingEntry, ServerMessage, Tick } from './messages'

const RAW_LIMIT = 30
const EVENT_LIMIT = 50

export interface RoomState {
  room: RoomView | null
  game: GameStart | null
  tick: Tick | null
  me: PlayerSnapshot | null
  lastOrder: OrderResult | null
  ranking: RankingEntry[]
  rankingTick: number
  /** 청산·뉴스·오류 알림. 최근 것이 위 */
  events: string[]
  end: { resultId: string; rankings: Rank[] } | null
  /** 방이 닫혔거나 없어서 더 있을 수 없다. 이유를 보여주고 로비로 보낸다 */
  gone: string | null
  /** 받은 원본 JSON. 최근 것이 위 (P0: 「raw JSON 을 그대로 화면에 흘리기」) */
  raw: string[]
}

export const initialRoomState: RoomState = {
  room: null,
  game: null,
  tick: null,
  me: null,
  lastOrder: null,
  ranking: [],
  rankingTick: 0,
  events: [],
  end: null,
  gone: null,
  raw: [],
}

const push = (list: string[], item: string, limit: number) => [item, ...list].slice(0, limit)

export function roomReducer(state: RoomState, action: { msg: ServerMessage; raw: string }): RoomState {
  const { msg } = action
  const s = { ...state, raw: push(state.raw, action.raw, RAW_LIMIT) }
  switch (msg.type) {
    case 'ROOM_STATE':
      return msg.payload.status === 'CLOSED'
        ? { ...s, room: msg.payload, gone: '활동 없이 시간이 지나 방이 닫혔습니다' }
        : { ...s, room: msg.payload }
    case 'GAME_START':
      return { ...s, game: msg.payload, tick: null, end: null, lastOrder: null, ranking: [], events: [] }
    case 'TICK':
      return { ...s, tick: msg.payload }
    case 'PLAYER_STATE':
      return { ...s, me: msg.payload }
    case 'ORDER_RESULT':
      return { ...s, lastOrder: msg.payload.result }
    case 'RANKING':
      return { ...s, ranking: msg.payload.rankings, rankingTick: msg.payload.tickIndex }
    case 'LIQUIDATED': {
      const l = msg.payload
      const text = `[${l.tickIndex}틱] 청산 — ${l.nickname} / ${l.symbolLabel} @ ${l.price} (증거금 ${l.lostMargin} 소멸, 수수료 ${l.fee})`
      return { ...s, events: push(s.events, text, EVENT_LIMIT) }
    }
    case 'NEWS': {
      const n = msg.payload
      return { ...s, events: push(s.events, `[${n.tickIndex}틱] 뉴스 — ${n.headlines.join(' / ')}`, EVENT_LIMIT) }
    }
    case 'GAME_END':
      return { ...s, end: msg.payload }
    case 'ERROR': {
      const events = push(s.events, `오류 — ${msg.payload.message}`, EVENT_LIMIT)
      // 새로고침했는데 그사이 방이 사라졌다 (결과 보관 시간 경과, 대기방 만료 등)
      return msg.payload.message.startsWith('방이 없습니다') ? { ...s, events, gone: msg.payload.message } : { ...s, events }
    }
  }
}
