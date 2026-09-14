package com.moneygame.position;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5 계산 규칙 검증. (CLAUDE.md §1.5)
 *
 * 경계값은 배율 3으로 만든다. 1/3 이 무한소수라 scale·RoundingMode 를
 * 잘못 잡으면 여기서 터진다. 배율 2로만 테스트하면 이 문제를 못 잡는다.
 */
@DisplayName("M5 포지션 계산 규칙")
class LiquidationTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Nested
    @DisplayName("청산가 경계값 — 배율 3")
    class 청산가경계값 {

        // entryPrice 10000, leverage 3
        //   1/3              = 0.33333333      (scale 8, HALF_UP)
        //   1 - 1/3          = 0.66666667
        //   x 10000          = 6666.66670000
        //   setScale(4,DOWN) = 6666.6667
        private final BigDecimal entry = bd("10000");
        private final BigDecimal liq = LiquidationRule.liquidationPrice(bd("10000"), 3);

        @Test
        void 청산가가_기대값과_정확히_일치한다() {
            assertEquals(bd("6666.6667"), liq);
        }

        @Test
        void 청산가보다_0_0001_높으면_생존한다() {
            assertFalse(LiquidationRule.isLiquidated(liq.add(bd("0.0001")), liq));
        }

        @Test
        void 정확히_청산가면_청산된다() {
            assertTrue(LiquidationRule.isLiquidated(liq, liq));
        }

        @Test
        void 청산가보다_0_0001_낮으면_청산된다() {
            assertTrue(LiquidationRule.isLiquidated(liq.subtract(bd("0.0001")), liq));
        }

        @Test
        void 청산가에서_포지션가치는_0에_수렴한다() {
            long qty = LiquidationRule.quantity(bd("100000"), 3, entry);
            Position p = Position.open("A", entry, qty, 3);
            BigDecimal value = p.value(liq);
            assertTrue(value.abs().compareTo(bd("0.01")) < 0,
                    "청산가에서 포지션가치가 0 근처여야 한다: " + value);
        }
    }

    @Nested
    @DisplayName("수량 정수화와 증거금 역산")
    class 수량과증거금 {

        @Test
        void 수량은_내림된다() {
            // 100000 x 3 / 7000 = 42.857...
            assertEquals(42L, LiquidationRule.quantity(bd("100000"), 3, bd("7000")));
        }

        @Test
        void 증거금은_체결수량에서_역산되며_입력값보다_작다() {
            long qty = LiquidationRule.quantity(bd("100000"), 3, bd("7000"));
            BigDecimal actual = LiquidationRule.actualMargin(qty, bd("7000"), 3);
            // 42 x 7000 / 3 = 98000
            assertEquals(0, bd("98000").compareTo(actual));
            assertTrue(actual.compareTo(bd("100000")) < 0, "margin 은 상한이다");
        }

        @Test
        void 항등식_actualMargin_x_leverage_는_notional_과_같다() {
            // 43 x 7000 = 301000 은 3으로 나누어떨어지지 않는다.
            // scale 8 반올림 잔차만 남아야 한다.
            long qty = LiquidationRule.quantity(bd("100400"), 3, bd("7000"));
            assertEquals(43L, qty);
            BigDecimal actual = LiquidationRule.actualMargin(qty, bd("7000"), 3);
            BigDecimal notional = BigDecimal.valueOf(qty).multiply(bd("7000"));
            BigDecimal diff = actual.multiply(BigDecimal.valueOf(3)).subtract(notional).abs();
            assertTrue(diff.compareTo(bd("0.0000001")) <= 0, "항등식 오차: " + diff);
        }

        @Test
        void 나누어떨어지면_항등식이_정확히_성립한다() {
            long qty = LiquidationRule.quantity(bd("100000"), 3, bd("10000"));
            assertEquals(30L, qty);
            BigDecimal actual = LiquidationRule.actualMargin(qty, bd("10000"), 3);
            assertEquals(0, bd("100000").compareTo(actual));
            assertEquals(0, actual.multiply(BigDecimal.valueOf(3))
                    .compareTo(bd("300000")));
        }

        @Test
        void 증거금이_한주값에_못미치면_수량이_0이다() {
            assertEquals(0L, LiquidationRule.quantity(bd("6999"), 1, bd("7000")));
        }
    }

    @Nested
    @DisplayName("수수료")
    class 수수료 {

        @Test
        void 수수료는_올림한다() {
            // 1 x 7000.5 x 0.0015 = 10.50075 -> 10.5008
            assertEquals(bd("10.5008"), LiquidationRule.fee(1L, bd("7000.5")));
        }

        @Test
        void 나누어떨어지는_수수료는_그대로다() {
            // 30 x 10000 x 0.0015 = 450
            assertEquals(0, bd("450").compareTo(LiquidationRule.fee(30L, bd("10000"))));
        }
    }

    @Nested
    @DisplayName("배율 1")
    class 배율1 {

        @Test
        void 청산가가_0이라_청산되지_않는다() {
            BigDecimal liq = LiquidationRule.liquidationPrice(bd("10000"), 1);
            assertEquals(0, BigDecimal.ZERO.compareTo(liq));
            assertFalse(LiquidationRule.isLiquidated(bd("0.0001"), liq));
        }
    }
}
