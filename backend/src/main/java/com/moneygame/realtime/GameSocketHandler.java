package com.moneygame.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneygame.engine.GameResult;
import com.moneygame.engine.OrderRequest;
import com.moneygame.engine.OrderResult;
import com.moneygame.engine.TickResult;
import com.moneygame.realtime.message.ClientMessage;
import com.moneygame.realtime.message.ServerMessage;
import com.moneygame.room.Bar;
import com.moneygame.room.GameStartInfo;
import com.moneygame.room.PlayerSnapshot;
import com.moneygame.room.RoomEventListener;
import com.moneygame.room.RoomProperties;
import com.moneygame.room.RoomService;
import com.moneygame.room.RoomView;
import com.moneygame.scenario.Scenario;
import com.moneygame.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 연결 관리와 메시지 송수신. (CLAUDE.md §3 M7)
 *
 * 수신: JOIN 으로 연결을 방·사용자에 묶고, READY·ORDER 를 RoomService 에 넘긴다.
 * 송신: RoomEventListener 로 방 이벤트를 받아 메시지로 바꿔 보낸다.
 *   ORDER_RESULT · PLAYER_STATE 는 본인에게만, 나머지는 방 전원에게.
 *   RANKING 에는 모든 참가자의 포지션과 배율이 들어간다 (의도된 설계).
 *   NEWS 는 참가자별 지연 없이 같은 틱 처리 안에서 전원에게 보낸다.
 *
 * 이벤트는 방 스레드에서, 수신은 웹소켓 스레드에서 온다. 한 연결로 동시에 보낼 수 있으므로
 * ConcurrentWebSocketSessionDecorator 로 감싼다. 느린 클라이언트는 버퍼 한도를 넘으면 끊긴다 —
 * 그 클라이언트 때문에 방 스레드(틱)가 멈추면 안 된다.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler implements RoomEventListener {

    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);

    /** 1틱 = 1초라 5틱마다 = 5초마다 (§3 M7 RANKING 주기) */
    static final int RANKING_EVERY_TICKS = 5;

    private static final int SEND_TIME_LIMIT_MS = 2000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final RoomService rooms;
    private final UserService users;
    private final ObjectMapper mapper;
    private final RoomProperties properties;

    /** 연결 ID -> 감싼 연결 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** 연결 ID -> 묶인 방·사용자 */
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();
    /** 방 ID -> (사용자 ID -> 연결). 한 사용자는 방마다 연결 하나 — 다시 JOIN 하면 새 연결로 바뀐다 */
    private final Map<String, Map<String, WebSocketSession>> byRoom = new ConcurrentHashMap<>();
    /** 방 ID -> 판 길이. GAME_START 때 받아 TICK 의 남은 시간을 계산한다 */
    private final Map<String, Integer> totalTicks = new ConcurrentHashMap<>();

    private record Binding(String roomId, String userId) {
    }

    public GameSocketHandler(RoomService rooms, UserService users, ObjectMapper mapper, RoomProperties properties) {
        this.rooms = rooms;
        this.users = users;
        this.mapper = mapper;
        this.properties = properties;
    }

    // ───────────────────────────── 수신 ─────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage text) {
        WebSocketSession session = sessions.getOrDefault(raw.getId(), raw);
        ClientMessage msg;
        try {
            msg = mapper.readValue(text.getPayload(), ClientMessage.class);
        } catch (JsonProcessingException e) {
            error(session, "메시지를 읽을 수 없습니다. {\"type\":\"JOIN|READY|ORDER\", ...} 형식이어야 합니다");
            return;
        }
        if (msg.type() == null) {
            error(session, "type 이 필요합니다 (JOIN | READY | ORDER)");
            return;
        }
        try {
            switch (msg.type()) {
                case JOIN -> join(session, msg);
                case READY -> {
                    Binding b = binding(session);
                    rooms.ready(b.roomId(), b.userId(), msg.ready() == null || msg.ready());
                }
                case ORDER -> order(session, msg);
            }
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            error(session, e.getMessage());
        }
    }

    private void join(WebSocketSession session, ClientMessage msg) {
        if (msg.roomId() == null || msg.userId() == null) {
            throw new IllegalArgumentException("JOIN 에는 roomId 와 userId 가 필요합니다");
        }
        UserService.User user = users.get(msg.userId());
        RoomView view = rooms.join(msg.roomId(), user.userId(), user.nickname());

        Binding previous = bindings.put(session.getId(), new Binding(view.id(), user.userId()));
        if (previous != null && !previous.roomId().equals(view.id())) {
            unbind(session.getId(), previous);
        }
        byRoom.computeIfAbsent(view.id(), k -> new ConcurrentHashMap<>()).put(user.userId(), session);
        // join 이 보낸 ROOM_STATE 는 묶기 전이라 이 연결에 닿지 않았다. 직접 보낸다
        send(session, ServerMessage.of(ServerMessage.Type.ROOM_STATE, view));
    }

    private void order(WebSocketSession session, ClientMessage msg) {
        Binding b = binding(session);
        if (msg.symbolLabel() == null || msg.action() == null) {
            throw new IllegalArgumentException("ORDER 에는 symbolLabel 과 action(BUY | SELL) 이 필요합니다");
        }
        OrderRequest order = switch (msg.action()) {
            case "BUY" -> OrderRequest.buy(b.userId(), msg.symbolLabel(), msg.margin(),
                    msg.leverage() == null ? 0 : msg.leverage());
            case "SELL" -> OrderRequest.sell(b.userId(), msg.symbolLabel());
            default -> throw new IllegalArgumentException("action 은 BUY | SELL 입니다: " + msg.action());
        };
        // 결과는 onOrderResult 로 본인에게 간다
        rooms.submitOrder(b.roomId(), order);
    }

    private Binding binding(WebSocketSession session) {
        Binding b = bindings.get(session.getId());
        if (b == null) {
            throw new IllegalStateException("먼저 JOIN 하세요");
        }
        return b;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        Binding b = bindings.remove(session.getId());
        if (b != null) {
            unbind(session.getId(), b);
        }
    }

    private void unbind(String sessionId, Binding b) {
        Map<String, WebSocketSession> members = byRoom.get(b.roomId());
        if (members != null) {
            members.computeIfPresent(b.userId(), (k, s) -> s.getId().equals(sessionId) ? null : s);
        }
    }

    // ───────────────────────────── 송신 (방 스레드) ─────────────────────────────

    @Override
    public void onRoomChanged(RoomView room) {
        broadcast(room.id(), ServerMessage.of(ServerMessage.Type.ROOM_STATE, room));
    }

    @Override
    public void onGameStarted(String roomId, GameStartInfo start) {
        totalTicks.put(roomId, start.totalTicks());
        broadcast(roomId, ServerMessage.of(ServerMessage.Type.GAME_START, new ServerMessage.GameStart(
                start.mode(), start.labels(), start.initialBars(), start.seedMoney(), start.leverages(),
                start.totalTicks(), properties.tickMillis())));
        start.players().forEach((userId, p) ->
                sendTo(roomId, userId, ServerMessage.of(ServerMessage.Type.PLAYER_STATE, p)));
    }

    @Override
    public void onTick(String roomId, TickResult tick, Map<String, Bar> bars, Map<String, PlayerSnapshot> players) {
        int total = totalTicks.getOrDefault(roomId, tick.tickIndex());
        int remaining = Math.max(0, total - tick.tickIndex());
        broadcast(roomId, ServerMessage.of(ServerMessage.Type.TICK, new ServerMessage.Tick(
                tick.tickIndex(), total, remaining, remaining * properties.tickMillis(), tick.prices(), bars)));

        players.forEach((userId, p) -> sendTo(roomId, userId, ServerMessage.of(ServerMessage.Type.PLAYER_STATE, p)));

        for (TickResult.Liquidation l : tick.liquidations()) {
            PlayerSnapshot p = players.get(l.userId());
            broadcast(roomId, ServerMessage.of(ServerMessage.Type.LIQUIDATED, new ServerMessage.Liquidated(
                    tick.tickIndex(), l.userId(), p == null ? l.userId() : p.nickname(), l.symbolLabel(),
                    l.price(), l.lostMargin(), l.fee())));
        }
        if (!tick.news().isEmpty()) {
            broadcast(roomId, ServerMessage.of(ServerMessage.Type.NEWS,
                    new ServerMessage.News(tick.tickIndex(), tick.news())));
        }
        if (tick.tickIndex() % RANKING_EVERY_TICKS == 0 || tick.finished()) {
            broadcast(roomId, ServerMessage.of(ServerMessage.Type.RANKING,
                    new ServerMessage.Ranking(tick.tickIndex(), ranking(players))));
        }
    }

    @Override
    public void onOrderResult(String roomId, String userId, OrderResult result) {
        sendTo(roomId, userId, ServerMessage.of(ServerMessage.Type.ORDER_RESULT,
                new ServerMessage.OrderResultPayload(userId, result)));
    }

    @Override
    public void onGameFinished(String roomId, GameResult result, Scenario scenario) {
        totalTicks.remove(roomId);
        broadcast(roomId, ServerMessage.of(ServerMessage.Type.GAME_END,
                new ServerMessage.GameEnd(roomId, result.rankings())));
    }

    /** 총자산 내림차순, 동점은 같은 순위 (1, 1, 3) — 엔진의 최종 순위와 같은 규칙 (§3 M4). */
    static List<ServerMessage.RankingEntry> ranking(Map<String, PlayerSnapshot> players) {
        List<PlayerSnapshot> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> b.totalAsset().compareTo(a.totalAsset()));
        List<ServerMessage.RankingEntry> list = new ArrayList<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            PlayerSnapshot p = sorted.get(i);
            if (previous == null || p.totalAsset().compareTo(previous) != 0) {
                rank = i + 1;
            }
            previous = p.totalAsset();
            list.add(new ServerMessage.RankingEntry(rank, p.userId(), p.nickname(), p.totalAsset(),
                    p.positions(), p.liquidatedCount()));
        }
        return list;
    }

    // ───────────────────────────── 보내기 ─────────────────────────────

    private void broadcast(String roomId, ServerMessage message) {
        Map<String, WebSocketSession> members = byRoom.get(roomId);
        if (members == null || members.isEmpty()) {
            return;
        }
        String json = json(message);
        for (WebSocketSession s : members.values()) {
            sendRaw(s, json);
        }
    }

    private void sendTo(String roomId, String userId, ServerMessage message) {
        Map<String, WebSocketSession> members = byRoom.get(roomId);
        WebSocketSession s = members == null ? null : members.get(userId);
        if (s != null) {
            sendRaw(s, json(message));
        }
    }

    private void send(WebSocketSession session, ServerMessage message) {
        sendRaw(session, json(message));
    }

    private void error(WebSocketSession session, String message) {
        send(session, ServerMessage.of(ServerMessage.Type.ERROR, new ServerMessage.Error(message)));
    }

    private String json(ServerMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("메시지를 JSON 으로 바꾸지 못했습니다: " + message.type(), e);
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            // 버퍼 한도를 넘긴 느린 연결 등. 방 스레드를 멈추지 않고 그 연결만 포기한다
            log.warn("메시지 전송 실패 — 연결 {}: {}", session.getId(), e.toString());
        }
    }
}
