package com.moneygame.room;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 방 운영 설정.
 *
 * @param tickMillis              틱 주기. 1틱 = 1초 (CLAUDE.md §7). 테스트에서만 줄인다
 * @param finishedRetentionMillis 끝난 방을 결과 조회용으로 남겨 두는 시간. 지나면 메모리에서 지운다
 * @param callTimeoutMillis       요청이 방의 실행 흐름에서 처리되기를 기다리는 최대 시간
 */
@ConfigurationProperties(prefix = "room")
public record RoomProperties(long tickMillis, long finishedRetentionMillis, long callTimeoutMillis) {

    public RoomProperties {
        if (tickMillis <= 0 || finishedRetentionMillis < 0 || callTimeoutMillis <= 0) {
            throw new IllegalArgumentException("room.* 설정이 잘못됐습니다");
        }
    }
}
