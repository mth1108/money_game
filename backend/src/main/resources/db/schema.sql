-- 머니게임 스키마
--
-- ddl-auto 는 validate 또는 none 입니다. update 금지 (CLAUDE.md §5).
-- 이 파일이 유일한 DDL 원본이며 Hibernate 가 테이블을 만들지 않습니다.
-- 여러 번 실행해도 됩니다 (CREATE TABLE IF NOT EXISTS, 시드는 upsert).
--
-- 시세(symbols / price_candles / collect_logs)는 M1, 사용자(users)는 M6,
-- 시나리오(scenarios / scenario_symbols / news_events)는 M3,
-- 게임 결과(game_rooms / game_participants / trades)는 M8 에서 만들었습니다.
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
-- 사용자
--
-- 닉네임 = 사용자 (2026-10-02 결정). 처음 쓰는 닉네임이면 여기 등록하고, 같은 닉네임은 같은
-- 사용자로 봅니다. 비밀번호는 없습니다. 쓰기는 가입(닉네임 첫 사용) 순간뿐입니다 (§5).
-- 게임 중 상태(현금·포지션)는 담지 않습니다 (§1.3).
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    nickname   VARCHAR(20) NOT NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_nickname (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 시나리오 (M3)
--
-- 한 판에 쓰는 「종목 + 기간 + 단위」 묶음입니다. 운영자가 관리 커맨드로 고른 구간만 들어갑니다.
-- 시세 자체는 담지 않습니다. 판 시작 시 PriceDataProvider 로 [start_ts, end_ts] 를 읽습니다.
--
-- bar_count 는 241 입니다 — 시작가 1봉 + 240틱 (CLAUDE.md §3 M4 「시세와 틱의 관계」).
-- 분봉은 하루 안, 09:01 ~ 15:20 에서만 자릅니다 (§3 M3).
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS scenarios (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(100) NOT NULL,
    bar_interval VARCHAR(4)   NOT NULL COMMENT '1m | 1d',
    start_ts     DATETIME     NOT NULL COMMENT 'KST. 첫 봉(0틱 시작가)의 ts',
    end_ts       DATETIME     NOT NULL COMMENT 'KST. 마지막 봉(240틱)의 ts',
    bar_count    INT          NOT NULL COMMENT '241 = 시작가 1봉 + 240틱',
    created_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 시나리오 ↔ 종목. 게임 중에는 라벨(A~D)만 보이고 실제 종목명은 결과 화면에서 공개합니다.
-- 한 시나리오에 1 ~ 4 종목 (2026-10-02 결정).
CREATE TABLE IF NOT EXISTS scenario_symbols (
    scenario_id BIGINT     NOT NULL,
    label       VARCHAR(1) NOT NULL COMMENT 'A | B | C | D',
    symbol_id   BIGINT     NOT NULL,
    PRIMARY KEY (scenario_id, label),
    UNIQUE KEY uk_scenario_symbols_symbol (scenario_id, symbol_id),
    CONSTRAINT fk_scenario_symbols_scenario
        FOREIGN KEY (scenario_id) REFERENCES scenarios (id),
    CONSTRAINT fk_scenario_symbols_symbol
        FOREIGN KEY (symbol_id) REFERENCES symbols (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 뉴스 이벤트. 지금은 비어 있습니다 — 생성 방식은 S6 에서 정합니다 (2026-10-02 결정).
CREATE TABLE IF NOT EXISTS news_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    scenario_id BIGINT       NOT NULL,
    tick_index  INT          NOT NULL COMMENT '발생 틱. 1 ~ 240',
    headline    VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_news_events_scenario (scenario_id, tick_index),
    CONSTRAINT fk_news_events_scenario
        FOREIGN KEY (scenario_id) REFERENCES scenarios (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 게임 결과 (M8)
--
-- 판이 끝나는 순간 한 트랜잭션에 INSERT 만 합니다 (2026-10-03 결정, §1.2 종료 시 쓰기).
-- 시작하지 못한 방은 남기지 않고, 상태 UPDATE 도 없습니다 (§1.3 — 진행 중 상태 테이블 금지).
-- 금액은 엔진 계산 그대로 소수 8자리까지 담습니다 (증거금 역산이 scale 8 이다, §1.5).
-- ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS game_rooms (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    room_code   VARCHAR(16)   NOT NULL COMMENT '진행 중에 쓰던 메모리 방 ID. 재사용될 수 있어 유니크가 아니다',
    scenario_id BIGINT        NOT NULL,
    mode        VARCHAR(10)   NOT NULL COMMENT 'DAILY | MINUTE',
    seed_money  DECIMAL(24,8) NOT NULL,
    total_ticks INT           NOT NULL,
    started_at  DATETIME      NOT NULL COMMENT 'KST',
    finished_at DATETIME      NOT NULL COMMENT 'KST',
    PRIMARY KEY (id),
    KEY idx_game_rooms_finished (finished_at),
    CONSTRAINT fk_game_rooms_scenario
        FOREIGN KEY (scenario_id) REFERENCES scenarios (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 참가자별 최종 결과. 봇도 순위와 자산을 남기되 user_id 는 비우고 is_bot 으로 표시합니다 (2026-10-03 결정).
CREATE TABLE IF NOT EXISTS game_participants (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    game_id          BIGINT        NOT NULL,
    player_key       VARCHAR(40)   NOT NULL COMMENT '엔진의 플레이어 ID. 사람은 users.id, 봇은 bot-N',
    user_id          BIGINT        NULL     COMMENT '봇이면 NULL',
    nickname         VARCHAR(20)   NOT NULL COMMENT '그 판 당시의 이름',
    is_bot           BIT(1)        NOT NULL,
    final_rank       INT           NOT NULL COMMENT '동점은 같은 순위 (1, 1, 3)',
    final_asset      DECIMAL(24,8) NOT NULL,
    return_rate      DECIMAL(12,4) NOT NULL COMMENT '비율. -1 = -100%',
    trade_count      INT           NOT NULL,
    liquidated_count INT           NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_participants_player (game_id, player_key),
    KEY idx_game_participants_user (user_id, game_id),
    CONSTRAINT fk_game_participants_game
        FOREIGN KEY (game_id) REFERENCES game_rooms (id),
    CONSTRAINT fk_game_participants_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 체결 내역. kind 하나로 §5 의 「매수/매도」(BUY 여부)와 「청산 여부」(LIQUIDATION 여부)를 함께 담습니다.
CREATE TABLE IF NOT EXISTS trades (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    game_id        BIGINT        NOT NULL,
    participant_id BIGINT        NOT NULL,
    tick_index     INT           NOT NULL COMMENT '0 = 시작 직후',
    symbol_label   VARCHAR(1)    NOT NULL,
    symbol_id      BIGINT        NOT NULL COMMENT '라벨이 가린 실제 종목',
    kind           VARCHAR(12)   NOT NULL COMMENT 'BUY | SELL | LIQUIDATION | SETTLEMENT(판 종료 정리)',
    quantity       BIGINT        NOT NULL,
    price          DECIMAL(18,4) NOT NULL,
    margin         DECIMAL(24,8) NOT NULL,
    leverage       INT           NOT NULL,
    fee            DECIMAL(24,8) NOT NULL COMMENT '실제로 부과된 금액',
    PRIMARY KEY (id),
    KEY idx_trades_game (game_id, tick_index),
    CONSTRAINT fk_trades_game
        FOREIGN KEY (game_id) REFERENCES game_rooms (id),
    CONSTRAINT fk_trades_participant
        FOREIGN KEY (participant_id) REFERENCES game_participants (id),
    CONSTRAINT fk_trades_symbol
        FOREIGN KEY (symbol_id) REFERENCES symbols (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ────────────────────────────────────────────────────────────────
-- 개발용 종목 시드
--
-- 배치 적재(PriceCandleBatchWriter)와 같은 행 별칭 문법을 씁니다.
-- VALUES() 함수는 MySQL 8.0.20 부터 폐기 예정입니다.
-- ────────────────────────────────────────────────────────────────
INSERT INTO symbols (code, name, market) VALUES
    ('005930', '삼성전자',     'KRX'),
    ('000660', 'SK하이닉스',   'KRX'),
    ('247540', '에코프로비엠', 'KRX')
AS incoming
ON DUPLICATE KEY UPDATE name = incoming.name, market = incoming.market;
