// 주문 폼 — 종목 / 매수·매도 / 증거금 / 배율 (CLAUDE.md §4 P0, §3 M7 ORDER).
// 증거금은 상한이다. 실제 차감액은 체결 수량에서 역산된다 (§1.5). 정밀도를 지키려 문자열로 보낸다.
import { useState } from 'react'
import type { ClientMessage, GameStart, OrderResult } from '../../socket/messages'
import { REJECT_TEXT } from '../../socket/messages'

export function OrderForm({
  game,
  lastOrder,
  onSend,
}: {
  game: GameStart
  lastOrder: OrderResult | null
  onSend: (msg: ClientMessage) => void
}) {
  const leverages = [...game.leverages].sort((a, b) => a - b)
  const [label, setLabel] = useState(game.labels[0])
  const [action, setAction] = useState<'BUY' | 'SELL'>('BUY')
  const [margin, setMargin] = useState('10000000')
  const [leverage, setLeverage] = useState(leverages[0])

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (action === 'BUY') onSend({ type: 'ORDER', symbolLabel: label, action, margin: margin.trim(), leverage })
    else onSend({ type: 'ORDER', symbolLabel: label, action })
  }

  return (
    <div>
      <h3>주문</h3>
      <form onSubmit={submit}>
        <label>
          종목{' '}
          <select value={label} onChange={(e) => setLabel(e.target.value)}>
            {game.labels.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </label>{' '}
        <label>
          <input type="radio" checked={action === 'BUY'} onChange={() => setAction('BUY')} /> 매수
        </label>{' '}
        <label>
          <input type="radio" checked={action === 'SELL'} onChange={() => setAction('SELL')} /> 매도(전량)
        </label>{' '}
        {action === 'BUY' && (
          <>
            <label>
              증거금(상한) <input value={margin} onChange={(e) => setMargin(e.target.value)} inputMode="decimal" size={12} />
            </label>{' '}
            <label>
              배율{' '}
              <select value={leverage} onChange={(e) => setLeverage(Number(e.target.value))}>
                {leverages.map((l) => (
                  <option key={l} value={l}>
                    {l}
                  </option>
                ))}
              </select>
            </label>{' '}
          </>
        )}
        <button type="submit">주문</button>
      </form>
      <p>
        마지막 주문:{' '}
        {lastOrder === null
          ? '없음'
          : lastOrder.accepted
            ? `체결 — ${lastOrder.symbolLabel} ${lastOrder.quantity}주 @ ${lastOrder.price}, 증거금 ${lastOrder.actualMargin}, 수수료 ${lastOrder.fee}`
            : `거부 — ${lastOrder.symbolLabel}: ${REJECT_TEXT[lastOrder.reason]} (${lastOrder.reason})`}
      </p>
    </div>
  )
}
