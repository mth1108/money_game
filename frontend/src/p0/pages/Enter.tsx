// 닉네임 입장. 처음 쓰는 닉네임이면 서버가 등록한다 (닉네임 = 사용자, 2026-10-02 결정).
import { useState } from 'react'
import { api } from '../../api/client'
import type { User } from '../../api/types'

export function Enter({ onEnter }: { onEnter: (user: User) => void }) {
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      onEnter(await api.enter(nickname))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        닉네임 <input value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} autoFocus />
      </label>{' '}
      <button type="submit">입장</button>
      {error && <p>오류: {error}</p>}
    </form>
  )
}
