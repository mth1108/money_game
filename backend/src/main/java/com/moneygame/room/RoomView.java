package com.moneygame.room;

import java.util.List;

/**
 * 방의 현재 모습. 방 목록·대기실·M7 의 ROOM_STATE 재료다.
 * 시나리오의 실제 종목·기간은 담지 않는다 — 게임 중에는 라벨만 보인다 (§3 M3).
 *
 * @param labels   판이 시작된 뒤에만 채워진다
 * @param tickIndex 판 시작 전에는 -1
 */
public record RoomView(String id,
                       RoomStatus status,
                       GameMode mode,
                       int maxPlayers,
                       String hostUserId,
                       List<Participant> participants,
                       List<String> labels,
                       int tickIndex,
                       int totalTicks) {

    public record Participant(String userId, String nickname, boolean ready) {
    }
}
