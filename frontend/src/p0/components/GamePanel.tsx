// 게임 진행 표시 — 현재가, 남은 시간, 내 현금·포지션·총자산 (CLAUDE.md §4 P0).
// 서버가 보낸 값을 그대로 보여준다. 화면에서 금액을 계산하지 않는다 (§1.5).
import type { GameStart, PlayerSnapshot, Tick } from '../../socket/messages'

export function GamePanel({ game, tick, me }: { game: GameStart; tick: Tick | null; me: PlayerSnapshot | null }) {
  const prices = tick?.prices ?? Object.fromEntries(game.labels.map((l) => [l, game.initialBars[l].close]))
  return (
    <div>
      <h3>진행</h3>
      <p>
        틱 {tick?.tickIndex ?? 0}/{game.totalTicks} · 남은 시간 {tick ? Math.ceil(tick.remainingMillis / 1000) : game.totalTicks * (game.tickMillis / 1000)}초
        · 모드 {game.mode} · 시드머니 {game.seedMoney} · 허용 배율 {game.leverages.join(', ')}
      </p>
      <p>
        현재가:{' '}
        {game.labels.map((l) => (
          <span key={l}>
            {l} {prices[l]}{'   '}
          </span>
        ))}
      </p>

      <h3>내 자산</h3>
      {me ? (
        <>
          <p>
            현금 {me.cash} · 총자산 {me.totalAsset} · 거래 {me.tradeCount}회 · 청산 {me.liquidatedCount}회
          </p>
          {me.positions.length === 0 ? (
            <p>포지션 없음</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>종목</th>
                  <th>수량</th>
                  <th>진입가</th>
                  <th>배율</th>
                  <th>증거금</th>
                  <th>청산가</th>
                  <th>미실현손익</th>
                  <th>가치</th>
                </tr>
              </thead>
              <tbody>
                {me.positions.map((p) => (
                  <tr key={p.symbolLabel}>
                    <td>{p.symbolLabel}</td>
                    <td>{p.quantity}</td>
                    <td>{p.entryPrice}</td>
                    <td>{p.leverage}</td>
                    <td>{p.margin}</td>
                    <td>{p.liquidationPrice}</td>
                    <td>{p.unrealizedPnl}</td>
                    <td>{p.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      ) : (
        <p>아직 받지 못함</p>
      )}
    </div>
  )
}
