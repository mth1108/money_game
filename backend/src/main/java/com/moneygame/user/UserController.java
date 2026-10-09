package com.moneygame.user;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 닉네임으로 입장한다. 처음이면 등록, 아니면 기존 사용자 (2026-10-02 결정).
 * 닉네임은 처음 만든 브라우저에 묶인다 — 토큰이 없거나 다르면 409 (2026-10-09).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    /** @param token 이 브라우저가 이 닉네임으로 받아 둔 토큰. 없으면 비운다 */
    public record EnterRequest(String nickname, String token) {
    }

    /** @param token 새로 발급했을 때만 담긴다. 브라우저가 저장해 두고 다음 입장에 보낸다 */
    public record EnterResponse(long id, String nickname, String token) {
    }

    @PostMapping
    public EnterResponse enter(@RequestBody EnterRequest request) {
        UserService.Entered entered = users.enter(request.nickname(), request.token());
        return new EnterResponse(entered.user().id(), entered.user().nickname(), entered.token());
    }
}
