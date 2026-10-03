// 표시용 포맷. 금액·가격은 서버가 보낸 문자열을 자릿수만 끊어 보여준다 — double 로 바꾸지 않는다 (CLAUDE.md §1.5).
// 등락률·차트처럼 「보기만 하는」 값만 Number 를 쓴다.
import type { Money } from './api/types'

interface Parts {
  negative: boolean
  int: string
  frac: string
}

function parts(s: Money): Parts {
  const m = /^(-?)(\d+)(?:\.(\d+))?$/.exec(s.trim())
  if (!m) return { negative: false, int: '0', frac: '' }
  return { negative: m[1] === '-', int: m[2].replace(/^0+(?=\d)/, ''), frac: m[3] ?? '' }
}

const group = (int: string) => int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')

/** 원 단위 금액. 원 아래는 버린다 (표시만). "69781505.8833" → "69,781,505" */
export function won(s: Money | null | undefined): string {
  if (s == null) return '-'
  const p = parts(s)
  const zero = /^0*$/.test(p.int)
  return `${p.negative && !zero ? '-' : ''}${group(p.int)}`
}

/** 부호를 붙인 원 단위 금액. 손익에 쓴다. "1234.5" → "+1,234" */
export function signedWon(s: Money | null | undefined): string {
  if (s == null) return '-'
  const w = won(s)
  return w.startsWith('-') || w === '0' ? w : `+${w}`
}

/** 가격. 소수가 있으면 둘째 자리까지 (버림). "53133.3333" → "53,133.33" */
export function price(s: Money | null | undefined): string {
  if (s == null) return '-'
  const p = parts(s)
  const frac = p.frac.slice(0, 2).replace(/0+$/, '')
  return `${p.negative ? '-' : ''}${group(p.int)}${frac ? `.${frac}` : ''}`
}

/** 비율 문자열을 퍼센트로 — 소수점만 두 칸 옮긴다 (둘째 자리 아래 버림). "-0.3022" → "-30.22%" */
export function ratioPct(s: Money | null | undefined): string {
  if (s == null) return '-'
  const p = parts(s)
  const frac = p.frac.padEnd(4, '0')
  const intPart = (p.int + frac.slice(0, 2)).replace(/^0+(?=\d)/, '')
  const rest = frac.slice(2, 4)
  const zero = /^0+$/.test(intPart + rest)
  return `${zero ? '' : p.negative ? '-' : '+'}${group(intPart)}.${rest}%`
}

/** 보기 전용 등락률. from → to 가 몇 % 움직였나 */
export function changePct(from: number, to: number): string {
  if (!from) return '-'
  const v = ((to - from) / from) * 100
  const fixed = Math.abs(v) < 0.005 ? '0.00' : v.toFixed(2)
  return `${v > 0 && fixed !== '0.00' ? '+' : ''}${fixed}%`
}

/** 부호에 맞는 글자 색 (상승 빨강 / 하락 파랑) */
export function toneOf(n: number | string | null | undefined): string {
  const v = typeof n === 'string' ? Number(n) : (n ?? 0)
  if (v > 0) return 'text-up'
  if (v < 0) return 'text-down'
  return 'text-muted'
}

/** 남은 시간 m:ss */
export function clock(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000))
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

// ── 증거금 상한 (정수 원 단위, BigInt) ──

const SCALE = 10n ** 8n

function scaled(s: Money): bigint {
  const p = parts(s)
  const v = BigInt(p.int) * SCALE + BigInt((p.frac + '00000000').slice(0, 8))
  return p.negative ? -v : v
}

/** 현금의 pct% (원 단위 내림) */
export function fractionOf(cash: Money, pct: number): string {
  const v = (scaled(cash) * BigInt(pct)) / 100n / SCALE
  return (v < 0n ? 0n : v).toString()
}

/**
 * 수수료까지 감안한 증거금 상한의 최대치 (원 단위 내림, 1원 여유).
 * 실제 증거금 × (1 + 배율 × 수수료율) ≤ 현금 이어야 체결된다 (§3 M5). 판단은 서버가 다시 한다.
 */
export function maxMargin(cash: Money, leverage: number, feeRate: Money): string {
  const denom = SCALE + BigInt(leverage) * scaled(feeRate)
  const v = (scaled(cash) * SCALE) / denom / SCALE - 1n
  return (v < 0n ? 0n : v).toString()
}

/** 입력란의 숫자만 남긴다 */
export function digitsOnly(s: string): string {
  return s.replace(/[^\d]/g, '').replace(/^0+(?=\d)/, '')
}
