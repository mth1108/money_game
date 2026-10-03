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

export interface Trade {
  tickIndex: number
  userId: string
  symbolLabel: string
  kind: TradeKind
  quantity: number
  price: Money
  margin: Money
  leverage: number
  fee: Money
}

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

export interface RoomResult {
  roomId: string
  result: { rankings: Rank[]; trades: Trade[] }
  scenario: Scenario
}
