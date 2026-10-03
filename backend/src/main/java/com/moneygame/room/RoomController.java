package com.moneygame.room;

import com.moneygame.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 방 REST. (CLAUDE.md §3 M6)
 *
 * 주문은 여기 없다 — ORDER 는 WebSocket 메시지다 (M7).
 * userId 를 본문으로 받는다. 닉네임 = 사용자이고 비밀번호가 없으므로 인증도 없다 (2026-10-02 결정).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;
    private final UserService users;
    private final RoomProperties properties;

    public RoomController(RoomService rooms, UserService users, RoomProperties properties) {
        this.rooms = rooms;
        this.users = users;
        this.properties = properties;
    }

    /**
     * @param seedMoney 선택. 비우면 1억 원. 정밀도를 지키려면 문자열로 보낸다 (§1.5)
     * @param bots      선택. 존버 봇 수 0 ~ 3, 비우면 0 (M9)
     * @param ticks     선택. 판 길이(틱). GET /api/rooms/options 의 allowedTicks 중 하나, 비우면 기본값
     */
    public record CreateRequest(Long userId, GameMode mode, Integer maxPlayers, Long scenarioId, BigDecimal seedMoney,
                                Integer bots, Integer ticks) {
    }

    /** 방 만들기 화면이 고를 수 있는 값 (모드별 배율, 판 길이, 인원·봇 한도, 기본 시드머니). */
    @GetMapping("/options")
    public RoomOptions options() {
        return RoomOptions.of(properties);
    }

    public record UserRequest(Long userId) {
    }

    public record ReadyRequest(Long userId, Boolean ready) {
    }

    @GetMapping
    public List<RoomView> list() {
        return rooms.list();
    }

    @PostMapping
    public RoomView create(@RequestBody CreateRequest request) {
        UserService.User user = user(request.userId());
        return rooms.create(user.userId(), user.nickname(),
                RoomSettings.of(request.mode(), request.maxPlayers(), request.scenarioId(), request.seedMoney(),
                        request.bots(), request.ticks(), properties.defaultTicks()));
    }

    @GetMapping("/{roomId}")
    public RoomView view(@PathVariable String roomId) {
        return rooms.view(roomId);
    }

    @PostMapping("/{roomId}/join")
    public RoomView join(@PathVariable String roomId, @RequestBody UserRequest request) {
        UserService.User user = user(request.userId());
        return rooms.join(roomId, user.userId(), user.nickname());
    }

    @PostMapping("/{roomId}/leave")
    public RoomView leave(@PathVariable String roomId, @RequestBody UserRequest request) {
        return rooms.leave(roomId, user(request.userId()).userId());
    }

    @PostMapping("/{roomId}/ready")
    public RoomView ready(@PathVariable String roomId, @RequestBody ReadyRequest request) {
        return rooms.ready(roomId, user(request.userId()).userId(), request.ready() == null || request.ready());
    }

    private UserService.User user(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 가 필요합니다. 먼저 POST /api/users 로 닉네임을 등록하세요");
        }
        return users.get(userId);
    }
}
