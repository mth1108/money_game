// 닉네임으로 입장 (닉네임 = 사용자, 2026-10-02 결정). 처음 쓰는 닉네임이면 등록된다.
import { useState } from 'react'
import { api } from '../api/client'
import type { User } from '../api/types'
import { Button, ErrorText, inputClass } from '../components/ui'

const RULES = [
  ['실제 과거 시세', '종목 이름과 날짜는 가린 채 A · B · C 로만 보여줍니다. 끝나면 공개합니다.'],
  ['1초 = 캔들 1개', '하루치(분봉) 또는 1년치(일봉) 시세가 몇 분 안에 압축 재생됩니다.'],
  ['배율과 청산', '배율을 걸면 손익이 커지고, 증거금만큼 잃으면 강제 청산됩니다.'],
  ['총자산 순위', '끝날 때 남은 자산으로 순위를 가립니다. 존버 봇을 이겨 보세요.'],
] as const

export function Enter({ onEnter }: { onEnter: (user: User) => void }) {
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      onEnter(await api.enter(nickname.trim()))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto grid max-w-4xl gap-8 py-8 md:grid-cols-2 md:py-16">
      <div>
        <p className="text-sm font-semibold text-brand">실제 시세 · 압축 재생 · 실시간 대결</p>
        <h1 className="mt-2 text-4xl font-black leading-tight tracking-tight">
          과거 차트로 겨루는
          <br />
          <span className="text-up">짧은</span> 투자 게임
        </h1>
        <ul className="mt-8 space-y-4">
          {RULES.map(([title, body]) => (
            <li key={title} className="flex gap-3">
              <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand" />
              <div>
                <div className="font-semibold">{title}</div>
                <div className="text-sm text-muted">{body}</div>
              </div>
            </li>
          ))}
        </ul>
      </div>
      <form onSubmit={submit} className="self-center rounded-2xl border border-line bg-panel p-6">
        <h2 className="text-lg font-bold">닉네임으로 시작하기</h2>
        <p className="mt-1 text-sm text-muted">
          닉네임은 처음 만든 브라우저에서만 쓸 수 있습니다. 같은 브라우저로 다시 오면 전적이 이어집니다.
        </p>
        <input
          className={`${inputClass} mt-5 py-3 text-base`}
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="닉네임"
          maxLength={20}
          autoFocus
        />
        <Button type="submit" variant="primary" size="lg" className="mt-3 w-full" disabled={busy || nickname.trim() === ''}>
          입장
        </Button>
        <div className="mt-3">
          <ErrorText>{error}</ErrorText>
        </div>
      </form>
    </div>
  )
}
