package com.moneygame.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByNickname(String nickname);

    /**
     * 아직 아무 브라우저도 가져가지 않은 닉네임을 가져간다. 동시에 두 곳이 가져가려 하면 한 곳만 1 을 받는다.
     *
     * @return 가져갔으면 1, 이미 누가 가져갔으면 0
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.tokenHash = :tokenHash WHERE u.id = :id AND u.tokenHash IS NULL")
    int claim(@Param("id") long id, @Param("tokenHash") String tokenHash);
}
