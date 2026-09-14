package com.moneygame.engine;

import com.moneygame.position.LiquidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4 게임 엔진 검증. (CLAUDE.md §M4 완료 판정)
 * 화면·네트워크·DB 없이 JUnit 만으로 한 판이 돌아야 한다.
 */
@DisplayName("M4 게임 엔진")
class GameSessionTest {

    private static final BigDecimal SEED = new BigDecimal("1000000");
    private static final Set<Integer> LEVERAGES = Set.of(1, 2, 3);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** 전 구간 같은 가격인 시세. tick() 을 ticks 번 부를 수 있도록 ticks+1 개를 만든다. */
    private static List<BigDecimal> flat(String price, int ticks) {
        List<BigDecimal> series = new ArrayList<>();
        for (int i = 0; i <= ticks; i++) series.add(bd(price));
        return series;
    }

    private static GameSession session(List<BigDecimal> seriesA, int ticks) {
        return new GameSession(List.of("A"), Map.of("A", seriesA), SEED, LEVERAGES, ticks);
    }

    @Nested
    @DisplayName("한 판 진행")
    class 한판진행 {

        @Test
        void 시작하고_240틱_돌고_끝난다() {
            GameSession s = session(flat("10000", 240), 240);
            s.addPlayer("u1", "플레이어1");
            s.start();
            assertEquals(0, s.tickIndex());
            assertEquals(GameSession.Status.RUNNING, s.status());

            TickResult last = null;
            for (int i = 0; i < 240; i++) last = s.tick();

            assertEquals(240, s.tickIndex());
            assertTrue(last.finished(), "마지막 틱은 finished 여야 한다");

            GameResult r = s.finish();
            assertEquals(GameSession.Status.FINISHED, s.status());
            assertEquals(1, r.rankings().size());
            assertEquals(0, SEED.compareTo(r.rankings().get(0).totalAsset()));
        }

        @Test
        void 시세를_넘어서면_더_틱할_수_없다() {
            GameSession s = session(flat("10000", 3), 3);
            s.addPlayer("u1", "p1");
            s.start();
            s.tick();
            s.tick();
            s.tick();
            assertThrows(IllegalStateException.class, s::tick);
        }

        @Test
        void 시작_전에는_틱할_수_없다() {
            GameSession s = session(flat("10000", 3), 3);
            s.addPlayer("u1", "p1");
            assertThrows(IllegalStateException.class, s::tick);
        }
    }

    @Nested
    @DisplayName("체결과 손익")
    class 체결과손익 {

        @Test
        void 매수시_증거금은_역산되고_수수료가_차감된다() {
            GameSession s = session(flat("10000", 5), 5);
            s.addPlayer("u1", "p1");
            s.start();

            // margin 100000, leverage 3, price 10000
            //   qty          = floor(300000 / 10000)  = 30
            //   actualMargin = 30 x 10000 / 3         = 100000
            //   fee          = 30 x 10000 x 0.0015    = 450
            //   cash         = 1000000 - 100000 - 450 = 899550
            OrderResult r = s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));

            assertTrue(r.accepted());
            assertEquals(30L, r.quantity());
            assertEquals(0, bd("100000").compareTo(r.actualMargin()));
            assertEquals(0, bd("450").compareTo(r.fee()));
            assertEquals(0, bd("899550").compareTo(s.player("u1").cash()));
        }

        @Test
        void 입력한_증거금보다_적게_쓰인다() {
            GameSession s = session(flat("7000", 5), 5);
            s.addPlayer("u1", "p1");
            s.start();

            // qty = floor(100000 x 3 / 7000) = 42, actualMargin = 42 x 7000 / 3 = 98000
            OrderResult r = s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));

            assertEquals(42L, r.quantity());
            assertEquals(0, bd("98000").compareTo(r.actualMargin()));
            assertTrue(r.actualMargin().compareTo(bd("100000")) < 0);
        }

        @Test
        void 상승후_매도하면_손익이_정확히_반영된다() {
            List<BigDecimal> series = new ArrayList<>();
            for (int i = 0; i <= 10; i++) series.add(i < 5 ? bd("10000") : bd("12000"));
            GameSession s = session(series, 10);
            s.addPlayer("u1", "p1");
            s.start();

            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            for (int i = 0; i < 5; i++) s.tick();

            // pnl  = (12000 - 10000) x 30 = 60000
            // fee  = 30 x 12000 x 0.0015  = 540
            // cash = 899550 + 100000 + 60000 - 540 = 1059010
            OrderResult sell = s.submitOrder(OrderRequest.sell("u1", "A"));

            assertTrue(sell.accepted());
            assertEquals(0, bd("1059010").compareTo(s.player("u1").cash()));
            assertTrue(s.player("u1").positions().isEmpty());
            assertEquals(2, s.player("u1").tradeCount());
        }

        @Test
        void 총자산은_현금과_포지션가치의_합이다() {
            GameSession s = session(flat("10000", 5), 5);
            s.addPlayer("u1", "p1");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));

            // cash 899550 + 포지션가치 100000 = 999550
            BigDecimal total = s.player("u1").totalAsset(Map.of("A", bd("10000")));
            assertEquals(0, bd("999550").compareTo(total));
        }
    }

    @Nested
    @DisplayName("청산")
    class 청산 {

        /** entry 10000, leverage 3 이면 청산가는 6666.6667 */
        private GameSession 청산직전세션(String secondTickPrice) {
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd(secondTickPrice)));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "p1");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            return s;
        }

        @Test
        void 청산가_이하로_내려가면_청산되고_증거금이_소멸한다() {
            GameSession s = 청산직전세션("6666.6667");
            BigDecimal cashBefore = s.player("u1").cash();

            TickResult t = s.tick();

            assertEquals(1, t.liquidations().size());
            assertEquals("A", t.liquidations().get(0).symbolLabel());
            assertEquals("u1", t.liquidations().get(0).userId());
            assertTrue(s.player("u1").positions().isEmpty());
            assertEquals(1, s.player("u1").liquidatedCount());
            // 증거금은 전액 소멸하고, 수수료는 종료 사유 불문 1회 부과된다 (§1.5)
            // fee = 30 x 6666.6667 x 0.0015 = 300.00000150 -> 올림 300.0001
            BigDecimal fee = t.liquidations().get(0).fee();
            assertEquals(bd("300.0001"), fee);
            assertEquals(0, cashBefore.subtract(fee).compareTo(s.player("u1").cash()),
                    "강제 청산은 증거금을 돌려주지 않고 수수료를 더 뺀다");
            assertEquals(0, bd("899249.9999").compareTo(s.player("u1").cash()));
        }

        @Test
        void 청산가보다_0_0001_높으면_청산되지_않는다() {
            GameSession s = 청산직전세션("6666.6668");

            TickResult t = s.tick();

            assertTrue(t.liquidations().isEmpty());
            assertFalse(s.player("u1").positions().isEmpty());
            assertEquals(0, s.player("u1").liquidatedCount());
        }

        @Test
        void 배율1은_청산되지_않는다() {
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd("1")));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "p1");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 1));

            TickResult t = s.tick();

            assertTrue(t.liquidations().isEmpty());
            assertFalse(s.player("u1").positions().isEmpty());
        }
    }

    @Nested
    @DisplayName("주문 거부")
    class 주문거부 {

        private GameSession 기본세션() {
            GameSession s = session(flat("10000", 5), 5);
            s.addPlayer("u1", "p1");
            s.start();
            return s;
        }

        @Test
        void 허용되지_않은_배율() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 5));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.LEVERAGE_NOT_ALLOWED, r.reason());
        }

        @Test
        void 증거금이_0이하() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.buy("u1", "A", BigDecimal.ZERO, 3));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.INVALID_MARGIN, r.reason());
        }

        @Test
        void 증거금이_한주값에_못미치면_증거금부족() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.buy("u1", "A", bd("9999"), 1));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.INSUFFICIENT_MARGIN, r.reason());
        }

        @Test
        void 잔고보다_큰_증거금() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.buy("u1", "A", bd("2000000"), 1));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.INSUFFICIENT_CASH, r.reason());
        }

        @Test
        void 같은_종목에_이미_포지션_보유() {
            GameSession s = 기본세션();
            assertTrue(s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3)).accepted());
            OrderResult r = s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.POSITION_ALREADY_EXISTS, r.reason());
        }

        @Test
        void 없는_포지션_매도() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.sell("u1", "A"));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.NO_POSITION, r.reason());
        }

        @Test
        void 없는_종목() {
            OrderResult r = 기본세션().submitOrder(OrderRequest.buy("u1", "Z", bd("100000"), 3));
            assertFalse(r.accepted());
            assertEquals(OrderResult.RejectReason.UNKNOWN_SYMBOL, r.reason());
        }

        @Test
        void 거부된_주문은_잔고를_건드리지_않는다() {
            GameSession s = 기본세션();
            BigDecimal before = s.player("u1").cash();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 5));
            assertEquals(0, before.compareTo(s.player("u1").cash()));
            assertEquals(0, s.player("u1").tradeCount());
        }
    }

    @Nested
    @DisplayName("종료와 순위")
    class 종료와순위 {

        @Test
        void 순위는_총자산_내림차순이다() {
            List<BigDecimal> series = new ArrayList<>();
            for (int i = 0; i <= 5; i++) series.add(i < 3 ? bd("10000") : bd("12000"));
            GameSession s = new GameSession(List.of("A"), Map.of("A", series),
                    SEED, LEVERAGES, 5);
            s.addPlayer("u1", "산사람");
            s.addPlayer("u2", "안산사람");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            for (int i = 0; i < 5; i++) s.tick();

            GameResult r = s.finish();

            assertEquals(2, r.rankings().size());
            assertEquals("u1", r.rankings().get(0).userId());
            assertEquals(1, r.rankings().get(0).rank());
            assertEquals("u2", r.rankings().get(1).userId());
            assertEquals(2, r.rankings().get(1).rank());
            // finish 도 매도와 똑같이 수수료를 부과한다 (§1.5 수수료 정책)
            // 899550 + 100000 + 60000 - (30 x 12000 x 0.0015 = 540) = 1059010
            assertEquals(0, bd("1059010").compareTo(r.rankings().get(0).totalAsset()));
            assertEquals(0, SEED.compareTo(r.rankings().get(1).totalAsset()));
        }

        @Test
        void 종료시_남은_포지션이_모두_정리된다() {
            GameSession s = session(flat("10000", 3), 3);
            s.addPlayer("u1", "p1");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            for (int i = 0; i < 3; i++) s.tick();

            s.finish();

            assertTrue(s.player("u1").positions().isEmpty());
        }

        @Test
        void 마지막틱_매도와_종료정리의_결과가_같다() {
            // 종료 정리에 수수료를 면제하면 "끝까지 안 파는 쪽"이 구조적으로 유리해져
            // 손절 판단 자체가 사라진다. 사유 불문 1회 규칙이 지켜지면
            // 두 경로의 최종 자산이 정확히 같아야 한다.
            GameSession s = session(flat("10000", 3), 3);
            s.addPlayer("u1", "마지막틱에판다");
            s.addPlayer("u2", "끝까지버틴다");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd("100000"), 3));
            s.submitOrder(OrderRequest.buy("u2", "A", bd("100000"), 3));
            for (int i = 0; i < 3; i++) s.tick();

            s.submitOrder(OrderRequest.sell("u1", "A"));   // 직접 매도
            GameResult r = s.finish();                      // u2 는 종료 정리로 청산

            BigDecimal u1 = s.player("u1").cash();
            BigDecimal u2 = s.player("u2").cash();
            assertEquals(0, u1.compareTo(u2),
                    "종료 정리를 면제하면 버티는 쪽이 유리해진다: u1=" + u1 + ", u2=" + u2);
            // 899550 + 100000 - 450 = 999100
            assertEquals(0, bd("999100").compareTo(u1));
            assertEquals(2, r.rankings().size());
        }

        @Test
        void 수익률이_계산된다() {
            GameSession s = session(flat("10000", 3), 3);
            s.addPlayer("u1", "p1");
            s.start();
            for (int i = 0; i < 3; i++) s.tick();

            GameResult r = s.finish();

            assertEquals(0, BigDecimal.ZERO.compareTo(r.rankings().get(0).returnRate()));
        }
    }

    @Nested
    @DisplayName("잔고 불변식 — cash 는 어떤 경로로도 음수가 되지 않는다")
    class 잔고불변식 {

        /** 시드 100만, 진입가 10000, 배율 3 에서 현금을 거의 다 태우는 주문. */
        private static final String 풀베팅 = "995000";

        @Test
        void 풀베팅_후_즉시_청산되어도_현금은_0이_된다() {
            // qty 298, actualMargin 993333.33333333, fee 4470 -> 잔여 현금 2196.66666667
            // 청산 수수료 원래값 2980.0001 은 잔여 현금보다 크다 -> 잔여 현금까지만 부과
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd("6666.6667")));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "풀베팅");
            s.addPlayer("u2", "관망");
            s.start();
            assertTrue(s.submitOrder(OrderRequest.buy("u1", "A", bd(풀베팅), 3)).accepted());
            BigDecimal cashBefore = s.player("u1").cash();
            assertEquals(0, bd("2196.66666667").compareTo(cashBefore));

            TickResult t = s.tick();

            assertEquals(1, t.liquidations().size());
            assertEquals(0, cashBefore.compareTo(t.liquidations().get(0).fee()),
                    "부과된 수수료는 잔여 현금을 넘지 않는다");
            assertEquals(0, s.player("u1").cash().signum(), "현금은 정확히 0 이 된다");
            assertTrue(s.player("u1").cash().signum() >= 0);
        }

        @Test
        void 여러_포지션이_같은_틱에_청산돼도_현금은_음수가_되지_않는다() {
            List<BigDecimal> a = new ArrayList<>(List.of(bd("10000"), bd("6666.6667")));
            List<BigDecimal> b = new ArrayList<>(List.of(bd("10000"), bd("6666.6667")));
            GameSession s = new GameSession(List.of("A", "B"), Map.of("A", a, "B", b),
                    SEED, LEVERAGES, 1);
            s.addPlayer("u1", "양쪽풀베팅");
            s.start();
            // 각각 qty 149, 비용 498901.66666667 -> 두 번이면 잔여 현금 2196.66666666
            assertTrue(s.submitOrder(OrderRequest.buy("u1", "A", bd("499000"), 3)).accepted());
            assertTrue(s.submitOrder(OrderRequest.buy("u1", "B", bd("499000"), 3)).accepted());
            assertEquals(2, s.player("u1").positions().size());

            TickResult t = s.tick();

            assertEquals(2, t.liquidations().size());
            assertEquals(2, s.player("u1").liquidatedCount());
            // 청산 수수료 합(2980.0002)이 잔여 현금(2196.66666666)보다 크다
            assertEquals(0, s.player("u1").cash().signum(), "두 번째 청산에서 잔여분까지만 부과된다");
            assertTrue(s.player("u1").cash().signum() >= 0);
        }

        @Test
        void 종료정리_수수료로도_현금이_음수가_되지_않는다() {
            // 청산가(6666.6667)보다 0.0001 높아 살아남지만 포지션가치가 거의 0 인 상태.
            // 정리 수수료 2980.0001 은 현금 + 포지션가치보다 크다.
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd("6666.6668")));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "풀베팅");
            s.start();
            assertTrue(s.submitOrder(OrderRequest.buy("u1", "A", bd(풀베팅), 3)).accepted());

            TickResult t = s.tick();
            assertTrue(t.liquidations().isEmpty(), "이 가격에서는 살아남아야 한다");

            GameResult r = s.finish();

            assertEquals(0, s.player("u1").cash().signum());
            assertEquals(0, r.rankings().get(0).totalAsset().signum());
        }

        @Test
        void 수익률은_마이너스_100퍼센트_밑으로_내려가지_않는다() {
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd("6666.6667")));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "풀베팅");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd(풀베팅), 3));
            s.tick();

            GameResult r = s.finish();

            assertEquals(0, bd("-1").compareTo(r.rankings().get(0).returnRate()),
                    "총자산 0 이면 수익률은 정확히 -100%% 다");
            assertTrue(r.rankings().get(0).returnRate().compareTo(bd("-1")) >= 0);
        }

        @Test
        void 매도_수수료도_잔여_현금을_넘지_않는다() {
            List<BigDecimal> series = new ArrayList<>(List.of(bd("10000"), bd("6666.6668")));
            GameSession s = session(series, 1);
            s.addPlayer("u1", "풀베팅");
            s.start();
            s.submitOrder(OrderRequest.buy("u1", "A", bd(풀베팅), 3));
            s.tick();

            OrderResult sell = s.submitOrder(OrderRequest.sell("u1", "A"));

            assertTrue(sell.accepted());
            assertEquals(0, s.player("u1").cash().signum());
            assertTrue(s.player("u1").cash().signum() >= 0);
        }
    }

    @Test
    void 수수료율은_M5_규칙에서_가져온다() {
        assertEquals(0, new BigDecimal("0.0015").compareTo(LiquidationRule.FEE_RATE));
    }
}
