// 게임 화면의 곁 패널들 — 내 포지션, 순위(남의 포지션·배율 포함, §3 M7), 알림.
import { changePct, price, ratioPct, signedWon, toneOf, won } from '../format'
import type { FeedItem } from '../socket/gameReducer'
import type { PlayerSnapshot, RankingEntry } from '../socket/messages'
import { Tag } from './ui'

export function MyPositions({
  me,
  canTrade,
  onSell,
}: {
  me: PlayerSnapshot | null
  canTrade: boolean
  onSell: (label: string) => void
}) {
  if (!me || me.positions.length === 0) {
    return <p className="py-2 text-sm text-faint">보유한 포지션이 없습니다.</p>
  }
  return (
    <div className="-mx-4 overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            <th className="px-4 py-1.5 font-medium">종목</th>
            <th className="py-1.5 text-right font-medium">수량</th>
            <th className="py-1.5 text-right font-medium">진입가</th>
            <th className="py-1.5 text-right font-medium">청산가</th>
            <th className="py-1.5 text-right font-medium">평가손익</th>
            <th className="py-1.5 text-right font-medium">평가금액</th>
            <th className="px-4 py-1.5" />
          </tr>
        </thead>
        <tbody>
          {me.positions.map((p) => (
            <tr key={p.symbolLabel} className="border-t border-line">
              <td className="px-4 py-2 font-bold">
                {p.symbolLabel} <Tag tone="brand">×{p.leverage}</Tag>
              </td>
              <td className="py-2 text-right">{p.quantity.toLocaleString('ko-KR')}</td>
              <td className="py-2 text-right">{price(p.entryPrice)}</td>
              <td className="py-2 text-right text-warn">{p.leverage > 1 ? price(p.liquidationPrice) : '-'}</td>
              <td className={`py-2 text-right font-semibold ${toneOf(p.unrealizedPnl)}`}>{signedWon(p.unrealizedPnl)}</td>
              <td className="py-2 text-right">{won(p.value)}</td>
              <td className="px-4 py-2 text-right">
                <button
                  disabled={!canTrade}
                  onClick={() => onSell(p.symbolLabel)}
                  className="rounded-md bg-down/15 px-2 py-1 text-xs font-semibold text-down hover:bg-down/25 disabled:opacity-40"
                >
                  매도
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function RankingList({
  ranking,
  seedMoney,
  myUserId,
}: {
  ranking: RankingEntry[]
  seedMoney: string
  myUserId: string
}) {
  if (ranking.length === 0) return <p className="py-2 text-sm text-faint">첫 순위는 5초 뒤에 나옵니다.</p>
  const seed = Number(seedMoney)
  return (
    <ol className="space-y-1.5">
      {ranking.map((r) => {
        const mine = r.userId === myUserId
        const asset = Number(r.totalAsset)
        return (
          <li
            key={r.userId}
            className={`rounded-lg px-3 py-2 ${mine ? 'bg-brand/10 ring-1 ring-brand/40' : 'bg-bg'}`}
          >
            <div className="flex items-center gap-2">
              <span
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-black ${
                  r.rank === 1 ? 'bg-warn text-bg' : 'bg-panel-2 text-muted'
                }`}
              >
                {r.rank}
              </span>
              <span className="min-w-0 flex-1 truncate font-semibold">
                {r.nickname}
                {mine && <span className="ml-1 text-xs text-brand">나</span>}
                {r.userId.startsWith('bot-') && <span className="ml-1 text-xs text-faint">봇</span>}
              </span>
              <span className="text-right">
                <span className="block text-sm font-bold">{won(r.totalAsset)}</span>
                <span className={`block text-[11px] ${toneOf(asset - seed)}`}>{changePct(seed, asset)}</span>
              </span>
            </div>
            {(r.positions.length > 0 || r.liquidatedCount > 0) && (
              <div className="mt-1.5 flex flex-wrap gap-1 pl-8">
                {r.positions.map((p) => (
                  <Tag key={p.symbolLabel} tone={Number(p.unrealizedPnl) >= 0 ? 'up' : 'down'}>
                    {p.symbolLabel} ×{p.leverage}
                  </Tag>
                ))}
                {r.liquidatedCount > 0 && <Tag tone="warn">청산 {r.liquidatedCount}</Tag>}
              </div>
            )}
          </li>
        )
      })}
    </ol>
  )
}

export function Feed({ items }: { items: FeedItem[] }) {
  if (items.length === 0) return <p className="py-2 text-sm text-faint">아직 소식이 없습니다.</p>
  return (
    <ul className="max-h-56 space-y-1.5 overflow-y-auto text-sm">
      {items.map((f) => (
        <li key={f.id} className="flex gap-2">
          <span className="w-10 shrink-0 text-right text-xs text-faint">{f.tick === null ? '' : `${f.tick}틱`}</span>
          <span className={f.kind === 'ERROR' ? 'text-up' : f.kind === 'LIQUIDATED' ? (f.mine ? 'text-warn' : 'text-muted') : 'text-ink'}>
            {f.text}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** 최종 순위 (GAME_END) — 수익률은 서버가 계산한 비율 */
export function FinalRanking({ rankings, myUserId }: { rankings: { rank: number; userId: string; nickname: string; totalAsset: string; returnRate: string; liquidatedCount: number }[]; myUserId: string }) {
  return (
    <ol className="space-y-1.5">
      {rankings.map((r) => (
        <li
          key={r.userId}
          className={`flex items-center gap-3 rounded-lg px-3 py-2 ${r.userId === myUserId ? 'bg-brand/10 ring-1 ring-brand/40' : 'bg-bg'}`}
        >
          <span className={`w-7 text-center text-lg font-black ${r.rank === 1 ? 'text-warn' : 'text-muted'}`}>{r.rank}</span>
          <span className="min-w-0 flex-1 truncate font-semibold">
            {r.nickname}
            {r.userId === myUserId && <span className="ml-1 text-xs text-brand">나</span>}
          </span>
          {r.liquidatedCount > 0 && <Tag tone="warn">청산 {r.liquidatedCount}</Tag>}
          <span className="text-right">
            <span className="block font-bold">{won(r.totalAsset)}원</span>
            <span className={`block text-xs ${toneOf(r.returnRate)}`}>{ratioPct(r.returnRate)}</span>
          </span>
        </li>
      ))}
    </ol>
  )
}
