package com.moneygame.room;

import com.moneygame.engine.GameResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 재접속한 참가자에게 다시 보낼 방의 현재 모습. 방 스레드 안에서 한 번에 만든다 — 틱과 섞이지 않는다.
 * (CLAUDE.md §9-8)
 *
 * @param start     판 시작 정보. 대기 중이면 null
 * @param tickIndex 지금 틱. 대기 중이면 -1
 * @param playedBars 라벨별 1틱부터 지금 틱까지의 봉 — 다시 그릴 차트 재료. 대기 중이면 비어 있다
 * @param players   전원의 순간 상태 (본인 PLAYER_STATE 와 RANKING 재료)
 * @param result    끝난 판이면 최종 결과, 아니면 null
 * @param resultId  끝난 판의 결과 ID (game_rooms.id). 진행 중이거나 저장에 실패했으면 null
 */
public record ResumeInfo(RoomView view,
                         GameStartInfo start,
                         int tickIndex,
                         Map<String, List<Bar>> playedBars,
                         Map<String, BigDecimal> prices,
                         Map<String, Bar> bars,
                         Map<String, PlayerSnapshot> players,
                         GameResult result,
                         Long resultId) {
}
