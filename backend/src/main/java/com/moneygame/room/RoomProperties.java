package com.moneygame.room;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 방 운영 설정.
 *
 * @param tickMillis              틱 주기. 1틱 = 1초 (CLAUDE.md §7). 테스트에서만 줄인다
 * @param finishedRetentionMillis 끝난 방을 재접속용으로 남겨 두는 시간. 지나면 메모리에서 지운다
 * @param callTimeoutMillis       요청이 방의 실행 흐름에서 처리되기를 기다리는 최대 시간
 * @param disconnectGraceMillis   대기 중 연결이 끊긴 참가자를 자동 퇴장시키기까지의 유예 (그 안에 다시 오면 취소)
 * @param waitingTimeoutMillis    아무 활동 없는 대기방을 닫기까지의 시간
 * @param allowedTicks            방 만들 때 고를 수 있는 판 길이(틱). 늘리려면 설정만 바꾸면 된다 —
 *                                시나리오 봉 수가 「틱 + 1」 이상이면 그 길이로 돌 수 있다
 * @param defaultTicks            판 길이를 고르지 않았을 때. allowedTicks 안에 있어야 한다
 */
@ConfigurationProperties(prefix = "room")
public record RoomProperties(long tickMillis,
                             long finishedRetentionMillis,
                             long callTimeoutMillis,
                             long disconnectGraceMillis,
                             long waitingTimeoutMillis,
                             List<Integer> allowedTicks,
                             int defaultTicks) {

    public RoomProperties {
        if (tickMillis <= 0 || finishedRetentionMillis < 0 || callTimeoutMillis <= 0
                || disconnectGraceMillis < 0 || waitingTimeoutMillis <= 0) {
            throw new IllegalArgumentException("room.* 설정이 잘못됐습니다");
        }
        if (allowedTicks == null || allowedTicks.isEmpty() || allowedTicks.stream().anyMatch(t -> t == null || t < 1)) {
            throw new IllegalArgumentException("room.allowed-ticks 는 1 이상의 틱 수 목록이어야 합니다: " + allowedTicks);
        }
        allowedTicks = allowedTicks.stream().distinct().sorted().toList();
        if (!allowedTicks.contains(defaultTicks)) {
            throw new IllegalArgumentException("room.default-ticks(" + defaultTicks + ") 가 room.allowed-ticks 에 없습니다");
        }
    }
}
