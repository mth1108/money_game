// 결과 (CLAUDE.md §3 M8) — 실제 종목 공개, 순위·수익률, 매매 지점이 찍힌 차트, 내 거래(시간 순), 전체 체결.
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { GameRecord, TradeRow, User } from '../api/types'
import { ResultChart } from '../components/ResultChart'
import { Button, ErrorText, Panel, Segmented, Tag } from '../components/ui'
import { changePct, price, ratioPct, toneOf, won } from '../format'
import { MODE_TEXT } from './Lobby'

const KIND_TEXT = { BUY: '매수', SELL: '매도', LIQUIDATION: '강제 청산', SETTLEMENT: '종료 정리' } as const
const KIND_TONE = { BUY: 'up', SELL: 'down', LIQUIDATION: 'warn', SETTLEMENT: 'muted' } as const

function TradeTable({ rows, showPlayer }: { rows: TradeRow[]; showPlayer: boolean }) {
  if (rows.length === 0) return <p className="py-2 text-sm text-faint">거래가 없습니다.</p>
  return (
    <div className="-mx-4 overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            <th className="px-4 py-1.5 text-right font-medium">틱</th>
            {showPlayer && <th className="py-1.5 font-medium">참가자</th>}
            <th className="py-1.5 font-medium">종목</th>
            <th className="py-1.5 font-medium">구분</th>
            <th className="py-1.5 text-right font-medium">수량</th>
            <th className="py-1.5 text-right font-medium">가격</th>
            <th className="py-1.5 text-right font-medium">증거금</th>
            <th className="py-1.5 text-right font-medium">배율</th>
            <th className="px-4 py-1.5 text-right font-medium">수수료</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t, i) => (
            <tr key={i} className="border-t border-line">
              <td className="px-4 py-1.5 text-right text-muted">{t.tickIndex}</td>
              {showPlayer && (
                <td className="py-1.5">
                  {t.nickname}
                  {t.bot && <span className="ml-1 text-xs text-faint">봇</span>}
                </td>
              )}
              <td className="py-1.5">
                <span className="font-bold">{t.symbolLabel}</span> <span className="text-muted">{t.symbolName}</span>
              </td>
              <td className="py-1.5">
                <Tag tone={KIND_TONE[t.kind]}>{KIND_TEXT[t.kind]}</Tag>
              </td>
              <td className="py-1.5 text-right">{t.quantity.toLocaleString('ko-KR')}</td>
              <td className="py-1.5 text-right">{price(t.price)}</td>
              <td className="py-1.5 text-right">{won(t.margin)}</td>
              <td className="py-1.5 text-right">×{t.leverage}</td>
              <td className="px-4 py-1.5 text-right">{won(t.fee)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Result({ resultId, user, onBack }: { resultId: number; user: User; onBack: () => void }) {
  const [record, setRecord] = useState<GameRecord | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [label, setLabel] = useState<string | null>(null)
  const [showAll, setShowAll] = useState(false)

  useEffect(() => {
    let alive = true
    api
      .result(resultId)
      .then((r) => {
        if (!alive) return
        setRecord(r)
        setLabel(r.scenario.symbols[0]?.label ?? null)
      })
      .catch((e: Error) => alive && setError(e.message))
    return () => {
      alive = false
    }
  }, [resultId])

  if (error) {
    return (
      <div className="space-y-3">
        <Button size="sm" onClick={onBack}>
          ← 돌아가기
        </Button>
        <ErrorText>결과를 읽지 못했습니다: {error}</ErrorText>
      </div>
    )
  }
  if (!record) return <p className="py-10 text-center text-sm text-faint">결과 불러오는 중…</p>

  const myKey = String(user.id)
  const me = record.participants.find((p) => p.playerKey === myKey)
  const mine = record.trades.filter((t) => t.playerKey === myKey)
  const chart = record.charts.find((c) => c.label === label)
  const period = (s: string) => (record.mode === 'MINUTE' ? s.replace('T', ' ').slice(0, 16) : s.slice(0, 10))

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button size="sm" onClick={onBack}>
          ← 로비로
        </Button>
        <span className="text-xs text-faint">결과 #{record.id}</span>
      </div>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Tag tone={record.mode === 'DAILY' ? 'brand' : 'ok'}>{MODE_TEXT[record.mode]}</Tag>
            <Tag>{record.totalTicks}틱</Tag>
          </div>
          <h1 className="mt-2 text-2xl font-black">{record.scenario.title}</h1>
          <p className="mt-1 text-sm text-muted">
            실제 기간 {period(record.periodStart)} ~ {period(record.periodEnd)}
          </p>
        </div>
        {me && (
          <div className="text-right">
            <div className="text-sm text-muted">
              내 순위 <span className="text-2xl font-black text-ink">{me.rank}위</span> / {record.participants.length}명
            </div>
            <div className={`text-lg font-bold ${toneOf(me.returnRate)}`}>
              {won(me.finalAsset)}원 ({ratioPct(me.returnRate)})
            </div>
          </div>
        )}
      </div>

      {/* 종목 공개 */}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {record.scenario.symbols.map((s) => {
          const bars = record.charts.find((c) => c.label === s.label)?.bars ?? []
          const from = bars.length ? Number(bars[0].close) : 0
          const to = bars.length ? Number(bars[bars.length - 1].close) : 0
          return (
            <button
              key={s.label}
              onClick={() => setLabel(s.label)}
              className={`rounded-xl border p-3 text-left transition ${label === s.label ? 'border-brand bg-brand/10' : 'border-line bg-panel hover:border-faint'}`}
            >
              <div className="text-xs text-muted">종목 {s.label} 의 정체</div>
              <div className="mt-0.5 truncate text-base font-black">{s.name}</div>
              <div className="flex items-baseline justify-between text-xs">
                <span className="text-faint">{s.code}</span>
                {bars.length > 0 && <span className={`font-semibold ${toneOf(to - from)}`}>{changePct(from, to)}</span>}
              </div>
            </button>
          )
        })}
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_380px]">
        <Panel
          title={chart ? `${chart.label} · ${chart.name}` : '차트'}
          right={
            <Segmented
              size="sm"
              value={showAll ? 'all' : 'me'}
              onChange={(v) => setShowAll(v === 'all')}
              options={[
                { value: 'me', label: '내 매매' },
                { value: 'all', label: '모두' },
              ]}
            />
          }
        >
          {chart && chart.bars.length > 0 ? (
            <div className="-mx-2 -my-2">
              <ResultChart chart={chart} mode={record.mode} trades={record.trades} myKey={myKey} showAll={showAll} />
            </div>
          ) : (
            <p className="py-10 text-center text-sm text-faint">시세를 불러오지 못해 차트가 없습니다.</p>
          )}
          <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted">
            <span>
              <span className="text-up">▲</span> 매수
            </span>
            <span>
              <span className="text-down">▼</span> 매도
            </span>
            <span>
              <span className="text-warn">◆</span> 강제 청산
            </span>
            <span>● 종료 정리</span>
          </div>
        </Panel>

        <Panel title="최종 순위">
          <ol className="space-y-1.5">
            {record.participants.map((p) => (
              <li
                key={p.playerKey}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 ${p.playerKey === myKey ? 'bg-brand/10 ring-1 ring-brand/40' : 'bg-bg'}`}
              >
                <span className={`w-6 text-center font-black ${p.rank === 1 ? 'text-warn' : 'text-muted'}`}>{p.rank}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold">
                    {p.nickname}
                    {p.bot && <span className="ml-1 text-xs text-faint">봇</span>}
                    {p.playerKey === myKey && <span className="ml-1 text-xs text-brand">나</span>}
                  </span>
                  <span className="text-xs text-faint">
                    거래 {p.tradeCount} · 청산 {p.liquidatedCount}
                  </span>
                </span>
                <span className="text-right">
                  <span className="block text-sm font-bold">{won(p.finalAsset)}</span>
                  <span className={`block text-xs ${toneOf(p.returnRate)}`}>{ratioPct(p.returnRate)}</span>
                </span>
              </li>
            ))}
          </ol>
        </Panel>
      </div>

      <Panel title="내 거래" right={`${mine.length}건 · 시간 순`}>
        <TradeTable rows={mine} showPlayer={false} />
      </Panel>
      <details className="rounded-xl border border-line bg-panel">
        <summary className="cursor-pointer px-4 py-2.5 text-sm font-semibold select-none">전체 체결 ({record.trades.length}건)</summary>
        <div className="border-t border-line p-4">
          <TradeTable rows={record.trades} showPlayer />
        </div>
      </details>
    </div>
  )
}
