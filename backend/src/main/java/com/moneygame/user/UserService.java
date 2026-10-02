package com.moneygame.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 닉네임 = 사용자 (2026-10-02 결정). 비밀번호는 없다.
 *
 * 처음 쓰는 닉네임이면 users 에 등록하고, 이미 있으면 그 사용자를 돌려준다.
 * DB 쓰기는 가입 순간뿐이다 — 게임 중에는 부르지 않는다 (§1.2, §5).
 *
 * 닉네임 비교는 DB collation(utf8mb4_0900_ai_ci)을 따른다. 대소문자를 구분하지 않는다.
 */
@Service
public class UserService {

    public static final int MAX_NICKNAME_LENGTH = 20;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public record User(long id, String nickname) {

        /** 엔진(GameSession)은 사용자 ID 를 문자열로 다룬다. */
        public String userId() {
            return Long.toString(id);
        }
    }

    /**
     * 트랜잭션으로 묶지 않는다. 동시 가입으로 유니크 제약이 깨지면 그 트랜잭션은 롤백 전용이 되어
     * 같은 트랜잭션에서 다시 읽을 수 없다. 저장소 호출마다 각자의 트랜잭션을 쓴다.
     */
    public User registerOrGet(String rawNickname) {
        String nickname = normalize(rawNickname);
        return users.findByNickname(nickname)
                .map(UserService::toUser)
                .orElseGet(() -> insert(nickname));
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return users.findById(id).map(UserService::toUser)
                .orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다: " + id));
    }

    private User insert(String nickname) {
        try {
            return toUser(users.saveAndFlush(new UserEntity(nickname, LocalDateTime.now(KST))));
        } catch (DataIntegrityViolationException e) {
            // 같은 닉네임이 동시에 들어왔다. 먼저 들어간 쪽을 쓴다
            return users.findByNickname(nickname).map(UserService::toUser).orElseThrow(() -> e);
        }
    }

    /** 앞뒤 공백을 걷고, 1 ~ 20자, 제어 문자 없음. */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("닉네임을 입력하세요");
        }
        String nickname = raw.strip();
        if (nickname.codePointCount(0, nickname.length()) > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("닉네임은 " + MAX_NICKNAME_LENGTH + "자 이하입니다");
        }
        if (nickname.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("닉네임에 쓸 수 없는 문자가 있습니다");
        }
        return nickname;
    }

    private static User toUser(UserEntity e) {
        return new User(e.getId(), e.getNickname());
    }
}
