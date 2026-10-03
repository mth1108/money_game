package com.moneygame.room;

import com.moneygame.bot.Bot;
import com.moneygame.engine.GameResult;
import com.moneygame.engine.GameSession;
import com.moneygame.scenario.LoadedScenario;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * 방 하나. (CLAUDE.md §3 M6)
 *
 * ┌─ §1.6 동시성 ──────────────────────────────────────────────────┐
 * │ 아래 가변 상태는 이 방의 executor 스레드에서만 읽고 쓴다.          │
 * │ 입장·준비·주문·틱이 모두 같은 단일 스레드 큐로 들어가므로            │
 * │ 한 방의 상태 변경은 자동으로 직렬이 된다. 방 간 락은 없다.          │
 * └──────────────────────────────────────────────────────────────┘
 */
final class Room {

    final String id;
    final RoomSettings settings;
    final Instant createdAt = Instant.now();
    final ScheduledExecutorService executor;

    // ── executor 스레드 전용 ──
    String hostUserId;
    final Map<String, Participant> participants = new LinkedHashMap<>();
    RoomStatus status = RoomStatus.WAITING;
    LoadedScenario scenario;
    GameSession session;
    GameStartInfo startInfo;
    ScheduledFuture<?> ticker;
    /** 활동 없는 대기방 닫기 예약. 활동할 때마다 다시 잡는다 */
    ScheduledFuture<?> waitingExpiry;
    /** KST. 판 시작 시각 (M8 기록용) */
    LocalDateTime startedAt;
    GameResult result;
    /** M8 이 저장한 결과 ID. 저장 실패면 null */
    Long resultId;

    static final class Participant {
        final String userId;
        final String nickname;
        /** 봇이면 그 전략. 사람이면 null */
        final Bot bot;
        boolean ready;
        /** 대기 중 연결이 끊겨 예약된 자동 퇴장. 다시 접속하면 취소한다 */
        ScheduledFuture<?> pendingLeave;

        Participant(String userId, String nickname) {
            this(userId, nickname, null);
        }

        /** 봇은 항상 준비 상태다 (2026-10-03 결정). */
        Participant(String userId, String nickname, Bot bot) {
            this.userId = userId;
            this.nickname = nickname;
            this.bot = bot;
            this.ready = bot != null;
        }

        boolean isBot() {
            return bot != null;
        }
    }

    Room(String id, RoomSettings settings) {
        this.id = id;
        this.settings = settings;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "room-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    /** executor 스레드에서만 부른다. */
    RoomView view() {
        List<RoomView.Participant> list = new ArrayList<>();
        for (Participant p : participants.values()) {
            list.add(new RoomView.Participant(p.userId, p.nickname, p.ready, p.isBot()));
        }
        return new RoomView(id, status, settings.mode(), settings.maxPlayers(), hostUserId, List.copyOf(list),
                session == null ? List.of() : session.symbolLabels(),
                session == null ? -1 : session.tickIndex(),
                settings.ticks());
    }
}
