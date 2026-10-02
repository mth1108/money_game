// 종료 — GAME_END 의 최종 순위와, 결과 조회로 실제 종목명 공개 (CLAUDE.md §4 P0).
// 결과 조회는 M8 이 생기기 전 임시 경로다. 끝난 방은 서버 메모리에 10분만 남는다 (§9-7).
import { useState } from 'react'
import { api } from '../api/client'
import type { Rank, RoomResult } from '../api/types'

const KIND_TEXT = { BUY: '매수', SELL: '매도', LIQUIDATION: '강제 청산', SETTLEMENT: '종료 정리' } as const

export function GameEnd({ resultId, rankings }: { resultId: string; rankings: Rank[] }) {
  const [result, setResult] = useState<RoomResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    setError(null)
    try {
      setResult(await api.result(resultId))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div>
      <h3>게임 종료 — 최종 순위</h3>
      <ol>
        {rankings.map((r) => (
          <li key={r.userId}>
            {r.rank}위 {r.nickname} · 총자산 {r.totalAsset} · 수익률 {r.returnRate} (비율) · 거래 {r.tradeCount}회 · 청산{' '}
            {r.liquidatedCount}회
          </li>
        ))}
      </ol>
      <button onClick={load}>결과 보기 (실제 종목 공개)</button>
      {error && <p>오류: {error}</p>}
      {result && (
        <div>
          <p>
            시나리오 #{result.scenario.id} 「{result.scenario.title}」 · {result.scenario.start} ~ {result.scenario.end}
          </p>
          <ul>
            {result.scenario.symbols.map((s) => (
              <li key={s.label}>
                {s.label} = {s.name} ({s.code})
              </li>
            ))}
          </ul>
          <h4>체결 내역 ({result.result.trades.length}건)</h4>
          {result.result.trades.length === 0 ? (
            <p>없음</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>틱</th>
                  <th>사용자</th>
                  <th>종목</th>
                  <th>구분</th>
                  <th>수량</th>
                  <th>가격</th>
                  <th>증거금</th>
                  <th>배율</th>
                  <th>수수료</th>
                </tr>
              </thead>
              <tbody>
                {result.result.trades.map((t, i) => (
                  <tr key={i}>
                    <td>{t.tickIndex}</td>
                    <td>{rankings.find((r) => r.userId === t.userId)?.nickname ?? t.userId}</td>
                    <td>{t.symbolLabel}</td>
                    <td>{KIND_TEXT[t.kind]}</td>
                    <td>{t.quantity}</td>
                    <td>{t.price}</td>
                    <td>{t.margin}</td>
                    <td>{t.leverage}</td>
                    <td>{t.fee}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
