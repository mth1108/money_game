package com.moneygame.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 DB 구현체 통합 테스트. 로컬 MySQL 이 필요하다 — ./gradlew integrationTest
 *
 * DB 에 실제 시세가 없어 임시 행을 넣어 검증한다 (CLAUDE.md §9).
 * 테스트마다 트랜잭션이 롤백되므로 개발 DB 에 흔적이 남지 않는다.
 *
 * ⚠️ ddl-auto 를 validate 로 못 박는다. 기본값이 바뀌어 create-drop 이 걸리면
 *    개발 DB 의 테이블이 지워진다.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("M2 DbPriceDataProvider (MySQL)")
class DbPriceDataProviderIntegrationTest {

    /** 실제 종목 코드와 겹치지 않는 임시 코드. */
    private static final String CODE = "ZZTEST";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SymbolRepository symbols;

    @Autowired
    PriceCandleRepository candles;

    @TempDir
    Path dir;

    private DbPriceDataProvider provider;
    private long symbolId;

    private static LocalDateTime at(String s) {
        return LocalDateTime.parse(s);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO symbols (code, name, market) VALUES (?, '테스트', 'KRX')", CODE);
        symbolId = jdbc.queryForObject("SELECT id FROM symbols WHERE code = ?", Long.class, CODE);
        provider = new DbPriceDataProvider(symbols, candles);
    }

    private void insert(String interval, String ts, String close) {
        jdbc.update("""
                INSERT INTO price_candles
                    (symbol_id, bar_interval, ts, open_price, high_price, low_price, close_price, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?, 100)
                """, symbolId, interval, ts, close, close, close, close);
    }

    @Test
    void 구간을_양끝_포함해_오름차순으로_돌려준다() {
        insert("1m", "2026-09-04 09:03:00", "3");
        insert("1m", "2026-09-04 09:00:00", "0");
        insert("1m", "2026-09-04 09:02:00", "2");
        insert("1m", "2026-09-04 09:01:00", "1");

        List<Candle> c = provider.getCandles(CODE, Interval.ONE_MINUTE,
                at("2026-09-04T09:01"), at("2026-09-04T09:03"));

        assertEquals(3, c.size());
        assertEquals(0, new BigDecimal("1").compareTo(c.get(0).close()));
        assertEquals(0, new BigDecimal("3").compareTo(c.get(2).close()));
    }

    @Test
    void ts_는_KST_오프셋을_붙여_돌려준다() {
        insert("1d", "2024-01-11 00:00:00", "301196");

        Candle c = provider.getCandles(CODE, Interval.ONE_DAY,
                at("2024-01-11T00:00"), at("2024-01-11T00:00")).get(0);

        assertEquals(OffsetDateTime.parse("2024-01-11T00:00+09:00"), c.timestamp());
    }

    @Test
    void 단위가_다른_봉은_섞이지_않는다() {
        insert("1d", "2026-09-04 00:00:00", "100");
        insert("1m", "2026-09-04 09:01:00", "200");

        List<Candle> d = provider.getCandles(CODE, Interval.ONE_DAY, at("2026-09-04T00:00"), at("2026-09-04T23:59"));

        assertEquals(1, d.size());
        assertEquals(0, new BigDecimal("100").compareTo(d.get(0).close()));
    }

    @Test
    void 구간에_봉이_없으면_빈_목록이다() {
        insert("1d", "2024-01-02 00:00:00", "1");

        assertTrue(provider.getCandles(CODE, Interval.ONE_DAY,
                at("2025-01-01T00:00"), at("2025-12-31T00:00")).isEmpty());
    }

    @Test
    void 없는_종목이면_예외다() {
        assertThrows(IllegalStateException.class, () -> provider.getCandles("ZZNONE", Interval.ONE_DAY,
                at("2024-01-01T00:00"), at("2024-12-31T00:00")));
    }

    @Test
    void from_이_to_보다_늦으면_예외다() {
        assertThrows(IllegalArgumentException.class, () -> provider.getCandles(CODE, Interval.ONE_DAY,
                at("2024-12-31T00:00"), at("2024-01-01T00:00")));
    }

    /**
     * M2 완료 판정 「두 구현체를 갈아 끼워도 상위 코드가 바뀌지 않는다」를 임시 데이터로 확인한다.
     * 같은 봉을 CSV 와 DB 에 넣고 같은 구간을 읽어 값이 같은지 본다.
     * DB 는 DECIMAL(18,4) 라 scale 이 달라지므로 compareTo 로 비교한다.
     */
    @Test
    void CSV_구현체와_같은_결과를_돌려준다() throws IOException {
        String[][] rows = {
                {"2026-09-04T09:01+09:00", "2026-09-04 09:01:00", "254000"},
                {"2026-09-04T09:02+09:00", "2026-09-04 09:02:00", "255000"},
                {"2026-09-04T09:03+09:00", "2026-09-04 09:03:00", "254500.5"},
        };
        StringBuilder csv = new StringBuilder("timestamp,open,high,low,close,volume\n");
        for (String[] r : rows) {
            csv.append(r[0]).append(',').append(r[2]).append(',').append(r[2]).append(',')
                    .append(r[2]).append(',').append(r[2]).append(",100\n");
            insert("1m", r[1], r[2]);
        }
        Files.writeString(dir.resolve(CODE + "_1m.csv"), csv, StandardCharsets.UTF_8);

        PriceDataProvider fromCsv = new CsvPriceDataProvider(dir);
        PriceDataProvider fromDb = provider;
        List<Candle> a = fromCsv.getCandles(CODE, Interval.ONE_MINUTE, at("2026-09-04T09:01"), at("2026-09-04T09:03"));
        List<Candle> b = fromDb.getCandles(CODE, Interval.ONE_MINUTE, at("2026-09-04T09:01"), at("2026-09-04T09:03"));

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).timestamp(), b.get(i).timestamp());
            assertEquals(0, a.get(i).open().compareTo(b.get(i).open()));
            assertEquals(0, a.get(i).high().compareTo(b.get(i).high()));
            assertEquals(0, a.get(i).low().compareTo(b.get(i).low()));
            assertEquals(0, a.get(i).close().compareTo(b.get(i).close()));
            assertEquals(0, a.get(i).volume().compareTo(b.get(i).volume()));
        }
    }
}
