-- 머니게임 시세 스키마
--
-- ddl-auto 는 validate 또는 none 입니다. update 금지 (CLAUDE.md §5).
-- 이 파일이 유일한 DDL 원본이며 Hibernate 가 테이블을 만들지 않습니다.
--
-- 이번 단계는 시세 관련 테이블만 만듭니다.
-- 시나리오(scenarios / scenario_symbols / news_events)는 M3,
-- 게임 결과(game_rooms / game_participants / trades)는 M8 에서 추가합니다.
--
-- 진행 중 상태를 담는 테이블은 만들지 않습니다 (§1.3).
-- user_balance, holdings, current_positions 같은 테이블이 생기면 매 거래마다
-- UPDATE 가 발생해 설계가 무너집니다. 진행 중 상태는 GameSession 메모리에만 둡니다.

CREATE DATABASE IF NOT EXISTS moneygame
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE moneygame;

-- ────────────────────────────────────────────────────────────────
-- 종목
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS symbols (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    code    VARCHAR(20)  NOT NULL COMMENT 'KRX 6자리 또는 US 티커',
    name    VARCHAR(100) NOT NULL,
    market  VARCHAR(20)  NOT NULL COMMENT 'KRX | US',
    PRIMARY KEY (id),
    UNIQUE KEY uk_symbols_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 시세 캔들
--
-- PK 순서가 중요합니다 (§5). 조회는 항상 「한 종목의 특정 구간」이므로
-- symbol_id 가 선두여야 인덱스를 탑니다. 별도 인덱스는 두지 않습니다.
--
-- ts 는 KST 기준으로 저장합니다. 타임존을 담지 않으므로 이 규약이 전부입니다.
--   1m : 봉 종료 시각. 구간은 [ts - 1분, ts)
--   1d : 거래일. 시각은 00:00:00 고정
-- 미국 종목을 넣게 되면 이 규약을 다시 봐야 합니다.
--
-- bar_interval 은 '1m' 또는 '1d' 입니다. interval 은 MySQL 예약어라
-- 쓸 때마다 백틱이 필요하고 JPA 매핑도 지저분해지므로 이름을 바꿨습니다.
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS price_candles (
    symbol_id    BIGINT        NOT NULL,
    bar_interval VARCHAR(4)    NOT NULL COMMENT '1m | 1d',
    ts           DATETIME      NOT NULL COMMENT 'KST. 1m 은 봉 종료 시각, 1d 는 거래일 자정',
    open_price   DECIMAL(18,4) NOT NULL,
    high_price   DECIMAL(18,4) NOT NULL,
    low_price    DECIMAL(18,4) NOT NULL,
    close_price  DECIMAL(18,4) NOT NULL,
    volume       DECIMAL(20,0) NOT NULL,
    PRIMARY KEY (symbol_id, bar_interval, ts),
    CONSTRAINT fk_price_candles_symbol
        FOREIGN KEY (symbol_id) REFERENCES symbols (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 수집 이력
--
-- 「이 구간을 이미 받았나」를 price_candles 조회만으로는 판단할 수 없습니다.
-- 거래일이 아닌 날은 원래 데이터가 없어서 「안 받음」과 「받았는데 없음」이
-- 구분되지 않습니다. 그 상태로는 68분짜리 수집을 중복으로 돌릴 위험이 있습니다.
--
-- 배치 실행 이력이지 진행 중 상태가 아니므로 §1.3 에 해당하지 않습니다.
-- 쓰기 시점은 수집 배치가 끝나는 순간 한 번뿐입니다.
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS collect_logs (
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    symbol_id    BIGINT     NOT NULL,
    bar_interval VARCHAR(4) NOT NULL COMMENT '1m | 1d',
    from_ts      DATETIME   NOT NULL COMMENT 'KST. 이 실행이 받은 가장 오래된 봉',
    to_ts        DATETIME   NOT NULL COMMENT 'KST. 이 실행이 받은 가장 최근 봉',
    bar_count    INT        NOT NULL,
    collected_at DATETIME   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_collect_logs_lookup (symbol_id, bar_interval, from_ts, to_ts),
    CONSTRAINT fk_collect_logs_symbol
        FOREIGN KEY (symbol_id) REFERENCES symbols (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 개발용 종목 시드
-- ────────────────────────────────────────────────────────────────
INSERT INTO symbols (code, name, market) VALUES
    ('005930', '삼성전자',     'KRX'),
    ('000660', 'SK하이닉스',   'KRX'),
    ('247540', '에코프로비엠', 'KRX')
ON DUPLICATE KEY UPDATE name = VALUES(name), market = VALUES(market);
