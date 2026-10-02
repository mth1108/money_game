package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.scenario.Scenario;

/**
 * 끝난 판의 결과와 실제 종목 공개. (CLAUDE.md §3 M8 이 생기기 전 임시 — §9)
 * M8 이 생기면 DB 에 저장하고 ResultController 가 조회한다.
 */
public record RoomResult(String roomId, GameResult result, Scenario scenario) {
}
