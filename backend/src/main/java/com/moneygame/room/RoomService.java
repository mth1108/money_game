package com.moneygame.room;

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

    private final ScenarioSource scenarios;
    private final Supplier<List<RoomEventListener>> listeners;
    private final RoomProperties properties;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** 리스너(M7)는 RoomService 를 주입받으므로 매번 늦게 찾는다 — 순환 의존을 피한다. */
    @Autowired
    public RoomService(ScenarioSource scenarios, ObjectProvider<RoomEventListener> listeners,
                       RoomProperties properties) {
        this(scenarios, () -> listeners.orderedStream().toList(), properties);
    }

    RoomService(ScenarioSource scenarios, Supplier<List<RoomEventListener>> listeners, RoomProperties properties) {
        this.scenarios = scenarios;
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
            return room.view();
        });
        log.info("방 생성 {} — {} / 최대 {}명 / 시나리오 {} / 방장 {}", room.id, settings.mode(),
                settings.maxPlayers(), settings.scenarioId() == null ? "무작위" : settings.scenarioId(), hostNickname);
        fire(l -> l.onRoomChanged(view));
        return view;
    }

    /** 대기 중인 방. 오래된 순. */
    public List<RoomView> list() {
        List<Room> waiting = new ArrayList<>(rooms.values());
        waiting.sort(Comparator.comparing(r -> r.createdAt));
        List<RoomView> views = new ArrayList<>();
        for (Room room : waiting) {
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

    public RoomView join(String roomId, String userId, String nickname) {
        Room room = find(roomId);
        return call(room, () -> {
            if (room.participants.containsKey(userId)) {
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
            room.participants.remove(userId);
            if (room.participants.isEmpty()) {
                close(room, "마지막 참가자 퇴장");
                return room.view();
            }
            if (userId.equals(room.hostUserId)) {
                room.hostUserId = room.participants.keySet().iterator().next();
            }
            return changed(room);
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

    /** 끝난 판의 결과. M8 이 생기기 전까지는 메모리에만 있다 (CLAUDE.md §9). */
    public RoomResult result(String roomId) {
        Room room = find(roomId);
        return call(room, () -> {
            if (room.status != RoomStatus.FINISHED || room.result == null) {
                throw new IllegalStateException("아직 끝나지 않은 방입니다: " + room.status);
            }
            return new RoomResult(room.id, room.result, room.scenario.scenario());
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
        }
        session.start();

        room.scenario = loaded;
        room.session = session;
        room.status = RoomStatus.PLAYING;

        GameStartInfo info = new GameStartInfo(room.settings.mode(), session.symbolLabels(), bars(loaded, 0),
                session.seedMoney(), session.allowedLeverages(), totalTicks, snapshots(session, prices(loaded, 0)));
        log.info("게임 시작 {} — 시나리오 #{} / {}명 / {}틱", room.id, loaded.scenario().id(),
                room.participants.size(), totalTicks);
        changed(room);
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
        log.info("게임 종료 {} — 1위 {}", room.id,
                room.result.rankings().isEmpty() ? "-" : room.result.rankings().get(0).nickname());
        fire(l -> l.onGameFinished(room.id, room.result, room.scenario.scenario()));
        changed(room);
        room.executor.schedule(() -> close(room, "결과 보관 시간 경과"),
                properties.finishedRetentionMillis(), TimeUnit.MILLISECONDS);
    }

    /** 방을 목록에서 지우고 executor 를 닫는다. 지금 처리 중인 작업은 끝까지 돈다. */
    private void close(Room room, String reason) {
        rooms.remove(room.id, room);
        room.executor.shutdown();
        log.info("방 닫힘 {} — {}", room.id, reason);
    }

    private RoomView changed(Room room) {
        RoomView view = room.view();
        fire(l -> l.onRoomChanged(view));
        return view;
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
