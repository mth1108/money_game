package com.moneygame.room;

import com.moneygame.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public RoomController(RoomService rooms, UserService users) {
        this.rooms = rooms;
        this.users = users;
    }

    public record CreateRequest(Long userId, GameMode mode, Integer maxPlayers, Long scenarioId) {
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
                RoomSettings.of(request.mode(), request.maxPlayers(), request.scenarioId()));
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

    /** 끝난 판의 결과와 실제 종목명. M8 이 생기기 전 임시 (CLAUDE.md §9). */
    @GetMapping("/{roomId}/result")
    public RoomResult result(@PathVariable String roomId) {
        return rooms.result(roomId);
    }

    private UserService.User user(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 가 필요합니다. 먼저 POST /api/users 로 닉네임을 등록하세요");
        }
        return users.get(userId);
    }
}
