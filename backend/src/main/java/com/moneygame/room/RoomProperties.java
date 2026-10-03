package com.moneygame.room;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 방 운영 설정.
 *
 * @param tickMillis              틱 주기. 1틱 = 1초 (CLAUDE.md §7). 테스트에서만 줄인다
 * @param finishedRetentionMillis 끝난 방을 재접속·결과 확인용으로 남겨 두는 시간. 지나면 메모리에서 지운다
 * @param callTimeoutMillis       요청이 방의 실행 흐름에서 처리되기를 기다리는 최대 시간
 * @param disconnectGraceMillis   대기 중 연결이 끊긴 참가자를 자동 퇴장시키기까지의 유예 (그 안에 다시 오면 취소)
 * @param waitingTimeoutMillis    아무 활동 없는 대기방을 닫기까지의 시간
 */
@ConfigurationProperties(prefix = "room")
public record RoomProperties(long tickMillis,
                             long finishedRetentionMillis,
                             long callTimeoutMillis,
                             long disconnectGraceMillis,
                             long waitingTimeoutMillis) {

    public RoomProperties {
        if (tickMillis <= 0 || finishedRetentionMillis < 0 || callTimeoutMillis <= 0
                || disconnectGraceMillis < 0 || waitingTimeoutMillis <= 0) {
            throw new IllegalArgumentException("room.* 설정이 잘못됐습니다");
        }
    }
}
