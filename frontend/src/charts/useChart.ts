// ECharts 인스턴스를 div 하나에 붙인다. 크기가 바뀌면 다시 그리고, 화면에서 빠지면 정리한다.
// React 래퍼 패키지는 쓰지 않는다 (§8 — 의존성은 echarts 하나).
import { useEffect, useRef } from 'react'
import { echarts, type EChartsCoreOption } from './echarts'

/**
 * @param option   매번 새로 만들어 넘긴다. series 는 통째로 바꾸고, 확대(dataZoom) 같은 사용자 조작은 남긴다
 * @param resetKey 이 값이 바뀌면 차트를 처음부터 다시 그린다 (예: 종목 전환)
 */
export function useChart(option: EChartsCoreOption, resetKey?: unknown) {
  const ref = useRef<HTMLDivElement>(null)
  const chart = useRef<ReturnType<typeof echarts.init> | null>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const c = echarts.init(el, null, { renderer: 'canvas' })
    chart.current = c
    const observer = new ResizeObserver(() => c.resize())
    observer.observe(el)
    return () => {
      observer.disconnect()
      c.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    chart.current?.clear()
  }, [resetKey])

  useEffect(() => {
    chart.current?.setOption(option, { replaceMerge: ['series'], lazyUpdate: true })
  }, [option])

  return ref
}
