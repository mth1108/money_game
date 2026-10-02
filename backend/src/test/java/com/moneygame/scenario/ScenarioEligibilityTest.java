package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.moneygame.scenario.TestCandles.candle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3 적격성 검사. (CLAUDE.md §3 M3) */
@DisplayName("M3 시나리오 적격성 검사")
class ScenarioEligibilityTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2024, 1, 1, 0, 0);

    /** 하루 09:01 부터 241분. 종가는 10000 에서 시작해 rise 만큼 선형으로 오른다. */
    private static List<Candle> minutes(LocalDateTime first, double rise) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < ScenarioRules.BARS; i++) {
            long close = 10000 + Math.round(10000 * rise * i / (ScenarioRules.BARS - 1));
            list.add(candle(first.plusMinutes(i), Long.toString(close), "100"));
        }
        return list;
    }

    private static List<Candle> withZeroVolume(List<Candle> base, int... indexes) {
        List<Candle> list = new ArrayList<>(base);
        for (int i : indexes) {
            Candle c = list.get(i);
            list.set(i, new Candle(c.timestamp(), c.open(), c.high(), c.low(), c.close(), BigDecimal.ZERO));
        }
        return list;
    }

    @Nested
    @DisplayName("실제 픽스처")
    class 실제픽스처 {

        @ParameterizedTest(name = "{0} -> {2}")
        @CsvSource({
                "crash_247540_1d, 1d, true",
                "surge_000660_1d, 1d, true",
                "crash_000660_1m, 1m, true",
                "surge_247540_1m, 1m, true",
                "flat_005930_1m,  1m, false",
        })
        void 픽스처_적격성(String fixture, String interval, boolean eligible) throws IOException {
            ScenarioEligibility.Result r = ScenarioEligibility.check(TestCandles.fixture(fixture), Interval.of(interval));
            assertEquals(eligible, r.eligible(), r.reasons().toString());
        }

        @Test
        void 횡보_분봉은_이동폭_때문에_탈락한다() throws IOException {
            ScenarioEligibility.Result r = ScenarioEligibility.check(TestCandles.fixture("flat_005930_1m"), Interval.ONE_MINUTE);
            assertEquals(1, r.reasons().size());
            assertTrue(r.reasons().get(0).startsWith("이동폭"), r.reasons().toString());
        }

        @Test
        void 급락_일봉의_최저는_시작_대비_마이너스다() throws IOException {
            ScenarioEligibility.Result r = ScenarioEligibility.check(TestCandles.fixture("crash_247540_1d"), Interval.ONE_DAY);
            assertTrue(r.drawdown().compareTo(new BigDecimal("-0.65")) < 0, "최저 " + r.drawdown());
            assertEquals(0, r.swing().compareTo(r.drawdown().negate()), "하락 쪽이 이동폭이 된다");
        }
    }

    @Nested
    @DisplayName("이동폭")
    class 이동폭 {

        @Test
        void 일봉은_30퍼센트_이상이어야_한다() {
            assertTrue(ScenarioEligibility.check(TestCandles.daily(DAY, 241, 10000, 0.30), Interval.ONE_DAY).eligible(),
                    "정확히 30% 는 통과");
            assertFalse(ScenarioEligibility.check(TestCandles.daily(DAY, 241, 10000, 0.299), Interval.ONE_DAY).eligible());
        }

        @Test
        void 분봉은_2_5퍼센트_이상이어야_한다() {
            LocalDateTime open = LocalDateTime.of(2026, 9, 4, 9, 1);
            assertTrue(ScenarioEligibility.check(minutes(open, 0.025), Interval.ONE_MINUTE).eligible());
            assertFalse(ScenarioEligibility.check(minutes(open, 0.024), Interval.ONE_MINUTE).eligible());
        }

        @Test
        void 하락_쪽_이동폭도_센다() {
            List<Candle> down = TestCandles.daily(DAY, 241, 10000, -0.35);
            ScenarioEligibility.Result r = ScenarioEligibility.check(down, Interval.ONE_DAY);
            assertTrue(r.eligible(), r.reasons().toString());
            assertEquals(0, new BigDecimal("0.35").compareTo(r.swing()));
            assertEquals(0, new BigDecimal("-0.35").compareTo(r.drawdown()));
        }
    }

    @Nested
    @DisplayName("거래량 0 봉")
    class 거래량0 {

        private final List<Candle> base = TestCandles.daily(DAY, 241, 10000, 0.5);

        @Test
        void 흩어진_12봉은_비율_5퍼센트_이내라_통과한다() {
            // 12 / 241 = 4.98%
            List<Candle> c = withZeroVolume(base, 10, 30, 50, 70, 90, 110, 130, 150, 170, 190, 210, 230);
            assertTrue(ScenarioEligibility.check(c, Interval.ONE_DAY).eligible());
        }

        @Test
        void 흩어진_13봉은_비율_초과로_탈락한다() {
            // 13 / 241 = 5.39%
            List<Candle> c = withZeroVolume(base, 10, 30, 50, 70, 90, 110, 130, 150, 170, 190, 210, 230, 235);
            ScenarioEligibility.Result r = ScenarioEligibility.check(c, Interval.ONE_DAY);
            assertFalse(r.eligible());
            assertTrue(r.reasons().get(0).startsWith("거래량 0 봉 비율"), r.reasons().toString());
        }

        @Test
        void 연속_5봉은_통과하고_6봉은_탈락한다() {
            assertTrue(ScenarioEligibility.check(withZeroVolume(base, 100, 101, 102, 103, 104), Interval.ONE_DAY).eligible());
            ScenarioEligibility.Result r = ScenarioEligibility.check(
                    withZeroVolume(base, 100, 101, 102, 103, 104, 105), Interval.ONE_DAY);
            assertFalse(r.eligible());
            assertEquals(6, r.maxConsecutiveZero());
        }
    }

    @Nested
    @DisplayName("분봉 구간")
    class 분봉구간 {

        @Test
        void 정규장_봉만_이어붙여도_날짜를_넘기면_탈락한다() {
            // 첫날 14:21 ~ 15:20 (60봉) + 다음 날 09:01 ~ 12:01 (181봉). 모든 봉이 정규장이지만
            // 오버나이트 갭이 섞인다
            List<Candle> c = new ArrayList<>();
            List<Candle> rising = minutes(LocalDateTime.of(2026, 9, 3, 9, 1), 0.05);
            for (int i = 0; i < 60; i++) {
                Candle r = rising.get(i);
                c.add(candle(LocalDateTime.of(2026, 9, 3, 14, 21).plusMinutes(i), r.close().toPlainString(), "100"));
            }
            for (int i = 60; i < ScenarioRules.BARS; i++) {
                Candle r = rising.get(i);
                c.add(candle(LocalDateTime.of(2026, 9, 4, 9, 1).plusMinutes(i - 60), r.close().toPlainString(), "100"));
            }

            ScenarioEligibility.Result r = ScenarioEligibility.check(c, Interval.ONE_MINUTE);

            assertFalse(r.eligible());
            assertTrue(r.reasons().get(0).startsWith("날짜를 넘깁니다"), r.reasons().toString());
        }

        @Test
        void 개장_전_09시_봉이_있으면_탈락한다() {
            List<Candle> c = minutes(LocalDateTime.of(2026, 9, 4, 9, 0), 0.05);
            ScenarioEligibility.Result r = ScenarioEligibility.check(c, Interval.ONE_MINUTE);
            assertFalse(r.eligible());
            assertTrue(r.reasons().get(0).contains("정규장"), r.reasons().toString());
        }

        @Test
        void 마지막_봉이_15시20분이면_통과하고_15시21분이면_탈락한다() {
            // 11:20 ~ 15:20 = 241봉
            assertTrue(ScenarioEligibility.check(minutes(LocalDateTime.of(2026, 9, 4, 11, 20), 0.05),
                    Interval.ONE_MINUTE).eligible());
            assertFalse(ScenarioEligibility.check(minutes(LocalDateTime.of(2026, 9, 4, 11, 21), 0.05),
                    Interval.ONE_MINUTE).eligible());
        }
    }

    @Test
    void 봉_수가_241이_아니면_탈락한다() {
        ScenarioEligibility.Result r = ScenarioEligibility.check(TestCandles.daily(DAY, 240, 10000, 0.5), Interval.ONE_DAY);
        assertFalse(r.eligible());
        assertTrue(r.reasons().get(0).startsWith("봉 수"), r.reasons().toString());
    }
}
