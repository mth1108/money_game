// 게임 화면 (P1) — 남은 시간·내 자산 / 종목 카드 / 캔들 차트 / 주문 / 내 포지션 / 순위 / 알림.
// 넓은 화면은 두 칸, 좁으면 세로로 쌓는다 (§8 2026-10-03 「데스크톱 우선, 폰에서 깨지지만 않게」).
import { useState } from 'react'
import type { User } from '../api/types'
import { Feed, MyPositions, RankingList } from '../components/GameSide'
import { OrderPanel } from '../components/OrderPanel'
import { PriceChart } from '../components/PriceChart'
import { SymbolCards } from '../components/SymbolCards'
import { Panel, Stat, Tag } from '../components/ui'
import { changePct, clock, signedWon, toneOf, won } from '../format'
import type { GameState } from '../socket/gameReducer'
import type { ClientMessage } from '../socket/messages'
import { MODE_TEXT } from './Lobby'

export function Game({ state, user, send }: { state: GameState; user: User; send: (msg: ClientMessage) => void }) {
  const game = state.game!
  const [label, setLabel] = useState(game.labels[0])
  const myId = String(user.id)

  const tickIndex = state.tick?.tickIndex ?? 0
  const remainingMillis = state.tick?.remainingMillis ?? game.totalTicks * game.tickMillis
  const finished = state.end !== null || state.room?.status === 'FINISHED'
  const canTrade = !finished && (state.tick?.remainingTicks ?? game.totalTicks) > 0
  const progress = Math.min(100, (tickIndex / game.totalTicks) * 100)

  const me = state.me
  const seed = Number(game.seedMoney)
  const total = me ? Number(me.totalAsset) : seed
  const pnl = me ? (Number(me.totalAsset) - seed).toFixed(0) : '0'
  const myRank = state.ranking.find((r) => r.userId === myId)?.rank

  const buy = (symbolLabel: string, margin: string, leverage: number) =>
    send({ type: 'ORDER', symbolLabel, action: 'BUY', margin, leverage })
  const sell = (symbolLabel: string) => send({ type: 'ORDER', symbolLabel, action: 'SELL' })

  return (
    <div className="space-y-4">
      {/* 상태 줄 */}
      <div className="rounded-xl border border-line bg-panel">
        <div className="flex flex-wrap items-center gap-x-8 gap-y-3 px-4 py-3">
          <div className="flex items-center gap-3">
            <div className={`text-3xl font-black tabular-nums ${remainingMillis <= 10_000 && !finished ? 'text-up' : ''}`}>
              {finished ? '종료' : clock(remainingMillis)}
            </div>
            <div className="text-xs text-muted">
              <div>
                <Tag tone={game.mode === 'DAILY' ? 'brand' : 'ok'}>{MODE_TEXT[game.mode]}</Tag>
              </div>
              <div className="mt-1">
                {tickIndex} / {game.totalTicks}틱
              </div>
            </div>
          </div>
          <Stat label="총자산" value={`${won(me?.totalAsset ?? game.seedMoney)}원`} sub={<span className={toneOf(total - seed)}>{changePct(seed, total)}</span>} />
          <Stat label="손익" value={<span className={toneOf(total - seed)}>{signedWon(pnl)}</span>} />
          <Stat label="현금" value={won(me?.cash ?? game.seedMoney)} />
          <Stat label="순위" value={myRank ? `${myRank}위` : '-'} sub={<span className="text-muted">/ {state.ranking.length || state.room?.participants.length || 1}명</span>} />
        </div>
        <div className="h-1 overflow-hidden rounded-b-xl bg-bg">
          <div className="h-full bg-brand transition-[width] duration-700 ease-linear" style={{ width: `${progress}%` }} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0 space-y-4">
          <SymbolCards
            labels={game.labels}
            played={state.played}
            totalTicks={game.totalTicks}
            selected={label}
            onSelect={setLabel}
            positions={me?.positions ?? []}
          />
          <Panel
            title={`종목 ${label}`}
            right={state.history[label]?.length ? `흐린 봉 = 시작 전 ${state.history[label].length}봉 · 휠로 확대` : '휠로 확대'}
          >
            <div className="-mx-2 -my-2">
              <PriceChart
                label={label}
                history={state.history[label] ?? []}
                played={state.played[label] ?? []}
                totalTicks={game.totalTicks}
                position={me?.positions.find((p) => p.symbolLabel === label)}
                trades={state.myTrades}
              />
            </div>
          </Panel>
          <Panel title="내 포지션" right={me ? `거래 ${me.tradeCount}회 · 청산 ${me.liquidatedCount}회` : undefined}>
            <MyPositions me={me} canTrade={canTrade} onSell={sell} />
          </Panel>
        </div>

        <div className="min-w-0 space-y-4">
          <Panel title="주문" right={canTrade ? undefined : finished ? '판이 끝났습니다' : '시간 종료'}>
            <OrderPanel
              game={game}
              me={me}
              label={label}
              onLabel={setLabel}
              canTrade={canTrade}
              lastOrder={state.lastOrder}
              onBuy={buy}
              onSell={sell}
            />
          </Panel>
          <Panel title="순위" right={state.rankingTick ? `${state.rankingTick}틱 기준 · 5초마다` : '5초마다'}>
            <RankingList ranking={state.ranking} seedMoney={game.seedMoney} myUserId={myId} />
          </Panel>
          <Panel title="알림">
            <Feed items={state.feed} />
          </Panel>
        </div>
      </div>
    </div>
  )
}
