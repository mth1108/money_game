package com.moneygame.marketdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
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
 * M2 CSV 구현체 검증. (CLAUDE.md §3 M2)
 *
 * PriceDataProvider 규약 — KST, 양끝 포함, 시간 오름차순 — 을 지키는지 본다.
 */
@DisplayName("M2 CsvPriceDataProvider")
class CsvPriceDataProviderTest {

    private static final String HEADER = "timestamp,open,high,low,close,volume";

    @TempDir
    Path dir;

    private static LocalDateTime at(String s) {
        return LocalDateTime.parse(s);
    }

    private void write(String fileName, String... rows) throws IOException {
        // Windows 에서 쓴 파일처럼 CRLF 로 만든다
        String body = HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n";
        Files.writeString(dir.resolve(fileName), body, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("구간과 정렬")
    class 구간과정렬 {

        @Test
        void 파일_순서와_무관하게_오름차순으로_돌려준다() throws IOException {
            write("005930_1m.csv",
                    "2026-09-04T09:03+09:00,3,3,3,3,30",
                    "2026-09-04T09:01+09:00,1,1,1,1,10",
                    "2026-09-04T09:02+09:00,2,2,2,2,20");

            List<Candle> c = new CsvPriceDataProvider(dir).getCandles("005930", Interval.ONE_MINUTE,
                    at("2026-09-04T00:00"), at("2026-09-04T23:59"));

            assertEquals(3, c.size());
            assertEquals(OffsetDateTime.parse("2026-09-04T09:01+09:00"), c.get(0).timestamp());
            assertEquals(OffsetDateTime.parse("2026-09-04T09:03+09:00"), c.get(2).timestamp());
        }

        @Test
        void from_과_to_는_양끝을_포함한다() throws IOException {
            write("005930_1m.csv",
                    "2026-09-04T09:00+09:00,0,0,0,0,0",
                    "2026-09-04T09:01+09:00,1,1,1,1,10",
                    "2026-09-04T09:02+09:00,2,2,2,2,20",
                    "2026-09-04T09:03+09:00,3,3,3,3,30");

            List<Candle> c = new CsvPriceDataProvider(dir).getCandles("005930", Interval.ONE_MINUTE,
                    at("2026-09-04T09:01"), at("2026-09-04T09:02"));

            assertEquals(2, c.size());
            assertEquals(0, new BigDecimal("1").compareTo(c.get(0).close()));
            assertEquals(0, new BigDecimal("2").compareTo(c.get(1).close()));
        }

        @Test
        void 구간에_봉이_없으면_빈_목록이다() throws IOException {
            write("005930_1d.csv", "2024-01-02T00:00+09:00,1,1,1,1,1");

            List<Candle> c = new CsvPriceDataProvider(dir).getCandles("005930", Interval.ONE_DAY,
                    at("2025-01-01T00:00"), at("2025-12-31T00:00"));

            assertTrue(c.isEmpty());
        }

        @Test
        void 구간은_KST_로_비교한다() throws IOException {
            // UTC 00:01 = KST 09:01
            write("005930_1m.csv", "2026-09-04T00:01Z,1,1,1,1,10");

            List<Candle> c = new CsvPriceDataProvider(dir).getCandles("005930", Interval.ONE_MINUTE,
                    at("2026-09-04T09:01"), at("2026-09-04T09:01"));

            assertEquals(1, c.size());
        }

        @Test
        void 단위별로_다른_파일을_읽는다() throws IOException {
            write("005930_1d.csv", "2026-09-04T00:00+09:00,100,100,100,100,1");
            write("005930_1m.csv", "2026-09-04T09:01+09:00,200,200,200,200,1");
            CsvPriceDataProvider p = new CsvPriceDataProvider(dir);

            List<Candle> d = p.getCandles("005930", Interval.ONE_DAY, at("2026-09-04T00:00"), at("2026-09-04T23:59"));
            List<Candle> m = p.getCandles("005930", Interval.ONE_MINUTE, at("2026-09-04T00:00"), at("2026-09-04T23:59"));

            assertEquals(0, new BigDecimal("100").compareTo(d.get(0).close()));
            assertEquals(0, new BigDecimal("200").compareTo(m.get(0).close()));
        }
    }

    @Nested
    @DisplayName("값")
    class 값 {

        @Test
        void 가격은_문자열_그대로_BigDecimal_로_읽는다() throws IOException {
            write("247540_1d.csv", "2024-01-11T00:00+09:00,288892.5,308578,288892,301196.1234,1262524");

            Candle c = new CsvPriceDataProvider(dir).getCandles("247540", Interval.ONE_DAY,
                    at("2024-01-11T00:00"), at("2024-01-11T00:00")).get(0);

            assertEquals(new BigDecimal("288892.5"), c.open());
            assertEquals(new BigDecimal("301196.1234"), c.close());
            assertEquals(new BigDecimal("1262524"), c.volume());
        }
    }

    @Nested
    @DisplayName("오류")
    class 오류 {

        @Test
        void 파일이_없으면_예외다() {
            CsvPriceDataProvider p = new CsvPriceDataProvider(dir);
            assertThrows(IllegalStateException.class, () -> p.getCandles("999999", Interval.ONE_DAY,
                    at("2024-01-01T00:00"), at("2024-12-31T00:00")));
        }

        @Test
        void from_이_to_보다_늦으면_예외다() throws IOException {
            write("005930_1d.csv", "2024-01-02T00:00+09:00,1,1,1,1,1");
            CsvPriceDataProvider p = new CsvPriceDataProvider(dir);
            assertThrows(IllegalArgumentException.class, () -> p.getCandles("005930", Interval.ONE_DAY,
                    at("2024-12-31T00:00"), at("2024-01-01T00:00")));
        }

        @Test
        void 헤더가_다르면_예외다() throws IOException {
            Files.writeString(dir.resolve("005930_1d.csv"), "ts,o,h,l,c,v\n2024-01-02T00:00+09:00,1,1,1,1,1\n");
            CsvPriceDataProvider p = new CsvPriceDataProvider(dir);
            assertThrows(IllegalStateException.class, () -> p.getCandles("005930", Interval.ONE_DAY,
                    at("2024-01-01T00:00"), at("2024-12-31T00:00")));
        }

        @Test
        void 깨진_행은_줄번호와_함께_예외다() throws IOException {
            write("005930_1d.csv", "2024-01-02T00:00+09:00,1,1,1,1,1", "2024-01-03T00:00+09:00,abc,1,1,1,1");
            CsvPriceDataProvider p = new CsvPriceDataProvider(dir);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> p.getCandles("005930",
                    Interval.ONE_DAY, at("2024-01-01T00:00"), at("2024-12-31T00:00")));
            assertTrue(e.getMessage().contains(":3"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("실제 픽스처")
    class 실제픽스처 {

        private Path fixtures() throws URISyntaxException {
            return Path.of(getClass().getResource("/fixtures/crash_247540_1d.csv").toURI()).getParent();
        }

        /** 픽스처 파일명은 <성격>_<symbol>_<interval>.csv 라 구현체 규칙에 맞춰 복사해 읽는다. */
        @Test
        void crash_247540_1d_241봉을_그대로_읽는다() throws Exception {
            Files.copy(fixtures().resolve("crash_247540_1d.csv"), dir.resolve("247540_1d.csv"));

            List<Candle> c = new CsvPriceDataProvider(dir).getCandles("247540", Interval.ONE_DAY,
                    at("2024-01-01T00:00"), at("2025-12-31T00:00"));

            assertEquals(241, c.size());
            assertEquals(OffsetDateTime.parse("2024-01-11T00:00+09:00"), c.get(0).timestamp());
            assertEquals(OffsetDateTime.parse("2025-01-07T00:00+09:00"), c.get(240).timestamp());
            assertEquals(new BigDecimal("114179"), c.get(240).close());
        }
    }
}
