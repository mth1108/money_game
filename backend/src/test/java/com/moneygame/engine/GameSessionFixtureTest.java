package com.moneygame.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 시세 픽스처로 한 판(241봉 = 시작가 + 240틱)을 끝까지 돌린다. (CLAUDE.md §3 M4, §9-5)
 *
 * 기대값은 엔진과 따로 계산했다 — 파이썬 Decimal 로 §1.5 반올림 규칙을 그대로 옮겨
 * 같은 입력에서 수량·증거금·수수료·청산 틱·최종 자산을 구한 값이다.
 *
 * engine 은 marketdata 를 참조하지 않으므로 CSV 는 여기서 직접 읽는다 (종가만).
 */
@DisplayName("M4 게임 엔진 — 실제 시세 픽스처")
class GameSessionFixtureTest {

    private static final BigDecimal SEED = new BigDecimal("1000000");
    private static final BigDecimal MARGIN = new BigDecimal("500000");
    private static final int TICKS = 240;

    /** CLAUDE.md §3 M5 배율 상한 */
    private static final Set<Integer> DAILY = Set.of(1, 2, 3);
    private static final Set<Integer> MINUTE = Set.of(1, 3, 5, 10);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** 픽스처의 종가 열. 241봉이어야 한다 (§3 M4 「시세와 틱의 관계」). */
    private static List<BigDecimal> closes(String fixture) throws IOException {
        try (InputStream in = GameSessionFixtureTest.class.getResourceAsStream("/fixtures/" + fixture + ".csv")) {
            assertNotNull(in, "픽스처가 없습니다: " + fixture);
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            List<BigDecimal> closes = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].strip();
                if (!line.isEmpty()) {
                    closes.add(new BigDecimal(line.split(",")[4]));
                }
            }
            assertEquals(TICKS + 1, closes.size(), fixture + " 는 241봉이어야 한다");
            return closes;
        }
    }

    private static GameSession session(String fixture, Set<Integer> leverages) throws IOException {
        return new GameSession(List.of("A"), Map.of("A", closes(fixture)), SEED, leverages, TICKS);
    }

    /** 240틱을 끝까지 돌리며 TickResult 를 모은다. */
    private static List<TickResult> runAll(GameSession s) {
        List<TickResult> ticks = new ArrayList<>();
        for (int i = 0; i < TICKS; i++) {
            ticks.add(s.tick());
            for (var p : s.players().values()) {
                assertTrue(p.cash().signum() >= 0, "cash 는 음수가 되지 않는다: " + p.userId());
            }
        }
        return ticks;
    }

    @Test
    void 일봉_급락_구간에서_배율별로_청산_시점과_최종_자산이_다르다() throws IOException {
        // crash_247540_1d : 시작 종가 301196, 시작 대비 최저 -65.5%
        GameSession s = session("crash_247540_1d", DAILY);
        s.addPlayer("hold", "관망");
        s.addPlayer("x1", "배율1");
        s.addPlayer("x2", "배율2");
        s.addPlayer("x3", "배율3");
        s.start();
        assertTrue(s.submitOrder(OrderRequest.buy("x1", "A", MARGIN, 1)).accepted());
        assertTrue(s.submitOrder(OrderRequest.buy("x2", "A", MARGIN, 2)).accepted());
        assertTrue(s.submitOrder(OrderRequest.buy("x3", "A", MARGIN, 3)).accepted());

        List<TickResult> ticks = runAll(s);

        // 배율 3 : 청산가 200797.3343 -> 86틱 종가 200305 에서 청산
        // 배율 2 : 청산가 150598.0000 -> 163틱 종가 149220 에서 청산
        // 배율 1 : 청산가 0 -> 끝까지 생존
        TickResult t86 = ticks.get(85);
        TickResult t163 = ticks.get(162);
        assertEquals(1, t86.liquidations().size());
        assertEquals("x3", t86.liquidations().get(0).userId());
        assertEquals(bd("200305"), t86.liquidations().get(0).price());
        assertEquals(1, t163.liquidations().size());
        assertEquals("x2", t163.liquidations().get(0).userId());
        assertEquals(2, ticks.stream().mapToInt(t -> t.liquidations().size()).sum(), "청산은 두 번뿐이다");

        assertTrue(ticks.get(TICKS - 1).finished());
        assertEquals(OrderResult.RejectReason.TIME_OVER, s.submitOrder(OrderRequest.sell("x1", "A")).reason());

        GameResult r = s.finish();

        // 수량 정수화 때문에 배율 3(4주)이 배율 2(3주)보다 증거금을 적게 걸어 덜 잃는다
        assertEquals(List.of("hold", "x1", "x3", "x2"), r.rankings().stream().map(GameResult.Rank::userId).toList());
        assertEquals(List.of(1, 2, 3, 4), r.rankings().stream().map(GameResult.Rank::rank).toList());
        assertEquals(0, SEED.compareTo(r.rankings().get(0).totalAsset()));
        assertEquals(0, bd("812359.93750000").compareTo(r.rankings().get(1).totalAsset()));
        assertEquals(0, bd("595396.32733333").compareTo(r.rankings().get(2).totalAsset()));
        assertEquals(0, bd("546179.12800000").compareTo(r.rankings().get(3).totalAsset()));
        assertEquals(bd("-0.1876"), r.rankings().get(1).returnRate());

        assertEquals(List.of(Trade.Kind.BUY, Trade.Kind.BUY, Trade.Kind.BUY,
                        Trade.Kind.LIQUIDATION, Trade.Kind.LIQUIDATION, Trade.Kind.SETTLEMENT),
                r.trades().stream().map(Trade::kind).toList());
        assertEquals(List.of(0, 0, 0, 86, 163, 240), r.trades().stream().map(Trade::tickIndex).toList());
        assertEquals(bd("1201.8300"), r.trades().get(3).fee(), "배율 3 청산 수수료 = 4주 x 200305 x 0.0015");
    }

    @Test
    void 일봉_급등_구간은_배율3이어도_청산되지_않는다() throws IOException {
        // surge_000660_1d : 시작 종가 230000 -> 마지막 종가 2324000, 시작가 밑으로 내려간 적이 없다
        GameSession s = session("surge_000660_1d", DAILY);
        s.addPlayer("x3", "배율3");
        s.start();
        assertTrue(s.submitOrder(OrderRequest.buy("x3", "A", MARGIN, 3)).accepted());

        List<TickResult> ticks = runAll(s);
        GameResult r = s.finish();

        assertTrue(ticks.stream().allMatch(t -> t.liquidations().isEmpty()));
        // 6주 x 230000, 증거금 460000, 매수 수수료 2070, 매도 수수료 20916
        assertEquals(0, bd("13541014").compareTo(r.rankings().get(0).totalAsset()));
        assertEquals(bd("12.5410"), r.rankings().get(0).returnRate());
    }

    /**
     * 분봉은 배율 10(청산선 -10%)을 걸어도 청산되지 않는다 — CLAUDE.md §8 실측과 같은 결론이다.
     * 급락 구간(시작 대비 최저 -4.3%)조차 청산선에 닿지 않는다.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "flat_005930_1m,  1004493.5",
            "crash_000660_1m, 829537",
            "surge_247540_1m, 1329799.3",
    })
    void 분봉은_배율10이어도_청산되지_않는다(String fixture, String expectedFinal) throws IOException {
        GameSession s = session(fixture, MINUTE);
        s.addPlayer("x10", "배율10");
        s.start();
        assertTrue(s.submitOrder(OrderRequest.buy("x10", "A", MARGIN, 10)).accepted());

        List<TickResult> ticks = runAll(s);
        GameResult r = s.finish();

        assertTrue(ticks.stream().allMatch(t -> t.liquidations().isEmpty()), "청산이 없어야 한다");
        assertEquals(0, s.player("x10").liquidatedCount());
        assertEquals(0, bd(expectedFinal).compareTo(r.rankings().get(0).totalAsset()));
        assertEquals(List.of(Trade.Kind.BUY, Trade.Kind.SETTLEMENT), r.trades().stream().map(Trade::kind).toList());
    }
}
