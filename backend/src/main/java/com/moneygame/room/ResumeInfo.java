package com.moneygame.room;

import com.moneygame.engine.GameResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 재접속한 참가자에게 다시 보낼 방의 현재 모습. 방 스레드 안에서 한 번에 만든다 — 틱과 섞이지 않는다.
 * (CLAUDE.md §9-8)
 *
 * @param start     판 시작 정보. 대기 중이면 null
 * @param tickIndex 지금 틱. 대기 중이면 -1
 * @param players   전원의 순간 상태 (본인 PLAYER_STATE 와 RANKING 재료)
 * @param result    끝난 판이면 최종 결과, 아니면 null
 */
public record ResumeInfo(RoomView view,
                         GameStartInfo start,
                         int tickIndex,
                         Map<String, BigDecimal> prices,
                         Map<String, Bar> bars,
                         Map<String, PlayerSnapshot> players,
                         GameResult result) {
}
