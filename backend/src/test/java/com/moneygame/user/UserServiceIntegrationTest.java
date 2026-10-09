package com.moneygame.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 닉네임 = 사용자 (2026-10-02 결정), 닉네임은 처음 만든 브라우저에 묶인다 (2026-10-09).
 * 로컬 MySQL 이 필요하다 — ./gradlew integrationTest. 트랜잭션 롤백으로 개발 DB 에 흔적을 남기지 않는다.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("사용자 (MySQL)")
class UserServiceIntegrationTest {

    @Autowired UserRepository repository;

    private UserService users;

    @BeforeEach
    void setUp() {
        users = new UserService(repository);
    }

    @Test
    void 처음_쓰는_닉네임이면_등록하고_토큰을_발급한다() {
        UserService.Entered first = users.enter("zz테스터", null);

        assertNotNull(first.token());
        assertEquals("zz테스터", users.get(first.user().id()).nickname());
        UserEntity saved = repository.findById(first.user().id()).orElseThrow();
        assertEquals(UserService.hash(first.token()), saved.getTokenHash(), "원문이 아니라 해시만 저장한다");
        assertNotEquals(first.token(), saved.getTokenHash());
    }

    @Test
    void 받은_토큰으로_다시_오면_같은_사용자이고_새_토큰은_없다() {
        UserService.Entered first = users.enter("zz테스터", null);

        UserService.Entered again = users.enter("zz테스터", first.token());

        assertEquals(first.user().id(), again.user().id());
        assertNull(again.token());
    }

    @Test
    void 토큰이_없거나_다르면_이미_사용_중이다() {
        UserService.Entered first = users.enter("zz테스터", null);

        assertThrows(IllegalStateException.class, () -> users.enter("zz테스터", null));
        assertThrows(IllegalStateException.class, () -> users.enter("zz테스터", "남의토큰"));
        assertThrows(IllegalStateException.class, () -> users.enter("zz테스터", UserService.hash(first.token())),
                "저장된 해시를 토큰으로 보내도 안 된다");
    }

    @Test
    void 다른_닉네임은_다른_사용자다() {
        UserService.Entered a = users.enter("zz테스터", null);
        UserService.Entered b = users.enter("zz다른사람", null);

        assertNotEquals(a.user().id(), b.user().id());
        assertNotEquals(a.token(), b.token());
    }

    @Test
    void 앞뒤_공백은_무시하고_대소문자는_구분하지_않는다() {
        UserService.Entered a = users.enter("  zzTester  ", null);

        UserService.Entered b = users.enter("ZZTESTER", a.token());

        assertEquals("zzTester", a.user().nickname());
        assertEquals(a.user().id(), b.user().id());
        assertThrows(IllegalStateException.class, () -> users.enter("ZZTESTER", null));
    }

    @Test
    void 토큰_없는_옛_닉네임은_처음_온_브라우저가_가져간다() {
        long id = repository.saveAndFlush(new UserEntity("zz옛사용자", null, LocalDateTime.now())).getId();

        UserService.Entered claimed = users.enter("zz옛사용자", null);

        assertEquals(id, claimed.user().id(), "전적이 이어지도록 같은 사용자다");
        assertNotNull(claimed.token());
        assertThrows(IllegalStateException.class, () -> users.enter("zz옛사용자", null), "두 번째 브라우저는 못 가져간다");
        assertNull(users.enter("zz옛사용자", claimed.token()).token());
    }

    @Test
    void 토큰은_매번_다르고_URL_에_안전한_43자다() {
        String a = UserService.newToken();
        String b = UserService.newToken();

        assertNotEquals(a, b);
        assertEquals(43, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void 잘못된_닉네임은_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> users.enter("   ", null));
        assertThrows(IllegalArgumentException.class, () -> users.enter(null, null));
        assertThrows(IllegalArgumentException.class, () -> users.enter("가".repeat(21), null));
        assertThrows(IllegalArgumentException.class, () -> users.enter("탭\t문자", null));
        assertEquals(20, users.enter("가".repeat(20), null).user().nickname().length());
    }

    @Test
    void 없는_사용자_ID_는_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> users.get(-1));
    }
}
