package com.moneygame.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 사용자. 닉네임 = 사용자 (2026-10-02 결정). DDL 원본은 db/schema.sql 이다. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    /** 브라우저 토큰의 SHA-256 (hex). null 이면 아직 어느 브라우저도 가져가지 않은 닉네임 (2026-10-09 이전 가입) */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserEntity() {
    }

    public UserEntity(String nickname, String tokenHash, LocalDateTime createdAt) {
        this.nickname = nickname;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
