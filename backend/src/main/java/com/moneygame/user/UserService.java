package com.moneygame.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 닉네임 = 사용자 (2026-10-02 결정). 비밀번호는 없다.
 *
 * 닉네임은 처음 만든 브라우저에 묶인다 (2026-10-09, CLAUDE.md §8). 등록할 때 비밀 토큰을 발급하고,
 * 브라우저는 그 토큰을 저장해 두었다가 다음 입장에 함께 보낸다. 토큰이 없거나 다르면 「이미 사용 중인 닉네임」이다.
 * 토큰 원문은 저장하지 않는다 — SHA-256 해시만 둔다.
 *
 * DB 쓰기는 가입(·토큰 없는 옛 닉네임을 가져갈 때) 순간뿐이다 — 게임 중에는 부르지 않는다 (§1.2, §5).
 * 닉네임 비교는 DB collation(utf8mb4_0900_ai_ci)을 따른다. 대소문자를 구분하지 않는다.
 */
@Service
public class UserService {

    public static final int MAX_NICKNAME_LENGTH = 20;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 토큰 바이트 수. base64url 로 43자가 된다 */
    private static final int TOKEN_BYTES = 32;

    static final String TAKEN = "이미 사용 중인 닉네임입니다. 이 닉네임을 만든 브라우저에서 들어오거나 다른 닉네임을 쓰세요";

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
     * 입장 결과.
     *
     * @param token 이번에 새로 발급한 토큰. 브라우저가 저장해 두고 다음 입장에 보낸다. 기존 토큰으로 들어왔으면 null
     */
    public record Entered(User user, String token) {
    }

    /**
     * 닉네임으로 입장한다.
     * <ul>
     *   <li>처음 쓰는 닉네임 — 등록하고 토큰을 발급한다</li>
     *   <li>토큰이 없는 옛 닉네임 (2026-10-09 이전 가입) — 처음 들어온 브라우저가 가져간다. 토큰을 발급한다</li>
     *   <li>토큰이 맞으면 그 사용자. 없거나 다르면 IllegalStateException (409)</li>
     * </ul>
     *
     * 트랜잭션으로 묶지 않는다. 동시 가입으로 유니크 제약이 깨지면 그 트랜잭션은 롤백 전용이 되어
     * 같은 트랜잭션에서 다시 읽을 수 없다. 저장소 호출마다 각자의 트랜잭션을 쓴다.
     *
     * @param token 이 브라우저가 이 닉네임으로 받아 둔 토큰. 없으면 null
     */
    public Entered enter(String rawNickname, String token) {
        String nickname = normalize(rawNickname);
        return users.findByNickname(nickname)
                .map(existing -> enterExisting(existing, token))
                .orElseGet(() -> insert(nickname, token));
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return users.findById(id).map(UserService::toUser)
                .orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다: " + id));
    }

    private Entered enterExisting(UserEntity existing, String token) {
        if (existing.getTokenHash() == null) {
            // 옛 닉네임 — 먼저 온 브라우저가 가져간다. 동시에 두 곳이 오면 한 곳만 성공한다
            String issued = newToken();
            if (users.claim(existing.getId(), hash(issued)) == 1) {
                return new Entered(toUser(existing), issued);
            }
            existing = users.findById(existing.getId()).orElseThrow();
        }
        if (token != null && matches(existing.getTokenHash(), token)) {
            return new Entered(toUser(existing), null);
        }
        throw new IllegalStateException(TAKEN);
    }

    private Entered insert(String nickname, String token) {
        String issued = newToken();
        try {
            UserEntity saved = users.saveAndFlush(new UserEntity(nickname, hash(issued), LocalDateTime.now(KST)));
            return new Entered(toUser(saved), issued);
        } catch (DataIntegrityViolationException e) {
            // 같은 닉네임이 동시에 들어왔다. 먼저 들어간 쪽의 것이다 — 이쪽 토큰으로는 들어갈 수 없다
            return users.findByNickname(nickname).map(existing -> enterExisting(existing, token)).orElseThrow(() -> e);
        }
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }

    /** 걸리는 시간으로 해시를 맞혀 보지 못하게 고정 시간 비교를 쓴다 */
    private static boolean matches(String storedHash, String token) {
        return MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII));
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
