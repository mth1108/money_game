-- 2026-10-09 닉네임 보호 — 닉네임을 처음 만든 브라우저에 묶는다 (CLAUDE.md §8).
--
-- 브라우저가 가진 토큰의 SHA-256 해시만 둔다. 원문은 저장하지 않는다.
-- 이미 있는 닉네임은 NULL 로 남고, 그 닉네임으로 처음 들어온 브라우저가 가져간다.
--
-- schema.sql 로 새로 만든 DB 에는 이미 들어 있다. 그 전에 만든 DB 에만 한 번 적용한다.
--   bash scripts/mysql.sh < backend/src/main/resources/db/migrations/2026-10-09_users_token.sql

ALTER TABLE users
    ADD COLUMN token_hash VARCHAR(64) NULL
        COMMENT '브라우저 토큰의 SHA-256 (hex). NULL 이면 아직 어느 브라우저도 가져가지 않았다' AFTER nickname;
