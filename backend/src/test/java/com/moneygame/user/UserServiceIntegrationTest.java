package com.moneygame.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 닉네임 = 사용자 (2026-10-02 결정). 로컬 MySQL 이 필요하다 — ./gradlew integrationTest
 * 트랜잭션 롤백으로 개발 DB 에 흔적을 남기지 않는다.
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
    void 처음_쓰는_닉네임이면_등록하고_같은_닉네임은_같은_사용자다() {
        UserService.User first = users.registerOrGet("zz테스터");
        UserService.User again = users.registerOrGet("zz테스터");
        UserService.User other = users.registerOrGet("zz다른사람");

        assertEquals(first.id(), again.id());
        assertNotEquals(first.id(), other.id());
        assertEquals("zz테스터", users.get(first.id()).nickname());
    }

    @Test
    void 앞뒤_공백은_무시하고_대소문자는_구분하지_않는다() {
        UserService.User a = users.registerOrGet("  zzTester  ");
        UserService.User b = users.registerOrGet("ZZTESTER");

        assertEquals("zzTester", a.nickname());
        assertEquals(a.id(), b.id());
    }

    @Test
    void 잘못된_닉네임은_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> users.registerOrGet("   "));
        assertThrows(IllegalArgumentException.class, () -> users.registerOrGet(null));
        assertThrows(IllegalArgumentException.class, () -> users.registerOrGet("가".repeat(21)));
        assertThrows(IllegalArgumentException.class, () -> users.registerOrGet("탭\t문자"));
        assertEquals(20, users.registerOrGet("가".repeat(20)).nickname().length());
    }

    @Test
    void 없는_사용자_ID_는_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> users.get(-1));
    }
}
