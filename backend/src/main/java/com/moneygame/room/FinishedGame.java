package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.scenario.Scenario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 끝난 판 한 개. GameRecorder(M8) 가 DB 에 옮겨 담는다.
 *
 * @param roomCode    진행 중에 쓰던 메모리 방 ID
 * @param totalTicks  이 판의 길이 (방 설정에서 고른 값)
 * @param periodStart KST. 실제로 쓴 첫 봉(0틱)의 시각
 * @param periodEnd   KST. 실제로 쓴 마지막 봉(totalTicks 틱)의 시각. 짧은 판이면 시나리오 끝보다 이르다
 * @param startedAt   KST. 판을 시작한 시각
 * @param finishedAt  KST. 판이 끝난 시각
 * @param players     참가 순. 봇은 userId 가 null 이다 (2026-10-03 결정)
 * @param result      엔진의 최종 순위와 체결 내역
 */
public record FinishedGame(String roomCode,
                           GameMode mode,
                           BigDecimal seedMoney,
                           int totalTicks,
                           Scenario scenario,
                           LocalDateTime periodStart,
                           LocalDateTime periodEnd,
                           LocalDateTime startedAt,
                           LocalDateTime finishedAt,
                           List<Player> players,
                           GameResult result) {

    /** @param playerKey 엔진의 플레이어 ID. 사람은 users.id 문자열, 봇은 bot-N */
    public record Player(String playerKey, Long userId, String nickname, boolean bot) {
    }
}
