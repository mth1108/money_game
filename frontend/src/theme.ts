// 차트(ECharts)용 색. CSS 변수를 읽지 못하므로 src/index.css 의 @theme 와 같은 값을 여기 둔다.
export const COLOR = {
  bg: '#0b0e14',
  panel: '#121722',
  line: '#242e42',
  ink: '#e8ebf1',
  muted: '#8b95a8',
  faint: '#5b6579',
  /** 상승 — 빨강 (국내 관례) */
  up: '#f04452',
  /** 하락 — 파랑 */
  down: '#3b82f6',
  warn: '#f5b83d',
  brand: '#8b6cff',
} as const
