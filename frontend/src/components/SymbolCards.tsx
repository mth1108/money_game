// 종목 카드 — 라벨별 현재가, 시작가 대비 등락, 미니 차트, 내 포지션. 누르면 그 종목을 차트·주문에 띄운다.
import { changePct, price, signedWon, toneOf } from '../format'
import type { Bar, PositionView } from '../socket/messages'
import { COLOR } from '../theme'

function lastBar(bars: Bar[]): Bar | undefined {
  for (let i = bars.length - 1; i >= 0; i--) if (bars[i]) return bars[i]
  return undefined
}

function Sparkline({ bars, totalTicks }: { bars: Bar[]; totalTicks: number }) {
  const closes = bars.map((b) => (b ? Number(b.close) : NaN))
  const known = closes.filter((v) => !Number.isNaN(v))
  if (known.length < 2) return <svg className="h-10 w-full" />
  const min = Math.min(...known)
  const max = Math.max(...known)
  const span = max - min || 1
  const w = 120
  const hgt = 40
  const pts = closes
    .map((v, i) => (Number.isNaN(v) ? null : `${((i / totalTicks) * w).toFixed(1)},${(hgt - 2 - ((v - min) / span) * (hgt - 4)).toFixed(1)}`))
    .filter(Boolean)
    .join(' ')
  const up = known[known.length - 1] >= known[0]
  const base = hgt - 2 - ((known[0] - min) / span) * (hgt - 4)
  return (
    <svg viewBox={`0 0 ${w} ${hgt}`} preserveAspectRatio="none" className="h-10 w-full">
      <line x1="0" x2={w} y1={base} y2={base} stroke={COLOR.line} strokeDasharray="2 3" vectorEffect="non-scaling-stroke" />
      <polyline points={pts} fill="none" stroke={up ? COLOR.up : COLOR.down} strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

export function SymbolCards({
  labels,
  played,
  totalTicks,
  selected,
  onSelect,
  positions,
}: {
  labels: string[]
  played: Record<string, Bar[]>
  totalTicks: number
  selected: string
  onSelect: (label: string) => void
  positions: PositionView[]
}) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {labels.map((label) => {
        const bars = played[label] ?? []
        const first = bars[0] ? Number(bars[0].close) : 0
        const last = lastBar(bars)
        const now = last ? Number(last.close) : first
        const pos = positions.find((p) => p.symbolLabel === label)
        const active = label === selected
        return (
          <button
            key={label}
            onClick={() => onSelect(label)}
            className={`rounded-xl border p-3 text-left transition ${
              active ? 'border-brand bg-brand/10' : 'border-line bg-panel hover:border-faint'
            }`}
          >
            <div className="flex items-baseline justify-between">
              <span className="text-lg font-black">{label}</span>
              <span className={`text-xs font-semibold ${toneOf(now - first)}`}>{changePct(first, now)}</span>
            </div>
            <div className="text-base font-bold">{last ? price(last.close) : '-'}</div>
            <Sparkline bars={bars} totalTicks={totalTicks} />
            <div className="mt-1 h-4 text-[11px]">
              {pos ? (
                <span className={toneOf(pos.unrealizedPnl)}>
                  보유 ×{pos.leverage} · {signedWon(pos.unrealizedPnl)}
                </span>
              ) : (
                <span className="text-faint">미보유</span>
              )}
            </div>
          </button>
        )
      })}
    </div>
  )
}
