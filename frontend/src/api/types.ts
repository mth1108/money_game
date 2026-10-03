// REST 응답 타입. 백엔드 record 와 1:1 (CLAUDE.md §3 M6).
// 금액·가격은 문자열이다 — 서버가 BigDecimal 을 문자열로 보낸다 (§1.5). 화면은 그대로 보여주고 계산하지 않는다.

export type Money = string

export interface User {
  id: number
  nickname: string
}

export type GameMode = 'DAILY' | 'MINUTE'
export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'

export interface Participant {
  userId: string
  nickname: string
  ready: boolean
  /** 존버 봇이면 true. 봇은 항상 준비 상태다 (M9) */
  bot: boolean
}

/** 방 만들기 선택지 (GET /api/rooms/options). 화면은 선택지를 하드코딩하지 않는다 */
export interface RoomOptions {
  modes: { mode: GameMode; leverages: number[] }[]
  allowedTicks: number[]
  defaultTicks: number
  tickMillis: number
  defaultMaxPlayers: number
  maxPlayers: number
  maxBots: number
  defaultSeedMoney: Money
}

export interface RoomView {
  id: string
  status: RoomStatus
  mode: GameMode
  maxPlayers: number
  hostUserId: string
  participants: Participant[]
  labels: string[]
  tickIndex: number
  totalTicks: number
}

export interface Rank {
  rank: number
  userId: string
  nickname: string
  totalAsset: Money
  returnRate: Money
  tradeCount: number
  liquidatedCount: number
}

export type TradeKind = 'BUY' | 'SELL' | 'LIQUIDATION' | 'SETTLEMENT'

export interface ScenarioSymbol {
  label: string
  code: string
  name: string
}

export interface Scenario {
  id: number
  title: string
  interval: string
  start: string
  end: string
  barCount: number
  symbols: ScenarioSymbol[]
}

// ── M8 결과 (GET /api/results/{id}, /api/users/{id}/results) ──

export interface ResultParticipant {
  playerKey: string
  userId: number | null
  nickname: string
  bot: boolean
  rank: number
  finalAsset: Money
  returnRate: Money
  tradeCount: number
  liquidatedCount: number
}

export interface TradeRow {
  tickIndex: number
  playerKey: string
  nickname: string
  bot: boolean
  symbolLabel: string
  symbolCode: string | null
  symbolName: string | null
  kind: TradeKind
  quantity: number
  price: Money
  margin: Money
  leverage: number
  fee: Money
}

export interface GameRecord {
  id: number
  roomCode: string
  mode: GameMode
  seedMoney: Money
  totalTicks: number
  /** 실제로 쓴 시세 구간. 짧은 판이면 시나리오 기간보다 짧다 */
  periodStart: string
  periodEnd: string
  startedAt: string
  finishedAt: string
  scenario: Scenario
  participants: ResultParticipant[]
  trades: TradeRow[]
}

export interface HistoryEntry {
  gameId: number
  finishedAt: string
  mode: GameMode
  totalTicks: number
  scenarioTitle: string
  rank: number
  playerCount: number
  finalAsset: Money
  returnRate: Money
  liquidatedCount: number
}
