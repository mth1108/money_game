// P1 공통 UI 조각. 색은 index.css 의 토큰만 쓴다.
import type { ButtonHTMLAttributes, ReactNode } from 'react'

export function Panel({
  title,
  right,
  children,
  className = '',
}: {
  title?: ReactNode
  right?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`rounded-xl border border-line bg-panel ${className}`}>
      {(title || right) && (
        <header className="flex items-center justify-between gap-2 border-b border-line px-4 py-2.5">
          <h2 className="text-sm font-semibold text-ink">{title}</h2>
          {right && <div className="text-xs text-muted">{right}</div>}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

type Variant = 'primary' | 'up' | 'down' | 'ghost' | 'subtle'

const VARIANT: Record<Variant, string> = {
  primary: 'bg-brand text-white hover:brightness-110',
  up: 'bg-up text-white hover:brightness-110',
  down: 'bg-down text-white hover:brightness-110',
  ghost: 'border border-line text-ink hover:bg-panel-2',
  subtle: 'bg-panel-2 text-ink hover:bg-line',
}

export function Button({
  variant = 'ghost',
  size = 'md',
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md' | 'lg' }) {
  const pad = size === 'sm' ? 'px-2.5 py-1 text-xs' : size === 'lg' ? 'px-5 py-3 text-base' : 'px-3.5 py-2 text-sm'
  return (
    <button
      {...rest}
      className={`rounded-lg font-semibold transition disabled:cursor-not-allowed disabled:opacity-40 ${pad} ${VARIANT[variant]} ${className}`}
    />
  )
}

/** 여러 값 중 하나를 고르는 버튼 묶음 */
export function Segmented<T extends string | number>({
  value,
  options,
  onChange,
  disabled,
  size = 'md',
}: {
  value: T
  options: { value: T; label: ReactNode }[]
  onChange: (v: T) => void
  disabled?: boolean
  size?: 'sm' | 'md'
}) {
  return (
    <div className="inline-flex flex-wrap gap-1 rounded-lg bg-bg p-1">
      {options.map((o) => (
        <button
          key={String(o.value)}
          type="button"
          disabled={disabled}
          onClick={() => onChange(o.value)}
          className={`rounded-md font-semibold transition disabled:opacity-40 ${size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3 py-1.5 text-sm'} ${
            o.value === value ? 'bg-panel-2 text-ink shadow ring-1 ring-line' : 'text-muted hover:text-ink'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function Tag({ children, tone = 'muted' }: { children: ReactNode; tone?: 'muted' | 'brand' | 'warn' | 'ok' | 'up' | 'down' }) {
  const cls = {
    muted: 'bg-panel-2 text-muted',
    brand: 'bg-brand/15 text-brand',
    warn: 'bg-warn/15 text-warn',
    ok: 'bg-ok/15 text-ok',
    up: 'bg-up/15 text-up',
    down: 'bg-down/15 text-down',
  }[tone]
  return <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-semibold ${cls}`}>{children}</span>
}

/** 이름표 + 큰 숫자 */
export function Stat({ label, value, sub, className = '' }: { label: ReactNode; value: ReactNode; sub?: ReactNode; className?: string }) {
  return (
    <div className={className}>
      <div className="text-xs text-muted">{label}</div>
      <div className="text-lg font-bold leading-tight">{value}</div>
      {sub && <div className="text-xs">{sub}</div>}
    </div>
  )
}

export function Field({ label, children, hint }: { label: ReactNode; children: ReactNode; hint?: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-medium text-muted">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-faint">{hint}</span>}
    </label>
  )
}

export const inputClass =
  'w-full rounded-lg border border-line bg-bg px-3 py-2 text-sm text-ink outline-none placeholder:text-faint focus:border-brand'

/** 오류 한 줄 */
export function ErrorText({ children }: { children: ReactNode }) {
  if (!children) return null
  return <p className="rounded-lg bg-up/10 px-3 py-2 text-sm text-up">{children}</p>
}
