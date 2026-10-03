package com.moneygame.room;

import com.moneygame.bot.BotContext;
import com.moneygame.bot.HoldBot;
import com.moneygame.engine.GameSession;
import com.moneygame.engine.OrderRequest;
import com.moneygame.engine.OrderResult;
import com.moneygame.engine.TickResult;
import com.moneygame.position.PlayerState;
import com.moneygame.scenario.LoadedScenario;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 방 생성 · 입퇴장 · 준비 · 게임 시작 · 틱 루프 · 종료. (CLAUDE.md §3 M6)
 *
 * 방마다 단일 스레드 ScheduledExecutorService 를 하나씩 둔다. 요청은 그 executor 에 넣고
 * 처리될 때까지 기다린다. 틱도 같은 executor 에서 돈다 — §1.6 이 구조로 지켜진다.
 *
 * 게임 규칙 계산은 하지 않는다 (M4/M5). 메시지 직렬화도 하지 않는다 (M7) — RoomEventListener 로 넘길 뿐이다.
 * 틱 루프 중에는 DB·외부 API 를 부르지 않는다 (§1.2). 시나리오는 판 시작 순간에만 읽는다.
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** 봇의 플레이어 ID 접두어. 사람의 ID 는 users.id 숫자라 겹치지 않는다 */
    static final String BOT_PREFIX = "bot-";

    private final ScenarioSource scenarios;
    private final GameRecorder recorder;
    private final Supplier<List<RoomEventListener>> listeners;
    private final RoomProperties properties;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** 리스너(M7)는 RoomService 를 주입받으므로 매번 늦게 찾는다 — 순환 의존을 피한다. */
    @Autowired
    public RoomService(ScenarioSource scenarios, GameRecorder recorder,
                       ObjectProvider<RoomEventListener> listeners, RoomProperties properties) {
        this(scenarios, recorder, () -> listeners.orderedStream().toList(), properties);
    }

    RoomService(ScenarioSource scenarios, GameRecorder recorder,
                Supplier<List<RoomEventListener>> listeners, RoomProperties properties) {
        this.scenarios = scenarios;
        this.recorder = recorder;
        this.listeners = listeners;
        this.properties = properties;
    }

    // ───────────────────────────── 대기실 ─────────────────────────────

    /** 방을 만들고 만든 사람이 바로 들어간다. */
    public RoomView create(String hostUserId, String hostNickname, RoomSettings settings) {
        Room room = newRoom(settings);
        RoomView view = call(room, () -> {
            room.participants.put(hostUserId, new Room.Participant(hostUserId, hostNickname));
            room.hostUserId = hostUserId;
            for (int i = 1; i <= settings.bots(); i++) {
                String name = settings.bots() == 1 ? HoldBot.NAME : HoldBot.NAME + i;
                room.participants.put(BOT_PREFIX + i, new Room.Participant(BOT_PREFIX + i, name, new HoldBot()));
            }
            return changed(room);
        });
        log.info("방 생성 {} — {} / 최대 {}명 / 봇 {} / 시나리오 {} / 시드 {} / 방장 {}", room.id, settings.mode(),
                settings.maxPlayers(), settings.bots(), settings.scenarioId() == null ? "무작위" : settings.scenarioId(),
                settings.seedMoney().toPlainString(), hostNickname);
        return view;
    }

    /** 대기 중인 방. 오래된 순. */
    public List<RoomView> list() {
        List<Room> all = new ArrayList<>(rooms.values());
        all.sort(Comparator.comparing(r -> r.createdAt));
        List<RoomView> views = new ArrayList<>();
        for (Room room : all) {
            try {
                RoomView v = call(room, room::view);
                if (v.status() == RoomStatus.WAITING) {
                    views.add(v);
                }
            } catch (NoSuchElementException ignored) {
                // 목록을 만드는 사이에 사라진 방
            }
        }
        return views;
    }

    public RoomView view(String roomId) {
        Room room = find(roomId);
        return call(room, room::view);
    }

    /** 대기 중이면 입장한다. 이미 참가자면 그대로 돌려준다 — 재접속이면 예약된 자동 퇴장을 취소한다. */
    public RoomView join(String roomId, String userId, String nickname) {
        Room room = find(roomId);
        return call(room, () -> {
            Room.Participant existing = room.participants.get(userId);
            if (existing != null) {
                cancelPendingLeave(existing);
                return room.view();
            }
            requireWaiting(room, "이미 시작한 방입니다");
            if (room.participants.size() >= room.settings.maxPlayers()) {
                throw new IllegalStateException("방이 가득 찼습니다: " + room.settings.maxPlayers() + "명");
            }
            room.participants.put(userId, new Room.Participant(userId, nickname));
            return changed(room);
        });
    }

    /** 대기 중에만 나갈 수 있다. 마지막 사람이 나가면 방이 사라진다. */
    public RoomView leave(String roomId, String userId) {
        Room room = find(roomId);
        return call(room, () -> {
            requireWaiting(room, "진행 중에는 나갈 수 없습니다. 남은 포지션은 종료 시 정리됩니다");
            requireParticipant(room, userId);
            return removeParticipant(room, userId, "퇴장");
        });
    }

    /** 전원이 준비되면 그 자리에서 판을 시작한다. */
    public RoomView ready(String roomId, String userId, boolean ready) {
        Room room = find(roomId);
        return call(room, () -> {
            requireWaiting(room, "이미 시작한 방입니다");
            Room.Participant p = requireParticipant(room, userId);
            p.ready = ready;
            boolean allReady = room.participants.values().stream().allMatch(x -> x.ready);
            if (!allReady) {
                return changed(room);
            }
            try {
                start(room);
            } catch (RuntimeException e) {
                p.ready = false;   // 시작하지 못했다. 다시 준비할 수 있게 되돌린다
                changed(room);
                throw e;
            }
            return room.view();
        });
    }

    /**
     * 참가자의 실시간 연결이 끊겼다 (M7 이 알린다). 대기 중이면 유예 뒤 자동으로 내보낸다.
     * 그 안에 다시 JOIN 하면 취소된다. 진행 중이면 아무것도 하지 않는다 — 포지션은 종료 시 정리된다 (§9-9).
     */
    public void disconnected(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        try {
            call(room, () -> {
                Room.Participant p = room.participants.get(userId);
                if (room.status == RoomStatus.WAITING && p != null && p.pendingLeave == null) {
                    p.pendingLeave = room.executor.schedule(() -> autoLeave(room, userId),
                            properties.disconnectGraceMillis(), TimeUnit.MILLISECONDS);
                }
                return null;
            });
        } catch (NoSuchElementException ignored) {
            // 그사이 방이 닫혔다
        }
    }

    // ───────────────────────────── 게임 ─────────────────────────────

    /** 주문을 방의 실행 흐름에 넣는다. 틱과 섞이지 않고 순서대로 처리된다 (§1.6). */
    public OrderResult submitOrder(String roomId, OrderRequest order) {
        Room room = find(roomId);
        return call(room, () -> {
            OrderResult result = room.session == null
                    ? OrderResult.reject(OrderResult.RejectReason.NOT_RUNNING, order.symbolLabel())
                    : room.session.submitOrder(order);
            fire(l -> l.onOrderResult(room.id, order.userId(), result));
            return result;
        });
    }

    /**
     * 재접속한 참가자에게 방의 현재 모습을 넘긴다 (§9-8). action 은 방 스레드 안에서 돈다 —
     * 그동안 틱이 끼어들지 않으므로, action 안에서 연결을 등록하고 다시 보내면 순서가 섞이지 않는다.
     */
    public void resume(String roomId, String userId, Consumer<ResumeInfo> action) {
        Room room = find(roomId);
        call(room, () -> {
            requireParticipant(room, userId);
            if (room.session == null) {
                action.accept(new ResumeInfo(room.view(), null, -1, Map.of(), Map.of(), Map.of(), null, null));
                return null;
            }
            int tick = room.session.tickIndex();
            Map<String, BigDecimal> prices = prices(room.scenario, tick);
            action.accept(new ResumeInfo(room.view(), room.startInfo, tick, prices, bars(room.scenario, tick),
                    snapshots(room.session, prices), room.result, room.resultId));
            return null;
        });
    }

    // ─────────────────────── executor 스레드 안에서만 ───────────────────────

    private void start(Room room) {
        LoadedScenario loaded = scenarios.load(room.settings.mode(), room.settings.scenarioId());
        int totalTicks = loaded.scenario().barCount() - 1;
        GameSession session = new GameSession(loaded.scenario().labels(), loaded.closeSeries(),
                room.settings.seedMoney(), room.settings.mode().leverages(), totalTicks, loaded.newsByTick());
        for (Room.Participant p : room.participants.values()) {
            session.addPlayer(p.userId, p.nickname);
            cancelPendingLeave(p);
        }
        session.start();
        cancel(room.waitingExpiry);

        room.scenario = loaded;
        room.session = session;
        room.status = RoomStatus.PLAYING;
        room.startedAt = LocalDateTime.now(KST);
        // 봇은 0틱에 사람과 똑같이 주문한다 (M9). 시작 정보에 봇 포지션이 담기도록 먼저 넣는다
        runBots(room, true);
        room.startInfo = new GameStartInfo(room.settings.mode(), session.symbolLabels(), bars(loaded, 0),
                session.seedMoney(), session.allowedLeverages(), totalTicks, snapshots(session, prices(loaded, 0)));

        log.info("게임 시작 {} — 시나리오 #{} / {}명 / {}틱", room.id, loaded.scenario().id(),
                room.participants.size(), totalTicks);
        changed(room);
        GameStartInfo info = room.startInfo;
        fire(l -> l.onGameStarted(room.id, info));

        long period = properties.tickMillis();
        room.ticker = room.executor.scheduleAtFixedRate(() -> tick(room), period, period, TimeUnit.MILLISECONDS);
    }

    private void tick(Room room) {
        if (room.status != RoomStatus.PLAYING) {
            return;
        }
        try {
            TickResult t = room.session.tick();
            Map<String, Bar> bars = bars(room.scenario, t.tickIndex());
            Map<String, PlayerSnapshot> players = snapshots(room.session, t.prices());
            fire(l -> l.onTick(room.id, t, bars, players));
            if (t.finished()) {
                finish(room);
            } else {
                runBots(room, false);
            }
        } catch (RuntimeException e) {
            // 예외가 새면 ScheduledExecutorService 가 조용히 틱을 멈춘다. 로그를 남기고 방을 닫는다
            log.error("틱 처리 실패 — 방 {} 를 닫습니다", room.id, e);
            room.ticker.cancel(false);
            room.status = RoomStatus.FINISHED;
            changed(room);
            close(room, "틱 처리 실패");
        }
    }

    private void finish(Room room) {
        room.ticker.cancel(false);
        room.result = room.session.finish();
        room.status = RoomStatus.FINISHED;
        room.resultId = record(room);
        log.info("게임 종료 {} — 결과 #{} / 1위 {}", room.id, room.resultId,
                room.result.rankings().isEmpty() ? "-" : room.result.rankings().get(0).nickname());
        Long resultId = room.resultId;
        fire(l -> l.onGameFinished(room.id, room.result, room.scenario.scenario(), resultId));
        changed(room);
        room.executor.schedule(() -> close(room, "결과 보관 시간 경과"),
                properties.finishedRetentionMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * M8 에 끝난 판을 넘긴다 (판 종료 시 쓰기, §1.2). 실패해도 게임은 끝난 것으로 처리한다 —
     * 결과 ID 없이 종료를 알리고, 순위는 GAME_END 로 이미 전원에게 간다.
     */
    private Long record(Room room) {
        List<FinishedGame.Player> players = new ArrayList<>();
        for (Room.Participant p : room.participants.values()) {
            // 봇은 사용자 ID 없이 봇으로 표시한다 (2026-10-03 결정)
            players.add(new FinishedGame.Player(p.userId, p.isBot() ? null : Long.valueOf(p.userId), p.nickname, p.isBot()));
        }
        FinishedGame game = new FinishedGame(room.id, room.settings.mode(), room.settings.seedMoney(),
                room.session.totalTicks(), room.scenario.scenario(), room.startedAt, LocalDateTime.now(KST),
                List.copyOf(players), room.result);
        try {
            return recorder.record(game);
        } catch (RuntimeException e) {
            log.error("결과 저장 실패 — 방 {}. 결과 ID 없이 종료를 알립니다", room.id, e);
            return null;
        }
    }

    /**
     * 봇들의 주문을 엔진에 넣는다 — 사람 주문과 같은 submitOrder 경로다 (M9 「봇은 M4 입장에서 사람과 구분되지 않는다」).
     * 봇에게는 연결이 없으므로 ORDER_RESULT 는 보내지 않는다.
     */
    private void runBots(Room room, boolean atStart) {
        GameSession session = room.session;
        Map<String, BigDecimal> prices = prices(room.scenario, session.tickIndex());
        for (Room.Participant p : room.participants.values()) {
            if (!p.isBot()) {
                continue;
            }
            BotContext ctx = new BotContext(p.userId, session.tickIndex(),
                    session.symbolLabels(), prices, session.player(p.userId).cash(), session.allowedLeverages());
            List<OrderRequest> orders = atStart ? p.bot.onStart(ctx) : p.bot.onTick(ctx);
            for (OrderRequest order : orders) {
                OrderResult r = session.submitOrder(order);
                if (!r.accepted()) {
                    log.warn("봇 주문 거부 — 방 {} / {} / {} {}: {}", room.id, p.nickname, order.action(),
                            order.symbolLabel(), r.reason());
                }
            }
        }
    }

    /** 참가자를 내보낸다. 사람이 아무도 안 남으면 방을 닫고, 방장이면 다음 사람에게 넘긴다. */
    private RoomView removeParticipant(Room room, String userId, String reason) {
        Room.Participant p = room.participants.remove(userId);
        if (p != null) {
            cancelPendingLeave(p);
        }
        String nextHost = room.participants.values().stream().filter(x -> !x.isBot()).map(x -> x.userId)
                .findFirst().orElse(null);
        if (nextHost == null) {
            close(room, "마지막 사람 " + reason);   // 봇만 남은 방은 의미가 없다
            return room.view();
        }
        if (userId.equals(room.hostUserId)) {
            room.hostUserId = nextHost;
        }
        return changed(room);
    }

    /** 대기 중 연결이 끊긴 뒤 유예가 지났다 (§9-9). */
    private void autoLeave(Room room, String userId) {
        Room.Participant p = room.participants.get(userId);
        if (room.status != RoomStatus.WAITING || p == null || p.pendingLeave == null) {
            return;   // 그사이 시작했거나, 나갔거나, 다시 접속했다
        }
        p.pendingLeave = null;
        log.info("자동 퇴장 {} — {} (연결이 끊긴 채 {}ms)", room.id, p.nickname, properties.disconnectGraceMillis());
        removeParticipant(room, userId, "자동 퇴장");
    }

    /** 활동 없이 시간이 지난 대기방을 닫는다 (§9-9). */
    private void expireWaiting(Room room) {
        if (room.status != RoomStatus.WAITING) {
            return;
        }
        room.status = RoomStatus.CLOSED;
        changed(room);
        close(room, "대기 시간 초과 (" + properties.waitingTimeoutMillis() + "ms 동안 활동 없음)");
    }

    /** 방을 목록에서 지우고 executor 를 닫는다. 지금 처리 중인 작업은 끝까지 돈다. */
    private void close(Room room, String reason) {
        rooms.remove(room.id, room);
        room.executor.shutdown();
        log.info("방 닫힘 {} — {}", room.id, reason);
    }

    /** 방 모습이 바뀌었다고 알린다. 대기 중이면 만료 예약을 다시 잡는다 — 활동이 있었다는 뜻이다. */
    private RoomView changed(Room room) {
        if (room.status == RoomStatus.WAITING && !room.executor.isShutdown()) {
            cancel(room.waitingExpiry);
            room.waitingExpiry = room.executor.schedule(() -> expireWaiting(room),
                    properties.waitingTimeoutMillis(), TimeUnit.MILLISECONDS);
        }
        RoomView view = room.view();
        fire(l -> l.onRoomChanged(view));
        return view;
    }

    private static void cancelPendingLeave(Room.Participant p) {
        cancel(p.pendingLeave);
        p.pendingLeave = null;
    }

    private static void cancel(Future<?> f) {
        if (f != null) {
            f.cancel(false);
        }
    }

    private static Map<String, Bar> bars(LoadedScenario loaded, int tickIndex) {
        Map<String, Bar> bars = new LinkedHashMap<>();
        loaded.candlesByLabel().forEach((label, candles) -> bars.put(label, Bar.of(candles.get(tickIndex))));
        return bars;
    }

    private static Map<String, BigDecimal> prices(LoadedScenario loaded, int tickIndex) {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        loaded.candlesByLabel().forEach((label, candles) -> prices.put(label, candles.get(tickIndex).close()));
        return prices;
    }

    private static Map<String, PlayerSnapshot> snapshots(GameSession session, Map<String, BigDecimal> prices) {
        Map<String, PlayerSnapshot> map = new LinkedHashMap<>();
        for (PlayerState p : session.players().values()) {
            map.put(p.userId(), PlayerSnapshot.of(p, prices));
        }
        return map;
    }

    private static void requireWaiting(Room room, String message) {
        if (room.status != RoomStatus.WAITING) {
            throw new IllegalStateException(message);
        }
    }

    private static Room.Participant requireParticipant(Room room, String userId) {
        Room.Participant p = room.participants.get(userId);
        if (p == null) {
            throw new IllegalArgumentException("방에 없는 사용자입니다: " + userId);
        }
        return p;
    }

    private void fire(Consumer<RoomEventListener> event) {
        for (RoomEventListener l : listeners.get()) {
            try {
                event.accept(l);
            } catch (RuntimeException e) {
                log.warn("방 이벤트 리스너 오류 — 게임은 계속합니다: {}", e.toString());
            }
        }
    }

    // ───────────────────────────── 공통 ─────────────────────────────

    private Room newRoom(RoomSettings settings) {
        while (true) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            Room room = new Room(id, settings);
            if (rooms.putIfAbsent(id, room) == null) {
                return room;
            }
            room.executor.shutdown();
        }
    }

    private Room find(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new NoSuchElementException("방이 없습니다: " + roomId);
        }
        return room;
    }

    /** 방의 실행 흐름에서 task 를 돌리고 결과를 기다린다. */
    private <T> T call(Room room, Callable<T> task) {
        Future<T> future;
        try {
            future = room.executor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new NoSuchElementException("방이 없습니다: " + room.id);
        }
        try {
            return future.get(properties.callTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("방이 응답하지 않습니다: " + room.id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단되었습니다", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        rooms.values().forEach(r -> r.executor.shutdownNow());
        rooms.clear();
    }
}
