package com.moneygame.user;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 닉네임으로 입장한다. 처음이면 등록, 아니면 기존 사용자 (2026-10-02 결정). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    public record EnterRequest(String nickname) {
    }

    @PostMapping
    public UserService.User enter(@RequestBody EnterRequest request) {
        return users.registerOrGet(request.nickname());
    }
}
