package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.OrderRequest;
import com.moneygame.engine.OrderResult;
import com.moneygame.engine.TickResult;
import com.moneygame.engine.Trade;
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
import java.util.stream.Collectors;

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
    /** 틱 2ms, 결과 보관 300ms, 끊김 유예 150ms, 대기방 만료 10초 */
    private static final RoomProperties FAST = new RoomProperties(2, 300, 3000, 150, 10_000, List.of(60, 120, 240), 240);

    private final Recorder recorder = new Recorder();
    private final Records records = new Records();
    private RoomService service;

    /** 가짜 M8. 받은 판을 모으고 101, 102, ... 을 결과 ID 로 돌려준다. fail 이면 저장 실패를 흉내 낸다 */
    static final class Records implements GameRecorder {
        final List<FinishedGame> games = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger next = new AtomicInteger(101);
        volatile boolean fail;

        @Override
        public long record(FinishedGame game) {
            if (fail) {
                throw new IllegalStateException("DB 가 내려갔다");
            }
            games.add(game);
            return next.getAndIncrement();
        }
    }

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
        return service(source, FAST, extra);
    }

    private RoomService service(ScenarioSource source, RoomProperties properties, RoomEventListener... extra) {
        List<RoomEventListener> listeners = new ArrayList<>(List.of(recorder));
        listeners.addAll(List.of(extra));
        service = new RoomService(source, records, () -> listeners, properties);
        return service;
    }

    private RoomService service() {
        return service((mode, id, minBars) -> scenario(id == null ? 1 : id));
    }

    private static RoomSettings daily(int maxPlayers, Long scenarioId) {
        return new RoomSettings(GameMode.DAILY, maxPlayers, scenarioId, RoomSettings.DEFAULT_SEED_MONEY);
    }

    /** 이벤트를 모으고, 이벤트가 어느 스레드에서 왔는지도 기록한다. */
    static final class Recorder implements RoomEventListener {
        final Map<String, List<Integer>> ticks = new ConcurrentHashMap<>();
        final Map<String, Set<String>> threads = new ConcurrentHashMap<>();
        final Map<String, GameResult> finished = new ConcurrentHashMap<>();
        final Map<String, Long> resultIds = new ConcurrentHashMap<>();
        final Map<String, CountDownLatch> done = new ConcurrentHashMap<>();
        final List<OrderResult> orders = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger started = new AtomicInteger();
        final List<GameStartInfo> starts = Collections.synchronizedList(new ArrayList<>());
        final List<RoomStatus> statuses = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onRoomChanged(RoomView room) {
            statuses.add(room.status());
        }

        CountDownLatch latch(String roomId) {
            return done.computeIfAbsent(roomId, k -> new CountDownLatch(1));
        }

        private void thread(String roomId) {
            threads.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
        }

        @Override
        public void onGameStarted(String roomId, GameStartInfo start) {
            started.incrementAndGet();
            starts.add(start);
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
        public void onGameFinished(String roomId, GameResult result, Scenario scenario, Long resultId) {
            finished.put(roomId, result);
            if (resultId != null) resultIds.put(roomId, resultId);
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
            assertEquals(1, recorder.finished.get(id).rankings().size());
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
            assertTrue(recorder.finished.get(up).rankings().get(0).totalAsset().compareTo(seed) > 0, "오른 방은 이익");
            assertTrue(recorder.finished.get(down).rankings().get(0).totalAsset().compareTo(seed) < 0, "내린 방은 손실");
            assertEquals("1", recorder.finished.get(up).rankings().get(0).userId());
            assertEquals("2", recorder.finished.get(down).rankings().get(0).userId());

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
            RoomService rooms = service((mode, sid, minBars) -> {
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
    @DisplayName("버려진 대기방 (§9-9)")
    class 버려진대기방 {

        @Test
        void 대기_중_연결이_끊기면_유예_뒤_자동_퇴장한다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            rooms.join(id, "2", "p2");

            rooms.disconnected(id, "2");
            assertEquals(2, rooms.view(id).participants().size(), "유예 중에는 그대로");
            Thread.sleep(FAST.disconnectGraceMillis() + 150);

            assertEquals(List.of("1"), rooms.view(id).participants().stream().map(RoomView.Participant::userId).toList());
        }

        @Test
        void 유예_안에_다시_들어오면_퇴장하지_않는다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            rooms.join(id, "2", "p2");

            rooms.disconnected(id, "2");
            rooms.join(id, "2", "p2");   // 재접속
            Thread.sleep(FAST.disconnectGraceMillis() + 150);

            assertEquals(2, rooms.view(id).participants().size());
        }

        @Test
        void 혼자_있던_사람이_끊기면_방이_사라진다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();

            rooms.disconnected(id, "1");
            Thread.sleep(FAST.disconnectGraceMillis() + 150);

            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }

        @Test
        void 진행_중_연결_끊김은_무시한다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);

            rooms.disconnected(id, "1");
            awaitFinish(id);

            assertEquals(1, recorder.finished.get(id).rankings().size(), "끝까지 참가자로 남는다");
        }

        @Test
        void 활동_없는_대기방은_CLOSED_를_알리고_닫힌다() throws Exception {
            RoomService rooms = service((mode, sid, minBars) -> scenario(1), new RoomProperties(2, 300, 3000, 150, 200, List.of(60, 120, 240), 240));
            String id = rooms.create("1", "p1", daily(4, null)).id();

            Thread.sleep(450);

            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
            assertEquals(RoomStatus.CLOSED, recorder.statuses.get(recorder.statuses.size() - 1));
        }

        @Test
        void 활동이_있으면_만료가_미뤄진다() throws Exception {
            RoomService rooms = service((mode, sid, minBars) -> scenario(1), new RoomProperties(2, 300, 3000, 150, 400, List.of(60, 120, 240), 240));
            String id = rooms.create("1", "p1", daily(4, null)).id();

            Thread.sleep(250);
            rooms.join(id, "2", "p2");   // 활동
            Thread.sleep(250);
            assertEquals(RoomStatus.WAITING, rooms.view(id).status(), "만료가 다시 잡혔다");

            Thread.sleep(400);
            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }
    }

    @Nested
    @DisplayName("재접속 (§9-8)")
    class 재접속 {

        @Test
        void 대기_중이면_방_모습만_준다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            List<ResumeInfo> got = new ArrayList<>();

            rooms.resume(id, "1", got::add);

            assertEquals(RoomStatus.WAITING, got.get(0).view().status());
            assertEquals(null, got.get(0).start());
            assertEquals(-1, got.get(0).tickIndex());
        }

        @Test
        void 진행_중이면_시작_정보와_현재_틱과_내_상태를_방_스레드에서_준다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);
            rooms.submitOrder(id, OrderRequest.buy("1", "A", new BigDecimal("10000000"), 2));
            List<ResumeInfo> got = new ArrayList<>();
            List<String> threads = new ArrayList<>();

            rooms.resume(id, "1", info -> {
                got.add(info);
                threads.add(Thread.currentThread().getName());
            });

            ResumeInfo info = got.get(0);
            assertEquals(List.of("A", "B"), info.start().labels());
            assertEquals(1, info.players().get("1").positions().size(), "주문한 포지션이 보인다");
            assertEquals(info.prices().get("A"), info.bars().get("A").close());
            assertEquals(List.of("room-" + id), threads);
        }

        @Test
        void 끝난_방이면_최종_결과도_준다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);
            awaitFinish(id);
            List<ResumeInfo> got = new ArrayList<>();

            rooms.resume(id, "1", got::add);

            assertEquals(240, got.get(0).tickIndex());
            assertEquals(1, got.get(0).result().rankings().size());
        }

        @Test
        void 참가자가_아니면_거부한다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(4, null)).id();
            assertThrows(IllegalArgumentException.class, () -> rooms.resume(id, "9", info -> { }));
        }
    }

    @Test
    void 방마다_시드머니를_정할_수_있다() {
        RoomService rooms = service();
        RoomSettings settings = RoomSettings.of(GameMode.DAILY, 1, null, new BigDecimal("5000000"), 0, null, 240);
        String id = rooms.create("1", "p1", settings).id();
        rooms.ready(id, "1", true);

        assertEquals(new BigDecimal("5000000"), recorder.starts.get(0).seedMoney());
        assertEquals(new BigDecimal("5000000"), recorder.starts.get(0).players().get("1").cash());
    }

    @Nested
    @DisplayName("판 길이")
    class 판길이 {

        private RoomSettings ticks(int ticks) {
            return new RoomSettings(GameMode.DAILY, 1, null, RoomSettings.DEFAULT_SEED_MONEY, 0, ticks);
        }

        /** 앞 bars 봉만 있는 짧은 시나리오 */
        private LoadedScenario shortScenario(int bars) {
            LoadedScenario full = scenario(1);
            Map<String, List<Candle>> cut = new LinkedHashMap<>();
            full.candlesByLabel().forEach((label, c) -> cut.put(label, c.subList(0, bars)));
            Scenario s = full.scenario();
            return new LoadedScenario(new Scenario(s.id(), s.title(), s.interval(), s.start(), s.end(), bars, s.symbols()),
                    cut, Map.of());
        }

        @Test
        void 고른_판_길이만큼만_돌고_멈추며_실제로_쓴_기간이_기록된다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", ticks(60)).id();
            rooms.ready(id, "1", true);

            awaitFinish(id);
            Thread.sleep(30);

            assertEquals(60, recorder.ticks.get(id).size(), "정확히 60틱");
            assertEquals(60, recorder.starts.get(0).totalTicks());
            FinishedGame g = records.games.get(0);
            assertEquals(60, g.totalTicks());
            // 가짜 시나리오는 2024-01-01 부터 하루 한 봉 -> 60틱 봉은 2024-03-01 (윤년)
            assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), g.periodStart());
            assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), g.periodEnd(), "시나리오 끝(2024-08-28)이 아니라 실제로 쓴 데까지");
        }

        @Test
        void 시작_전에도_고른_판_길이가_보인다() {
            RoomService rooms = service();
            assertEquals(120, rooms.create("1", "p1", ticks(120)).totalTicks());
        }

        @Test
        void 허용되지_않은_판_길이는_거부한다() {
            RoomService rooms = service();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> rooms.create("1", "p1", ticks(100)));
            assertTrue(e.getMessage().contains("[60, 120, 240]"), e.getMessage());
        }

        @Test
        void 시나리오에_필요한_봉_수를_알린다() {
            List<Integer> asked = new ArrayList<>();
            RoomService rooms = service((mode, sid, minBars) -> {
                asked.add(minBars);
                return scenario(1);
            });
            String id = rooms.create("1", "p1", ticks(120)).id();
            rooms.ready(id, "1", true);

            assertEquals(List.of(121), asked, "판 길이 + 1 봉");
        }

        @Test
        void 시나리오가_판_길이보다_짧으면_시작하지_않는다() {
            RoomService rooms = service((mode, sid, minBars) -> shortScenario(100));
            String id = rooms.create("1", "p1", ticks(120)).id();

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> rooms.ready(id, "1", true));

            assertTrue(e.getMessage().contains("120틱"), e.getMessage());
            assertEquals(RoomStatus.WAITING, rooms.view(id).status());
        }
    }

    @Nested
    @DisplayName("봇 (M9)")
    class 봇 {

        private RoomSettings withBots(int maxPlayers, int bots) {
            return new RoomSettings(GameMode.DAILY, maxPlayers, null, RoomSettings.DEFAULT_SEED_MONEY, bots);
        }

        @Test
        void 봇은_인원에_포함되고_항상_준비_상태다() {
            RoomService rooms = service();
            RoomView v = rooms.create("1", "p1", withBots(4, 2));

            assertEquals(List.of("1", "bot-1", "bot-2"), v.participants().stream().map(RoomView.Participant::userId).toList());
            assertEquals(List.of("존버봇1", "존버봇2"), v.participants().subList(1, 3).stream().map(RoomView.Participant::nickname).toList());
            assertTrue(v.participants().subList(1, 3).stream().allMatch(p -> p.bot() && p.ready()));
            assertFalse(v.participants().get(0).bot());

            rooms.join(v.id(), "2", "p2");   // 4/4
            assertThrows(IllegalStateException.class, () -> rooms.join(v.id(), "3", "p3"), "봇도 자리를 차지한다");
        }

        @Test
        void 사람이_준비하면_시작하고_봇은_0틱에_종목마다_균등_매수_후_끝까지_보유한다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", withBots(2, 1)).id();

            assertEquals(RoomStatus.PLAYING, rooms.ready(id, "1", true).status(), "봇은 이미 준비돼 있다");
            awaitFinish(id);

            GameResult r = recorder.finished.get(id);
            List<Trade> botTrades = r.trades().stream().filter(t -> t.userId().equals("bot-1")).toList();
            assertEquals(List.of(Trade.Kind.BUY, Trade.Kind.BUY, Trade.Kind.SETTLEMENT, Trade.Kind.SETTLEMENT),
                    botTrades.stream().map(Trade::kind).toList(), "0틱에 사고 판 종료 때까지 보유");
            assertEquals(List.of(0, 0, 240, 240), botTrades.stream().map(Trade::tickIndex).toList());
            assertTrue(botTrades.stream().allMatch(t -> t.leverage() == 1));
            assertEquals(botTrades.get(0).margin(), botTrades.get(1).margin(), "두 종목에 같은 금액");
            // 가짜 시나리오 1 은 두 종목 모두 두 배로 오른다 -> 봇이 이긴다
            assertEquals("bot-1", r.rankings().get(0).userId());
            assertEquals("존버봇", r.rankings().get(0).nickname());
        }

        @Test
        void 순위에_봇과_사람이_나란히_나온다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", withBots(2, 1)).id();
            rooms.ready(id, "1", true);
            awaitFinish(id);

            assertEquals(2, recorder.finished.get(id).rankings().size());
            assertEquals(Set.of("1", "bot-1"),
                    recorder.finished.get(id).rankings().stream().map(GameResult.Rank::userId).collect(Collectors.toSet()));
        }

        @Test
        void 기록에는_봇이_사용자_ID_없이_봇으로_표시된다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", withBots(2, 1)).id();
            rooms.ready(id, "1", true);
            awaitFinish(id);

            assertEquals(List.of(new FinishedGame.Player("1", 1L, "p1", false), new FinishedGame.Player("bot-1", null, "존버봇", true)),
                    records.games.get(0).players());
        }

        @Test
        void 사람이_모두_나가면_봇만_남은_방은_닫힌다() {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", withBots(4, 2)).id();

            rooms.leave(id, "1");

            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }

        @Test
        void 봇_수는_0에서_3_이고_사람_자리가_남아야_한다() {
            assertThrows(IllegalArgumentException.class, () -> withBots(8, 4));
            assertThrows(IllegalArgumentException.class, () -> withBots(2, 2), "만든 사람 자리가 없다");
            assertThrows(IllegalArgumentException.class, () -> withBots(4, -1));
            assertEquals(3, withBots(4, 3).bots());
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
            assertEquals(2, recorder.finished.get(id).trades().size(), "매수 + 종료 정리");
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
            RoomService rooms = service((mode, sid, minBars) -> scenario(1), broken);
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);

            awaitFinish(id);
            assertEquals(240, recorder.ticks.get(id).size());
        }

        @Test
        void 끝난_방은_보관_시간이_지나면_사라진다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);
            awaitFinish(id);

            assertEquals(RoomStatus.FINISHED, rooms.view(id).status(), "끝난 직후에는 재접속용으로 남아 있다");
            Thread.sleep(FAST.finishedRetentionMillis() + 300);
            assertThrows(NoSuchElementException.class, () -> rooms.view(id));
        }
    }

    @Nested
    @DisplayName("결과 기록 (M8)")
    class 결과기록 {

        @Test
        void 판이_끝나면_한_번_기록하고_결과_ID_를_알린다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", RoomSettings.of(GameMode.DAILY, 2, null, new BigDecimal("5000000"), 0, null, 240)).id();
            rooms.join(id, "2", "p2");
            rooms.ready(id, "1", true);
            rooms.ready(id, "2", true);
            rooms.submitOrder(id, OrderRequest.buy("1", "A", new BigDecimal("1000000"), 2));
            awaitFinish(id);

            assertEquals(1, records.games.size(), "한 판에 한 번만 기록한다");
            FinishedGame g = records.games.get(0);
            assertEquals(id, g.roomCode());
            assertEquals(GameMode.DAILY, g.mode());
            assertEquals(new BigDecimal("5000000"), g.seedMoney());
            assertEquals(240, g.totalTicks());
            assertEquals("가짜1", g.scenario().title(), "실제 종목을 공개할 시나리오가 함께 간다");
            assertFalse(g.finishedAt().isBefore(g.startedAt()));
            assertEquals(List.of(new FinishedGame.Player("1", 1L, "p1", false), new FinishedGame.Player("2", 2L, "p2", false)),
                    g.players());
            assertEquals(2, g.result().trades().size(), "매수 + 종료 정리");
            assertEquals(101L, recorder.resultIds.get(id), "GAME_END 에 실을 결과 ID");
        }

        @Test
        void 저장에_실패해도_게임은_끝나고_결과_ID_만_없다() throws Exception {
            records.fail = true;
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);

            awaitFinish(id);

            assertEquals(1, recorder.finished.get(id).rankings().size(), "종료는 알린다");
            assertFalse(recorder.resultIds.containsKey(id), "결과 ID 는 없다");
            assertEquals(RoomStatus.FINISHED, rooms.view(id).status());
        }

        @Test
        void 끝난_방에_다시_들어오면_결과_ID_도_준다() throws Exception {
            RoomService rooms = service();
            String id = rooms.create("1", "p1", daily(1, null)).id();
            rooms.ready(id, "1", true);
            awaitFinish(id);
            List<ResumeInfo> got = new ArrayList<>();

            rooms.resume(id, "1", got::add);

            assertEquals(101L, got.get(0).resultId());
        }
    }
}
