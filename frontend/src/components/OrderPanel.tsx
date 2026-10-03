// 주문 패널 (P1). 종목당 포지션은 하나라 (§3 M5) 보유 중이면 매도, 아니면 매수 화면을 보여준다.
// 증거금은 상한이다 — 실제 차감액은 체결 수량에서 역산된다 (§1.5). 정밀도를 지키려 문자열로 보낸다.
import { useState } from 'react'
import { digitsOnly, fractionOf, maxMargin, price, signedWon, toneOf, won } from '../format'
import type { GameState } from '../socket/gameReducer'
import type { GameStart, PlayerSnapshot } from '../socket/messages'
import { REJECT_TEXT } from '../socket/messages'
import { Button, Segmented, inputClass } from './ui'

type Quick = 10 | 25 | 50 | 'max'

const QUICK: { value: Quick; label: string }[] = [
  { value: 10, label: '10%' },
  { value: 25, label: '25%' },
  { value: 50, label: '50%' },
  { value: 'max', label: '최대' },
]

function OrderMessage({ order }: { order: GameState['lastOrder'] }) {
  if (!order) return null
  if (!order.accepted) {
    return (
      <p key={order.seq} className="rounded-lg bg-up/10 px-3 py-2 text-xs text-up">
        {order.symbolLabel} 주문 거부 — {REJECT_TEXT[order.reason]}
      </p>
    )
  }
  const what = order.action === 'SELL' ? '매도' : '매수'
  return (
    <p key={order.seq} className="rounded-lg bg-ok/10 px-3 py-2 text-xs text-ok">
      {order.symbolLabel} {order.quantity.toLocaleString('ko-KR')}주 {what} 체결 @ {price(order.price)}
      {order.action === 'BUY' && order.actualMargin && ` · 증거금 ${won(order.actualMargin)}원`}
      {order.fee && ` · 수수료 ${won(order.fee)}원`}
    </p>
  )
}

export function OrderPanel({
  game,
  me,
  label,
  onLabel,
  canTrade,
  lastOrder,
  onBuy,
  onSell,
}: {
  game: GameStart
  me: PlayerSnapshot | null
  label: string
  onLabel: (label: string) => void
  canTrade: boolean
  lastOrder: GameState['lastOrder']
  onBuy: (label: string, margin: string, leverage: number) => void
  onSell: (label: string) => void
}) {
  const leverages = [...game.leverages].sort((a, b) => a - b)
  const [leverage, setLeverage] = useState(leverages[0])
  const [typed, setTyped] = useState('')
  const [quick, setQuick] = useState<Quick | null>(25)

  const cash = me?.cash ?? game.seedMoney
  const position = me?.positions.find((p) => p.symbolLabel === label)
  // 빠른 선택은 지금 현금 기준으로 계속 다시 잡는다 — 사고 나면 남은 현금의 25% 가 된다
  const margin =
    quick === null ? typed : quick === 'max' ? maxMargin(cash, leverage, game.feeRate) : fractionOf(cash, quick)
  const safeMax = maxMargin(cash, leverage, game.feeRate)

  return (
    <div className="space-y-4">
      <Segmented value={label} onChange={onLabel} options={game.labels.map((l) => ({ value: l, label: `종목 ${l}` }))} />

      {position ? (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg bg-bg p-3 text-sm">
            <span className="text-muted">수량 · 배율</span>
            <span className="text-right font-semibold">
              {position.quantity.toLocaleString('ko-KR')}주 · ×{position.leverage}
            </span>
            <span className="text-muted">진입가</span>
            <span className="text-right">{price(position.entryPrice)}</span>
            <span className="text-muted">청산가</span>
            <span className="text-right text-warn">{price(position.liquidationPrice)}</span>
            <span className="text-muted">증거금</span>
            <span className="text-right">{won(position.margin)}</span>
            <span className="text-muted">평가손익</span>
            <span className={`text-right font-bold ${toneOf(position.unrealizedPnl)}`}>{signedWon(position.unrealizedPnl)}</span>
          </div>
          <Button variant="down" size="lg" className="w-full" disabled={!canTrade} onClick={() => onSell(label)}>
            {label} 전량 매도
          </Button>
          <p className="text-xs text-faint">한 종목에는 포지션을 하나만 가질 수 있습니다. 팔고 나서 다시 살 수 있습니다.</p>
        </div>
      ) : (
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (margin) onBuy(label, margin, leverage)
          }}
        >
          <div>
            <div className="mb-1.5 flex items-baseline justify-between text-xs">
              <span className="font-medium text-muted">증거금 상한</span>
              <span className="text-faint">최대 {won(safeMax)}원</span>
            </div>
            <div className="relative">
              <input
                className={`${inputClass} pr-8 text-right text-base font-semibold`}
                inputMode="numeric"
                value={margin ? won(margin) : ''}
                placeholder="0"
                onChange={(e) => {
                  setQuick(null)
                  setTyped(digitsOnly(e.target.value))
                }}
              />
              <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm text-muted">원</span>
            </div>
            <div className="mt-2 grid grid-cols-4 gap-1">
              {QUICK.map((q) => (
                <Button key={q.value} type="button" size="sm" variant={quick === q.value ? 'primary' : 'subtle'} onClick={() => setQuick(q.value)}>
                  {q.label}
                </Button>
              ))}
            </div>
          </div>

          <div>
            <div className="mb-1.5 text-xs font-medium text-muted">배율</div>
            <Segmented value={leverage} onChange={setLeverage} options={leverages.map((l) => ({ value: l, label: `×${l}` }))} />
            <p className="mt-1.5 text-xs text-faint">
              {leverage === 1 ? '배율 1 은 청산되지 않습니다.' : `가격이 진입가보다 약 ${(100 / leverage).toFixed(1)}% 내리면 청산되어 증거금을 잃습니다.`}
            </p>
          </div>

          <Button type="submit" variant="up" size="lg" className="w-full" disabled={!canTrade || !margin || margin === '0'}>
            {label} 매수 ×{leverage}
          </Button>
          <p className="text-xs text-faint">실제 증거금은 체결 수량에서 다시 계산되어 상한보다 조금 적을 수 있습니다.</p>
        </form>
      )}

      <OrderMessage order={lastOrder} />
    </div>
  )
}
