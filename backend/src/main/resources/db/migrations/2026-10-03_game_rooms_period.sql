-- 2026-10-03 판 길이 선택 — game_rooms 에 「실제로 쓴 시세 구간」을 더한다.
--
-- 짧은 판(60·120틱)은 시나리오 앞부분만 쓴다. 결과 화면에서 기간을 정확히 공개하려면
-- 시나리오 기간이 아니라 실제로 쓴 구간이 있어야 한다.
--
-- schema.sql 로 새로 만든 DB 에는 이미 들어 있다. 그 전에 만든 DB 에만 한 번 적용한다.
--   bash scripts/mysql.sh < backend/src/main/resources/db/migrations/2026-10-03_game_rooms_period.sql

ALTER TABLE game_rooms
    ADD COLUMN period_start DATETIME NULL COMMENT 'KST. 실제로 쓴 첫 봉(0틱)' AFTER total_ticks,
    ADD COLUMN period_end   DATETIME NULL COMMENT 'KST. 실제로 쓴 마지막 봉. 짧은 판이면 시나리오 끝보다 이르다' AFTER period_start;

-- 이전 판은 모두 240틱(시나리오 전체)이었다
UPDATE game_rooms g
    JOIN scenarios s ON s.id = g.scenario_id
SET g.period_start = s.start_ts,
    g.period_end   = s.end_ts
WHERE g.period_start IS NULL;

ALTER TABLE game_rooms
    MODIFY period_start DATETIME NOT NULL COMMENT 'KST. 실제로 쓴 첫 봉(0틱)',
    MODIFY period_end   DATETIME NOT NULL COMMENT 'KST. 실제로 쓴 마지막 봉. 짧은 판이면 시나리오 끝보다 이르다';
