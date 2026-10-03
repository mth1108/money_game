// 종료 — GAME_END 의 최종 순위와, M8 이 저장한 결과 상세 (CLAUDE.md §4 P0, §3 M8).
import { useState } from 'react'
import type { GameEnd as GameEndPayload } from '../socket/messages'
import { ResultView } from './ResultView'

export function GameEnd({ end, myUserId }: { end: GameEndPayload; myUserId: number }) {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <h3>게임 종료 — 최종 순위</h3>
      <ol>
        {end.rankings.map((r) => (
          <li key={r.userId}>
            {r.rank}위 {r.nickname} · 총자산 {r.totalAsset} · 수익률 {r.returnRate} (비율) · 거래 {r.tradeCount}회 · 청산{' '}
            {r.liquidatedCount}회
          </li>
        ))}
      </ol>
      {end.resultId === null ? (
        <p>결과를 저장하지 못했습니다. 순위는 위와 같습니다 (서버 로그 확인)</p>
      ) : open ? (
        <ResultView resultId={end.resultId} myUserId={myUserId} />
      ) : (
        <button onClick={() => setOpen(true)}>결과 보기 (실제 종목 공개 · 내 거래)</button>
      )}
    </div>
  )
}
