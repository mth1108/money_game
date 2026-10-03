// WebSocket 메시지 타입. 백엔드 realtime/message 와 1:1 (CLAUDE.md §3 M7).
// 봉투는 {"type": "...", "payload": {...}}. 금액·가격은 문자열이다 (§1.5).
import type { GameMode, Money, Rank, RoomView } from '../api/types'

// ── 클라이언트 → 서버 ──

export type ClientMessage =
  | { type: 'JOIN'; roomId: string; userId: number }
  | { type: 'READY'; ready: boolean }
  | { type: 'ORDER'; symbolLabel: string; action: 'BUY'; margin: Money; leverage: number }
  | { type: 'ORDER'; symbolLabel: string; action: 'SELL' }

// ── 서버 → 클라이언트 ──

/** 한 틱의 캔들. 시각은 없다 — 날짜로 종목을 알아내지 못하게 한다 */
export interface Bar {
  open: Money
  high: Money
  low: Money
  close: Money
  volume: Money
}

export interface PositionView {
  symbolLabel: string
  side: 'LONG' | 'SHORT'
  entryPrice: Money
  quantity: number
  margin: Money
  leverage: number
  liquidationPrice: Money
  unrealizedPnl: Money
  value: Money
}

export interface PlayerSnapshot {
  userId: string
  nickname: string
  cash: Money
  totalAsset: Money
  positions: PositionView[]
  tradeCount: number
  liquidatedCount: number
}

export interface GameStart {
  mode: GameMode
  labels: string[]
  /** 라벨별 시작 전 과거 봉 (오래된 순). 차트 배경이다 */
  history: Record<string, Bar[]>
  /** 0틱 캔들 (시작가) */
  initialBars: Record<string, Bar>
  /** 라벨별 1틱 ~ 지금 틱 봉. 재접속 때만 채워진다 */
  playedBars: Record<string, Bar[]>
  seedMoney: Money
  leverages: number[]
  totalTicks: number
  tickMillis: number
  /** 수수료율 ("0.0015"). 「최대」 증거금 상한을 잡는 데 쓴다 */
  feeRate: Money
}

export interface Tick {
  tickIndex: number
  totalTicks: number
  remainingTicks: number
  remainingMillis: number
  prices: Record<string, Money>
  bars: Record<string, Bar>
}

export type RejectReason =
  | 'NONE'
  | 'INVALID_MARGIN'
  | 'INSUFFICIENT_MARGIN'
  | 'INSUFFICIENT_CASH'
  | 'LEVERAGE_NOT_ALLOWED'
  | 'POSITION_ALREADY_EXISTS'
  | 'NO_POSITION'
  | 'UNKNOWN_SYMBOL'
  | 'UNKNOWN_PLAYER'
  | 'NOT_RUNNING'
  | 'TIME_OVER'

export interface OrderResult {
  accepted: boolean
  reason: RejectReason
  symbolLabel: string
  quantity: number
  price: Money | null
  actualMargin: Money | null
  fee: Money | null
}

export interface RankingEntry {
  rank: number
  userId: string
  nickname: string
  totalAsset: Money
  positions: PositionView[]
  liquidatedCount: number
}

export interface Liquidated {
  tickIndex: number
  userId: string
  nickname: string
  symbolLabel: string
  price: Money
  lostMargin: Money
  fee: Money
}

/** resultId 는 M8 이 저장한 결과 ID. 저장에 실패했으면 null 이다 */
export interface GameEnd {
  resultId: number | null
  roomId: string
  rankings: Rank[]
}

export type ServerMessage =
  | { type: 'ROOM_STATE'; payload: RoomView }
  | { type: 'GAME_START'; payload: GameStart }
  | { type: 'TICK'; payload: Tick }
  | { type: 'PLAYER_STATE'; payload: PlayerSnapshot }
  | { type: 'ORDER_RESULT'; payload: { userId: string; result: OrderResult } }
  | { type: 'RANKING'; payload: { tickIndex: number; rankings: RankingEntry[] } }
  | { type: 'LIQUIDATED'; payload: Liquidated }
  | { type: 'NEWS'; payload: { tickIndex: number; headlines: string[] } }
  | { type: 'GAME_END'; payload: GameEnd }
  | { type: 'ERROR'; payload: { message: string } }

/** 거부 사유를 사람이 읽을 말로. 「증거금 부족」은 §1.5 에 정한 문구다 */
export const REJECT_TEXT: Record<RejectReason, string> = {
  NONE: '',
  INVALID_MARGIN: '증거금은 0 보다 커야 합니다',
  INSUFFICIENT_MARGIN: '증거금 부족 (1주 값에 못 미침)',
  INSUFFICIENT_CASH: '현금 부족',
  LEVERAGE_NOT_ALLOWED: '허용되지 않은 배율',
  POSITION_ALREADY_EXISTS: '이미 이 종목 포지션이 있습니다',
  NO_POSITION: '팔 포지션이 없습니다',
  UNKNOWN_SYMBOL: '없는 종목',
  UNKNOWN_PLAYER: '이 판의 참가자가 아닙니다',
  NOT_RUNNING: '진행 중인 판이 아닙니다',
  TIME_OVER: '시간 종료',
}
