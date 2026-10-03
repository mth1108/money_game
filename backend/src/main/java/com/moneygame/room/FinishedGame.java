package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.scenario.Scenario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 끝난 판 한 개. GameRecorder(M8) 가 DB 에 옮겨 담는다.
 *
 * @param roomCode   진행 중에 쓰던 메모리 방 ID
 * @param startedAt  KST
 * @param finishedAt KST
 * @param players    참가 순. 봇은 userId 가 null 이다 (2026-10-03 결정)
 * @param result     엔진의 최종 순위와 체결 내역
 */
public record FinishedGame(String roomCode,
                           GameMode mode,
                           BigDecimal seedMoney,
                           int totalTicks,
                           Scenario scenario,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           List<Player> players,
                           GameResult result) {

    /** @param playerKey 엔진의 플레이어 ID. 사람은 users.id 문자열, 봇은 bot-N */
    public record Player(String playerKey, Long userId, String nickname, boolean bot) {
    }
}
