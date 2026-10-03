// 결과 상세 — 순위·수익률·내 거래(시간 순)·전체 체결·실제 종목명 공개 (CLAUDE.md §3 M8).
// 게임 종료 화면과 로비의 「내 전적」에서 함께 쓴다.
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { GameRecord, TradeRow } from '../api/types'

const KIND_TEXT = { BUY: '매수', SELL: '매도', LIQUIDATION: '강제 청산', SETTLEMENT: '종료 정리' } as const

function TradeTable({ rows }: { rows: TradeRow[] }) {
  if (rows.length === 0) return <p>없음</p>
  return (
    <table>
      <thead>
        <tr>
          <th>틱</th>
          <th>참가자</th>
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
        {rows.map((t, i) => (
          <tr key={i}>
            <td>{t.tickIndex}</td>
            <td>
              {t.nickname}
              {t.bot ? ' (봇)' : ''}
            </td>
            <td>
              {t.symbolLabel} = {t.symbolName}
            </td>
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
  )
}

export function ResultView({ resultId, myUserId }: { resultId: number; myUserId: number }) {
  const [record, setRecord] = useState<GameRecord | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    api
      .result(resultId)
      .then((r) => alive && setRecord(r))
      .catch((e: Error) => alive && setError(e.message))
    return () => {
      alive = false
    }
  }, [resultId])

  if (error) return <p>결과를 읽지 못했습니다: {error}</p>
  if (!record) return <p>결과 읽는 중…</p>

  const mine = record.trades.filter((t) => t.playerKey === String(myUserId))
  return (
    <div>
      <h4>
        결과 #{record.id} · 시나리오 「{record.scenario.title}」 ({record.scenario.start} ~ {record.scenario.end}) · {record.mode} · 시드{' '}
        {record.seedMoney}
      </h4>
      <p>
        실제 종목:{' '}
        {record.scenario.symbols.map((s) => `${s.label} = ${s.name}(${s.code})`).join(', ')}
      </p>
      <ol>
        {record.participants.map((p) => (
          <li key={p.playerKey}>
            {p.rank}위 {p.nickname}
            {p.bot ? ' (봇)' : ''}
            {p.playerKey === String(myUserId) ? ' (나)' : ''} · 최종 자산 {p.finalAsset} · 수익률 {p.returnRate} (비율) · 거래{' '}
            {p.tradeCount}회 · 청산 {p.liquidatedCount}회
          </li>
        ))}
      </ol>
      <h4>내 거래 ({mine.length}건, 시간 순)</h4>
      <TradeTable rows={mine} />
      <h4>전체 체결 ({record.trades.length}건)</h4>
      <TradeTable rows={record.trades} />
    </div>
  )
}
