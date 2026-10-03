// 결과 차트 (P1) — 끝난 판이라 실제 날짜·시각으로 그리고, 매매 지점을 찍는다.
import { useMemo } from 'react'
import type { GameMode, SymbolChart, TradeRow } from '../api/types'
import type { EChartsCoreOption } from '../charts/echarts'
import { useChart } from '../charts/useChart'
import { COLOR } from '../theme'

const KIND = {
  BUY: { symbol: 'triangle', rotate: 0, color: COLOR.up, text: '매수', offset: 10 },
  SELL: { symbol: 'triangle', rotate: 180, color: COLOR.down, text: '매도', offset: -10 },
  LIQUIDATION: { symbol: 'diamond', rotate: 0, color: COLOR.warn, text: '청산', offset: 0 },
  SETTLEMENT: { symbol: 'circle', rotate: 0, color: COLOR.muted, text: '종료 정리', offset: 0 },
} as const

const fmt = (v: number) => v.toLocaleString('ko-KR', { maximumFractionDigits: 2 })

export function ResultChart({
  chart,
  mode,
  trades,
  myKey,
  showAll,
}: {
  chart: SymbolChart
  mode: GameMode
  trades: TradeRow[]
  myKey: string
  showAll: boolean
}) {
  const option = useMemo<EChartsCoreOption>(() => {
    const cats = chart.bars.map((b) => (mode === 'MINUTE' ? b.time.slice(11, 16) : b.time.slice(0, 10)))
    const candles = chart.bars.map((b) => [Number(b.open), Number(b.close), Number(b.low), Number(b.high)])
    const volumes = chart.bars.map((b) => ({
      value: Number(b.volume),
      itemStyle: { color: Number(b.close) >= Number(b.open) ? COLOR.up : COLOR.down, opacity: 0.55 },
    }))
    const points = trades
      .filter((t) => t.symbolLabel === chart.label && t.tickIndex < cats.length && (showAll || t.playerKey === myKey))
      .map((t) => {
        const k = KIND[t.kind]
        const mine = t.playerKey === myKey
        return {
          coord: [cats[t.tickIndex], Number(t.price)],
          symbol: k.symbol,
          symbolRotate: k.rotate,
          symbolSize: mine ? 13 : 9,
          symbolOffset: [0, k.offset],
          itemStyle: { color: mine ? k.color : COLOR.faint, borderColor: mine ? '#fff' : COLOR.line, borderWidth: 1 },
          label: { show: false },
          name: `${t.nickname} ${k.text} ${t.quantity}주 @ ${fmt(Number(t.price))}`,
        }
      })
    return {
      backgroundColor: 'transparent',
      animation: false,
      grid: [
        { left: 8, right: 72, top: 12, bottom: '27%' },
        { left: 8, right: 72, top: '77%', bottom: 22 },
      ],
      xAxis: [
        { type: 'category', data: cats, axisLabel: { show: false }, axisTick: { show: false }, axisLine: { lineStyle: { color: COLOR.line } } },
        {
          type: 'category',
          gridIndex: 1,
          data: cats,
          axisTick: { show: false },
          axisLine: { lineStyle: { color: COLOR.line } },
          axisLabel: { color: COLOR.faint, hideOverlap: true },
        },
      ],
      yAxis: [
        {
          scale: true,
          position: 'right',
          axisLabel: { color: COLOR.muted, formatter: fmt },
          splitLine: { lineStyle: { color: COLOR.line, opacity: 0.6 } },
        },
        { gridIndex: 1, scale: true, position: 'right', splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
      ],
      dataZoom: [{ type: 'inside', xAxisIndex: [0, 1] }],
      axisPointer: { link: [{ xAxisIndex: 'all' }] },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', lineStyle: { color: COLOR.faint } },
        backgroundColor: COLOR.panel,
        borderColor: COLOR.line,
        textStyle: { color: COLOR.ink, fontSize: 12 },
        formatter: (params: { dataIndex: number }[]) => {
          const i = params[0]?.dataIndex
          const b = i === undefined ? undefined : chart.bars[i]
          if (!b) return ''
          const here = trades
            .filter((t) => t.symbolLabel === chart.label && t.tickIndex === i && (showAll || t.playerKey === myKey))
            .map((t) => `${t.nickname} ${KIND[t.kind].text} ${t.quantity.toLocaleString('ko-KR')}주`)
          return [
            `<b>${b.time.replace('T', ' ').slice(0, mode === 'MINUTE' ? 16 : 10)}</b> · ${i}틱`,
            `시가 ${fmt(Number(b.open))} · 고가 ${fmt(Number(b.high))}`,
            `저가 ${fmt(Number(b.low))} · 종가 ${fmt(Number(b.close))}`,
            ...here,
          ].join('<br/>')
        },
      },
      series: [
        {
          type: 'candlestick',
          data: candles,
          itemStyle: { color: COLOR.up, color0: COLOR.down, borderColor: COLOR.up, borderColor0: COLOR.down },
          markPoint: { data: points },
        },
        { type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: volumes, barWidth: '60%' },
      ],
    }
  }, [chart, mode, trades, myKey, showAll])

  const ref = useChart(option, chart.label)
  return <div ref={ref} className="h-[380px] w-full" />
}
