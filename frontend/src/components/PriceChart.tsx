// 게임 중 캔들 차트 (P1). 시작 전 과거 봉은 흐리게, 판 봉은 틱마다 오른쪽으로 채워진다.
// 내 포지션의 진입가·청산가 선과 내 매매 지점을 함께 그린다. 시각은 없다 — 가로축은 틱이다.
import { useMemo } from 'react'
import type { EChartsCoreOption } from '../charts/echarts'
import { useChart } from '../charts/useChart'
import type { MyTrade } from '../socket/gameReducer'
import type { Bar, PositionView } from '../socket/messages'
import { COLOR } from '../theme'

const n = (s: string) => Number(s)
const fmt = (v: number) => v.toLocaleString('ko-KR', { maximumFractionDigits: 2 })

const MARK: Record<MyTrade['kind'], { symbol: string; rotate: number; color: string; text: string }> = {
  BUY: { symbol: 'triangle', rotate: 0, color: COLOR.up, text: '매수' },
  SELL: { symbol: 'triangle', rotate: 180, color: COLOR.down, text: '매도' },
  LIQUIDATION: { symbol: 'diamond', rotate: 0, color: COLOR.warn, text: '청산' },
}

export function PriceChart({
  label,
  history,
  played,
  totalTicks,
  position,
  trades,
  height = 420,
}: {
  label: string
  history: Bar[]
  played: Bar[]
  totalTicks: number
  position: PositionView | undefined
  trades: MyTrade[]
  height?: number
}) {
  const option = useMemo<EChartsCoreOption>(() => {
    const h = history.length
    const categories = [...history.map((_, i) => `-${h - i}`), ...Array.from({ length: totalTicks + 1 }, (_, t) => String(t))]
    const all: (Bar | undefined)[] = [...history, ...Array.from({ length: totalTicks + 1 }, (_, t) => played[t])]

    // 과거 봉은 흐리게 — 캔들은 항목별 opacity 가 듣지 않아 색 자체를 옅게 준다
    const dim = { color: `${COLOR.up}55`, color0: `${COLOR.down}55`, borderColor: `${COLOR.up}55`, borderColor0: `${COLOR.down}55` }
    const candles = all.map((b, i) =>
      b ? { value: [n(b.open), n(b.close), n(b.low), n(b.high)], itemStyle: i < h ? dim : undefined } : '-',
    )
    const volumes = all.map((b, i) =>
      b
        ? {
            value: n(b.volume),
            itemStyle: { color: n(b.close) >= n(b.open) ? COLOR.up : COLOR.down, opacity: i < h ? 0.25 : 0.6 },
          }
        : '-',
    )

    let lastTick = -1
    for (let t = played.length - 1; t >= 0; t--) {
      if (played[t]) {
        lastTick = t
        break
      }
    }
    const last = lastTick >= 0 ? n(played[lastTick].close) : null
    const first = played[0] ? n(played[0].close) : null
    const lastTone = last !== null && first !== null && last < first ? COLOR.down : COLOR.up

    const entry = position ? n(position.entryPrice) : null
    const liq = position ? n(position.liquidationPrice) : null
    const lines: object[] = [
      {
        xAxis: '0',
        lineStyle: { color: COLOR.faint, type: 'solid', width: 1 },
        label: { formatter: '시작', color: COLOR.muted, position: 'end', rotate: 0 },
      },
    ]
    if (last !== null) {
      lines.push({
        yAxis: last,
        lineStyle: { color: lastTone, type: 'dotted', width: 1 },
        label: { formatter: fmt(last), color: '#fff', backgroundColor: lastTone, padding: [2, 4], borderRadius: 3 },
      })
    }
    if (entry !== null) {
      lines.push({
        yAxis: entry,
        lineStyle: { color: COLOR.muted, type: 'dashed', width: 1 },
        label: { formatter: `진입 ${fmt(entry)}`, color: COLOR.ink, position: 'insideStartTop' },
      })
    }
    if (liq !== null && liq > 0) {
      lines.push({
        yAxis: liq,
        lineStyle: { color: COLOR.warn, type: 'dashed', width: 1 },
        label: { formatter: `청산 ${fmt(liq)}`, color: COLOR.warn, position: 'insideStartBottom' },
      })
    }

    const points = trades
      .filter((t) => t.label === label)
      .map((t) => ({
        coord: [String(t.tick), t.price],
        symbol: MARK[t.kind].symbol,
        symbolRotate: MARK[t.kind].rotate,
        symbolSize: 12,
        symbolOffset: [0, t.kind === 'SELL' ? -10 : 10],
        itemStyle: { color: MARK[t.kind].color, borderColor: '#fff', borderWidth: 1 },
        label: { show: false },
        name: MARK[t.kind].text,
      }))

    // 청산가가 화면 밖이면 선이 안 보인다 — 세로축을 포지션 선까지 넓힌다
    const extra = [entry, liq].filter((v): v is number => v !== null && v > 0)
    const step = totalTicks <= 60 ? 10 : totalTicks <= 120 ? 20 : 40

    return {
      backgroundColor: 'transparent',
      animation: false,
      grid: [
        { left: 8, right: 72, top: 22, bottom: '27%' },
        { left: 8, right: 72, top: '77%', bottom: 22 },
      ],
      xAxis: [
        {
          type: 'category',
          data: categories,
          axisLine: { lineStyle: { color: COLOR.line } },
          axisLabel: { show: false },
          axisTick: { show: false },
        },
        {
          type: 'category',
          gridIndex: 1,
          data: categories,
          axisLine: { lineStyle: { color: COLOR.line } },
          axisTick: { show: false },
          axisLabel: {
            color: COLOR.faint,
            interval: (_: number, v: string) => !v.startsWith('-') && Number(v) % step === 0,
            formatter: (v: string) => `${v}틱`,
          },
        },
      ],
      yAxis: [
        {
          scale: true,
          position: 'right',
          splitNumber: 5,
          // 포지션 선 때문에 넓힌 양끝 값은 어중간한 수라 이름표를 숨긴다
          axisLabel: { color: COLOR.muted, formatter: fmt, showMinLabel: !extra.length, showMaxLabel: !extra.length },
          splitLine: { lineStyle: { color: COLOR.line, opacity: 0.6 } },
          min: (v: { min: number }) => (extra.length ? Math.min(v.min, ...extra) * 0.995 : v.min),
          max: (v: { max: number }) => (extra.length ? Math.max(v.max, ...extra) * 1.005 : v.max),
        },
        { gridIndex: 1, scale: true, position: 'right', splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
      ],
      dataZoom: [{ type: 'inside', xAxisIndex: [0, 1] }],
      axisPointer: { link: [{ xAxisIndex: 'all' }], label: { backgroundColor: COLOR.panel } },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', lineStyle: { color: COLOR.faint } },
        backgroundColor: COLOR.panel,
        borderColor: COLOR.line,
        textStyle: { color: COLOR.ink, fontSize: 12 },
        formatter: (params: { dataIndex: number }[]) => {
          const i = params[0]?.dataIndex
          const b = i === undefined ? undefined : all[i]
          if (!b) return ''
          const head = i < h ? `시작 전 ${h - i}봉` : `${i - h}틱`
          return `<b>${head}</b><br/>시가 ${fmt(n(b.open))}<br/>고가 ${fmt(n(b.high))}<br/>저가 ${fmt(n(b.low))}<br/>종가 ${fmt(n(b.close))}<br/>거래량 ${fmt(n(b.volume))}`
        },
      },
      series: [
        {
          type: 'candlestick',
          data: candles,
          itemStyle: { color: COLOR.up, color0: COLOR.down, borderColor: COLOR.up, borderColor0: COLOR.down },
          markArea: h
            ? {
                silent: true,
                itemStyle: { color: 'rgba(255,255,255,0.025)' },
                label: { color: COLOR.faint, position: 'insideTop', fontSize: 11 },
                data: [[{ name: '시작 전', xAxis: categories[0] }, { xAxis: categories[h - 1] }]],
              }
            : undefined,
          markLine: { silent: true, symbol: 'none', data: lines },
          markPoint: { data: points, animation: false },
        },
        { type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: volumes, barWidth: '60%' },
      ],
    }
  }, [label, history, played, totalTicks, position, trades])

  const ref = useChart(option, label)
  return <div ref={ref} style={{ height }} className="w-full" />
}
