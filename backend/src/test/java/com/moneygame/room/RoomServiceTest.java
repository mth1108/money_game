package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.OrderRequest;
import com.moneygame.engine.OrderResult;
import com.moneygame.engine.TickResult;
import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import com.moneygame.scenario.LoadedScenario;
import com.moneygame.scenario.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M6 방 관리. (CLAUDE.md §3 M6 완료 판정)
 *   - 방 하나를 만들고 시작하면 240틱이 자동으로 흐르고 정확히 멈춘다
 *   - 방 두 개가 동시에 돌아도 서로 간섭하지 않는다
 *
 * 시나리오는 가짜다 (DB·CSV 없음). 틱은 2ms 로 줄여 한 판을 0.5초 안에 돈다.
 */
@DisplayName("M6 방 관리")
class RoomServiceTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final RoomProperties FAST = new RoomProperties(2, 300, 3000);

    private final Recorder recorder = new Recorder();
    private RoomService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    /** 시나리오 id 1 = 라벨 A·B 가 10000 에서 두 배로 오른다, 2 = 반으로 내린다. */
    private static LoadedScenario scenario(long id) {
        long start = 10000;
        long end = id == 2 ? 5000 : 20000;
        Map<String, List<Candle>> candles = new LinkedHashMap<>();
        for (String label : List.of("A", "B")) {
            List<Candle> list = new ArrayList<>();
            for (int i = 0; i < 241; i++) {
                BigDecimal close = BigDecimal.valueOf(start + (end - start) * i / 240);
                list.add(new Candle(LocalDateTime.of(2024, 1, 1, 0, 0).plusDays(i).atOffset(KST),
                        close, close, close, close, BigDecimal.ONE));
            }
            candles.put(label, list);
        }
        Scenario s = new Scenario(id, "가짜" + id, Interval.ONE_DAY, LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 8, 28, 0, 0), 241,
                List.of(new Scenario.ScenarioSymbol("A", "X" + id, "가짜A"), new Scenario.ScenarioSymbol("B", "Y" + id, "가짜B")));
        return new LoadedScenario(s, candles, Map.of());
    }

    private RoomService service(ScenarioSource source, RoomEventListener... extra) {
        List<RoomEventListener> listeners = new ArrayList<>(List.of(recorder));
        listeners.addAll(List.of(extra));
        service = new RoomService(source, () -> listeners, FAST);
        return service;
    }

    private RoomService service() {
        return service((mode, id) -> scenario(id == null ? 1 : id));
    }

    private static RoomSettings daily(int maxPlayers, Long scenarioId) {
        return new RoomSettings(GameMode.DAILY, maxPlayers, scenarioId, RoomSettings.DEFAULT_SEED_MONEY);
    }

    /** 이벤트를 모으고, 이벤트가 어느 스레드에서 왔는지도 기록한다. */
    static final class Recorder implements RoomEventListener {
        final Map<String, List<Integer>> ticks = new ConcurrentHashMap<>();
        final Map<String, Set<String>> threads = new ConcurrentHashMap<>();
        final Map<String, GameResult> finished = new ConcurrentHashMap<>();
        final Map<String, CountDownLatch> done = new ConcurrentHashMap<>();
        final List<OrderResult> orders = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger started = new AtomicInteger();

        CountDownLatch latch(String roomId) {
            return done.computeIfAbsent(roomId, k -> new CountDownLatch(1));
        }

        private void thread(String roomId) {
            threads.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
        }

        @Override
        public void onGameStarted(String roomId, GameStartInfo start) {
            started.incrementAndGet();
            thread(roomId);
        }

        @Override
        public void onTick(String roomId, TickResult tick, Map<String, Bar> bars, Map<String, PlayerSnapshot> players) {
            ticks.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>())).add(tick.tickIndex());
            thread(roomId);
        }

        @Override
        public void onOrderResult(String roomId, String userId, OrderResult result) {
            orders.add(result);
            thread(roomId);
        }

        @Override
        public void onGameFinished(String roomId, GameResult result, Scenario scenario) {
            finished.put(roomId, result);
            thread(roomId);
            latch(roomId).countDown();
        }
    }

    private void awaitFinish(String roomId) throws InterruptedException {
        assertTrue(recorder.latch(roomId).await(10, TimeUnit.SECONDS), "판이 끝나지 않았다: " + roomId);
    }

    @Nested
    @DisplayName("완료 판정")
    class 완료판정 {

        @Test
        void 방_하나를_만들고_시작하면_240틱이_흐르고_정확히_멈춘다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();

            RoomView started = rooms.ready(id, "1", true);
            assertEquals(RoomStatus.PLAYING, started.status());
            assertEquals(List.of("A", "B"), started.labels());

            awaitFinish(id);
            Thread.sleep(50);   // 멈춘 뒤에도 틱이 더 오지 않는지 본다

            List<Integer> ticks = recorder.ticks.get(id);
            assertEquals(240, ticks.size(), "정확히 240틱");
            for (int i = 0; i < 240; i++) {
                assertEquals(i + 1, ticks.get(i), "틱은 1 부터 순서대로");
            }
            RoomView end = rooms.view(id);
            assertEquals(RoomStatus.FINISHED, end.status());
            assertEquals(240, end.tickIndex());
            assertEquals(1, rooms.result(id).result().rankings().size());
        }

        @Test
        void 방_두_개가_동시에_돌아도_서로_간섭하지_않는다() throws Exception {
            RoomService rooms = service();
            String up = rooms.create("1", "오름", daily(1, 1L)).id();
            String down = rooms.create("2", "내림", daily(1, 2L)).id();
            rooms.ready(up, "1", true);
            rooms.ready(down, "2", true);
            BigDecimal margin = new BigDecimal("10000000");
            assertTrue(rooms.submitOrder(up, OrderRequest.buy("1", "A", margin, 1)).accepted());
            assertTrue(rooms.submitOrder(down, OrderRequest.buy("2", "A", margin, 1)).accepted());

            awaitFinish(up);
            awaitFinish(down);

            assertEquals(240, recorder.ticks.get(up).size());
            assertEquals(240, recorder.ticks.get(down).size());
            BigDecimal seed = RoomSettings.DEFAULT_SEED_MONEY;
            assertTrue(rooms.result(up).result().rankings().get(0).totalAsset().compareTo(seed) > 0, "오른 방은 이익");
            assertTrue(rooms.result(down).result().rankings().get(0).totalAsset().compareTo(seed) < 0, "내린 방은 손실");
            assertEquals("1", rooms.result(up).result().rankings().get(0).userId());
            assertEquals("2", rooms.result(down).result().rankings().get(0).userId());

            // 방마다 자기 스레드 하나에서만 이벤트가 나온다 (§1.6)
            assertEquals(Set.of("room-" + up), recorder.threads.get(up));
            assertEquals(Set.of("room-" + down), recorder.threads.get(down));
        }
    }

    @Nested
    @DisplayName("대기실")
    class 대기실 {

        @Test
        void 전원이_준비해야_시작한다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(2, null)).id();
            rooms.join(id, "2", "p2");

            assertEquals(RoomStatus.WAITING, rooms.ready(id, "1", true).status());
            assertEquals(RoomStatus.PLAYING, rooms.ready(id, "2", true).status());
            assertEquals(1, recorder.started.get());
        }

        @Test
        void 가득_찬_방과_시작한_방에는_들어갈_수_없다() {
            RoomService rooms = service();
            String full = rooms.create("1", "p1", daily(1, null)).id();
            assertThrows(IllegalStateException.class, () -> rooms.join(full, "2", "p2"));

            String playing = rooms.create("3", "p3", daily(4, null)).id();
            rooms.ready(playing, "3", true);
            assertThrows(IllegalStateException.class, () -> rooms.join(playing, "4", "p4"));
        }

        @Test
        void 같은_사람이_다시_들어와도_한_번만_센다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            rooms.join(id, "2", "p2");
            assertEquals(2, rooms.join(id, "2", "p2").participants().size());
        }

        @Test
        void 방장이_나가면_다음_사람이_방장이_되고_마지막_사람이_나가면_방이_사라진다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            rooms.join(id, "2", "p2");

            assertEquals("2", rooms.leave(id, "1").hostUserId());
            rooms.leave(id, "2");

            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }

        @Test
        void 진행_중에는_나갈_수_없다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);
            assertThrows(IllegalStateException.class, () -> rooms.leave(id, "1"));
        }

        @Test
        void 방에_없는_사람은_준비할_수_없다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            assertThrows(IllegalArgumentException.class, () -> rooms.ready(id, "9", true));
        }

        @Test
        void 목록에는_대기_중인_방만_만든_순서로_나온다() {
            RoomService rooms = service();
            String a = rooms.create("1", "p1", daily(4, null)).id();
            String b = rooms.create("2", "p2", daily(1, null)).id();
            String c = rooms.create("3", "p3", daily(4, null)).id();
            rooms.ready(b, "2", true);

            assertEquals(List.of(a, c), rooms.list().stream().map(RoomView::id).toList());
        }

        @Test
        void 시나리오를_못_읽으면_시작하지_않고_준비가_풀린다() {
            RoomService rooms = service((mode, sid) -> {
                throw new IllegalStateException("DAILY 모드로 등록된 시나리오가 없습니다");
            });
            String id = rooms.create("1", "p1", daily(1, null)).id();

            assertThrows(IllegalStateException.class, () -> rooms.ready(id, "1", true));

            RoomView v = rooms.view(id);
            assertEquals(RoomStatus.WAITING, v.status());
            assertFalse(v.participants().get(0).ready());
        }
    }

    @Nested
    @DisplayName("게임")
    class 게임 {

        @Test
        void 주문은_방의_실행_흐름에서_처리된다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);

            OrderResult r = rooms.submitOrder(id, OrderRequest.buy("1", "A", new BigDecimal("10000000"), 2));

            assertTrue(r.accepted());
            awaitFinish(id);
            assertEquals(Set.of("room-" + id), recorder.threads.get(id));
            assertEquals(1, recorder.orders.size());
            assertEquals(2, rooms.result(id).result().trades().size(), "매수 + 종료 정리");
        }

        @Test
        void 시작_전_주문은_NOT_RUNNING_이다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            assertEquals(OrderResult.RejectReason.NOT_RUNNING,
                    rooms.submitOrder(id, OrderRequest.buy("1", "A", BigDecimal.TEN, 1)).reason());
        }

        @Test
        void 리스너가_예외를_던져도_게임은_끝까지_돈다() throws Exception {
            RoomEventListener broken = new RoomEventListener() {
                @Override
                public void onTick(String roomId, TickResult tick, Map<String, Bar> bars, Map<String, PlayerSnapshot> players) {
                    throw new RuntimeException("리스너 고장");
                }
            };
            RoomService rooms = service((mode, sid) -> scenario(1), broken);
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);

            awaitFinish(id);
            assertEquals(240, recorder.ticks.get(id).size());
        }

        @Test
        void 끝난_방은_결과를_보관하다가_시간이_지나면_사라진다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            assertThrows(IllegalStateException.class, () -> rooms.result(id), "끝나기 전에는 결과가 없다");
            rooms.ready(id, "1", true);
            awaitFinish(id);

            assertEquals("가짜1", rooms.result(id).scenario().title(), "끝나면 실제 시나리오를 공개한다");
            Thread.sleep(FAST.finishedRetentionMillis() + 300);
            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }
    }
}
