package com.moneygame.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import com.moneygame.room.GameMode;
import com.moneygame.room.GameRecorder;
import com.moneygame.room.RoomService;
import com.moneygame.room.RoomSettings;
import com.moneygame.room.ScenarioSource;
import com.moneygame.scenario.LoadedScenario;
import com.moneygame.scenario.Scenario;
import com.moneygame.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * M7 완료 판정 — 「브라우저 두 개가 같은 방에 들어가 같은 TICK 을 받는다」. (CLAUDE.md §3 M7)
 *
 * 실제 서버를 띄우고 JDK 내장 WebSocket 클라이언트 두 개로 접속한다 (새 의존성 없음).
 * 시나리오와 사용자는 가짜다. 스프링 컨텍스트가 DB 연결을 만들므로 로컬 MySQL 이 필요하다 — integrationTest.
 * 틱은 10ms 로 줄인다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"room.tick-millis=10", "room.disconnect-grace-millis=300",
                "spring.jpa.hibernate.ddl-auto=validate"})
@DisplayName("M7 실시간 통신 (WebSocket)")
class GameSocketIntegrationTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @LocalServerPort int port;
    @Autowired RoomService rooms;
    @Autowired ObjectMapper mapper;
    @MockitoBean ScenarioSource scenarios;
    @MockitoBean UserService users;
    @MockitoBean GameRecorder recorder;

    private final List<Client> clients = new ArrayList<>();

    /** A 는 10000 -> 5000 으로 내린다 (배율 3 청산), B 는 10000 고정. 10틱에 뉴스 하나. */
    private static LoadedScenario scenario() {
        Map<String, List<Candle>> candles = new LinkedHashMap<>();
        for (String label : List.of("A", "B")) {
            List<Candle> list = new ArrayList<>();
            for (int i = 0; i < 241; i++) {
                BigDecimal close = label.equals("A") ? BigDecimal.valueOf(10000 - 5000L * i / 240) : BigDecimal.valueOf(10000);
                list.add(new Candle(LocalDateTime.of(2024, 1, 1, 0, 0).plusDays(i).atOffset(KST),
                        close, close, close, close, BigDecimal.ONE));
            }
            candles.put(label, list);
        }
        Scenario s = new Scenario(99, "가짜", Interval.ONE_DAY, LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 8, 28, 0, 0), 241,
                List.of(new Scenario.ScenarioSymbol("A", "X", "가짜A"), new Scenario.ScenarioSymbol("B", "Y", "가짜B")));
        return new LoadedScenario(s, candles, Map.of(10, List.of("테스트 뉴스")));
    }

    @BeforeEach
    void setUp() {
        when(scenarios.load(any(), any(), anyInt())).thenReturn(scenario());
        when(users.get(1L)).thenReturn(new UserService.User(1, "철수"));
        when(users.get(2L)).thenReturn(new UserService.User(2, "영희"));
        when(recorder.record(any())).thenReturn(777L);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(c -> c.ws.abort());
    }

    /** 받은 메시지를 모으는 WebSocket 클라이언트. 조각난 프레임은 이어 붙인다. */
    final class Client implements WebSocket.Listener {
        final List<JsonNode> messages = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch gameEnd = new CountDownLatch(1);
        final CountDownLatch gameStart = new CountDownLatch(1);
        final CountDownLatch roomState = new CountDownLatch(1);
        final CountDownLatch error = new CountDownLatch(1);
        private final StringBuilder buffer = new StringBuilder();
        WebSocket ws;

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    JsonNode n = mapper.readTree(buffer.toString());
                    messages.add(n);
                    switch (n.get("type").asText()) {
                        case "GAME_END" -> gameEnd.countDown();
                        case "GAME_START" -> gameStart.countDown();
                        case "ROOM_STATE" -> roomState.countDown();
                        case "ERROR" -> error.countDown();
                        default -> { }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        void send(String json) {
            ws.sendText(json, true).join();
        }

        List<JsonNode> of(String type) {
            synchronized (messages) {
                return messages.stream().filter(m -> m.get("type").asText().equals(type)).toList();
            }
        }
    }

    private Client connect() {
        Client c = new Client();
        c.ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), c).join();
        clients.add(c);
        return c;
    }

    private static void await(CountDownLatch latch, String what) throws InterruptedException {
        assertTrue(latch.await(15, TimeUnit.SECONDS), what + " 을(를) 받지 못했다");
    }

    @Test
    void 두_클라이언트가_같은_방에_들어가_같은_TICK_을_받는다() throws Exception {
        String roomId = rooms.create("1", "철수", new RoomSettings(GameMode.DAILY, 2, null,
                RoomSettings.DEFAULT_SEED_MONEY)).id();
        Client c1 = connect();
        Client c2 = connect();
        c1.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":1}");
        c2.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":2}");
        await(c1.roomState, "c1 ROOM_STATE");
        await(c2.roomState, "c2 ROOM_STATE");

        c1.send("{\"type\":\"READY\",\"ready\":true}");
        c2.send("{\"type\":\"READY\",\"ready\":true}");
        await(c1.gameStart, "c1 GAME_START");
        // 배율 3 매수 -> A 가 청산가(진입가의 2/3) 밑으로 내려가며 강제 청산된다
        c1.send("{\"type\":\"ORDER\",\"symbolLabel\":\"A\",\"action\":\"BUY\",\"margin\":\"10000000\",\"leverage\":3}");

        await(c1.gameEnd, "c1 GAME_END");
        await(c2.gameEnd, "c2 GAME_END");

        // ── 같은 TICK ──
        List<JsonNode> t1 = c1.of("TICK");
        List<JsonNode> t2 = c2.of("TICK");
        assertEquals(240, t1.size());
        assertEquals(t1, t2, "두 클라이언트가 받은 TICK 이 완전히 같다");
        assertEquals(1, t1.get(0).at("/payload/tickIndex").asInt());
        assertEquals(240, t1.get(239).at("/payload/tickIndex").asInt());
        assertEquals(0, t1.get(239).at("/payload/remainingTicks").asInt());
        assertTrue(t1.get(0).at("/payload/prices/A").isTextual(), "가격은 문자열로 온다 (§1.5)");
        assertFalse(t1.get(0).at("/payload/bars/A").has("timestamp"), "캔들에 시각을 넣지 않는다");

        // ── 본인에게만 ──
        assertTrue(c1.of("PLAYER_STATE").stream().allMatch(m -> m.at("/payload/userId").asText().equals("1")));
        assertTrue(c2.of("PLAYER_STATE").stream().allMatch(m -> m.at("/payload/userId").asText().equals("2")));
        assertEquals(241, c1.of("PLAYER_STATE").size(), "시작 1번 + 틱마다 240번");
        assertEquals(1, c1.of("ORDER_RESULT").size());
        assertTrue(c1.of("ORDER_RESULT").get(0).at("/payload/result/accepted").asBoolean());
        assertEquals(0, c2.of("ORDER_RESULT").size(), "남의 주문 결과는 받지 않는다");

        // ── 전원에게 ──
        assertEquals(1, c1.of("GAME_START").size());
        assertEquals(1, c2.of("GAME_START").size());
        assertEquals("10000", c2.of("GAME_START").get(0).at("/payload/initialBars/A/close").asText());
        assertEquals(48, c1.of("RANKING").size(), "5틱마다 = 240 / 5");
        assertEquals(48, c2.of("RANKING").size());
        JsonNode liq = c2.of("LIQUIDATED").get(0);
        assertEquals("철수", liq.at("/payload/nickname").asText(), "남의 청산도 전원이 받는다");
        assertEquals("A", liq.at("/payload/symbolLabel").asText());
        assertEquals(1, c1.of("NEWS").size());
        assertEquals(1, c2.of("NEWS").size());
        assertEquals(10, c2.of("NEWS").get(0).at("/payload/tickIndex").asInt());
        assertEquals("테스트 뉴스", c2.of("NEWS").get(0).at("/payload/headlines/0").asText());

        // ── 종료 ──
        JsonNode end = c2.of("GAME_END").get(0);
        assertEquals(777L, end.at("/payload/resultId").asLong(), "M8 이 저장한 결과 ID");
        assertEquals(roomId, end.at("/payload/roomId").asText());
        assertEquals("2", end.at("/payload/rankings/0/userId").asText(), "거래하지 않은 영희가 1위");
        assertEquals(1, c1.of("GAME_END").size());
    }

    @Test
    void 진행_중에_다시_접속하면_게임_상태를_순서대로_다시_받는다() throws Exception {
        String roomId = rooms.create("1", "철수", new RoomSettings(GameMode.DAILY, 1, null,
                RoomSettings.DEFAULT_SEED_MONEY)).id();
        Client first = connect();
        first.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":1}");
        await(first.roomState, "ROOM_STATE");
        first.send("{\"type\":\"READY\",\"ready\":true}");
        await(first.gameStart, "GAME_START");
        first.send("{\"type\":\"ORDER\",\"symbolLabel\":\"B\",\"action\":\"BUY\",\"margin\":\"10000000\",\"leverage\":2}");
        Thread.sleep(400);
        first.ws.abort();   // 새로고침처럼 연결이 끊긴다

        Client again = connect();
        again.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":1}");
        await(again.gameEnd, "GAME_END");

        List<String> types;
        synchronized (again.messages) {
            types = again.messages.stream().map(m -> m.get("type").asText()).toList();
        }
        assertEquals(List.of("ROOM_STATE", "GAME_START", "TICK", "PLAYER_STATE", "RANKING"), types.subList(0, 5),
                "재접속하면 현재 모습을 이 순서로 먼저 받는다");
        assertEquals("PLAYING", again.messages.get(0).at("/payload/status").asText());
        JsonNode me = again.messages.get(3);
        assertEquals("B", me.at("/payload/positions/0/symbolLabel").asText(), "끊기기 전에 산 포지션이 보인다");

        List<Integer> ticks = again.of("TICK").stream().map(m -> m.at("/payload/tickIndex").asInt()).toList();
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i) > ticks.get(i - 1), "틱이 거꾸로 오지 않는다: " + ticks);
        }
        assertEquals(240, ticks.get(ticks.size() - 1));
    }

    @Test
    void 대기_중_연결이_끊긴_참가자는_유예_뒤_빠진다() throws Exception {
        String roomId = rooms.create("1", "철수", new RoomSettings(GameMode.DAILY, 2, null,
                RoomSettings.DEFAULT_SEED_MONEY)).id();
        Client c1 = connect();
        Client c2 = connect();
        c1.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":1}");
        c2.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":2}");
        await(c2.roomState, "c2 ROOM_STATE");

        c2.ws.abort();   // 탭을 닫았다
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            List<JsonNode> states = c1.of("ROOM_STATE");
            if (!states.isEmpty() && states.get(states.size() - 1).at("/payload/participants").size() == 1) {
                break;
            }
            Thread.sleep(50);
        }

        List<JsonNode> states = c1.of("ROOM_STATE");
        JsonNode last = states.get(states.size() - 1);
        assertEquals(1, last.at("/payload/participants").size(), "영희가 빠졌다");
        assertEquals("1", last.at("/payload/participants/0/userId").asText());
    }

    /** M9 완료 판정 — 「싱글 모드에서 봇과 순위가 나란히 표시된다」 */
    @Test
    void 혼자_하는_판에서_봇과_순위가_나란히_나온다() throws Exception {
        String roomId = rooms.create("1", "철수", new RoomSettings(GameMode.DAILY, 2, null,
                RoomSettings.DEFAULT_SEED_MONEY, 1)).id();
        Client c = connect();
        c.send("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"userId\":1}");
        await(c.roomState, "ROOM_STATE");
        JsonNode bot = c.of("ROOM_STATE").get(0).at("/payload/participants/1");
        assertEquals("존버봇", bot.at("/nickname").asText());
        assertTrue(bot.at("/bot").asBoolean() && bot.at("/ready").asBoolean(), "봇은 처음부터 준비 상태");

        c.send("{\"type\":\"READY\",\"ready\":true}");
        await(c.gameEnd, "GAME_END");

        JsonNode firstRanking = c.of("RANKING").get(0).at("/payload/rankings");
        List<String> names = new ArrayList<>();
        firstRanking.forEach(r -> names.add(r.at("/nickname").asText()));
        assertEquals(2, names.size());
        assertTrue(names.containsAll(List.of("철수", "존버봇")), "순위에 봇과 사람이 함께: " + names);
        JsonNode botEntry = names.indexOf("존버봇") == 0 ? firstRanking.get(0) : firstRanking.get(1);
        assertEquals(2, botEntry.at("/positions").size(), "봇은 두 종목을 모두 들고 있다");
        assertEquals(1, botEntry.at("/positions/0/leverage").asInt());

        JsonNode end = c.of("GAME_END").get(0).at("/payload/rankings");
        assertEquals(2, end.size());
    }

    @Test
    void JOIN_전에_보낸_요청과_잘못된_메시지는_ERROR_로_답한다() throws Exception {
        Client c = connect();
        c.send("{\"type\":\"READY\"}");
        await(c.error, "ERROR");
        assertEquals("먼저 JOIN 하세요", c.of("ERROR").get(0).at("/payload/message").asText());

        c.send("이건 JSON 이 아니다");
        c.send("{\"type\":\"JOIN\",\"roomId\":\"nope\",\"userId\":1}");
        long deadline = System.currentTimeMillis() + 5000;
        while (c.of("ERROR").size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(3, c.of("ERROR").size());
        assertEquals("방이 없습니다: nope", c.of("ERROR").get(2).at("/payload/message").asText());
    }
}
