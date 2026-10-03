// 순위 — RANKING 은 5초마다, 모든 참가자의 포지션과 배율을 담는다 (의도된 설계, CLAUDE.md §3 M7).
import type { RankingEntry } from '../../socket/messages'

export function Ranking({ ranking, tickIndex, myUserId }: { ranking: RankingEntry[]; tickIndex: number; myUserId: string }) {
  return (
    <div>
      <h3>순위 ({tickIndex}틱 기준, 5초마다 갱신)</h3>
      {ranking.length === 0 ? (
        <p>아직 받지 못함</p>
      ) : (
        <ol>
          {ranking.map((r) => (
            <li key={r.userId}>
              {r.rank}위 {r.nickname}
              {r.userId === myUserId ? ' (나)' : ''} · 총자산 {r.totalAsset} · 청산 {r.liquidatedCount}회 · 포지션{' '}
              {r.positions.length === 0
                ? '없음'
                : r.positions.map((p) => `${p.symbolLabel} x${p.leverage} ${p.quantity}주 @${p.entryPrice}`).join(', ')}
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
