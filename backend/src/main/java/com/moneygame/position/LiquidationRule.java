package com.moneygame.position;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 배율 포지션의 진입·평가·청산 계산 규칙. (CLAUDE.md §1.5, M5)
 *
 * 상태를 갖지 않는 순수 계산이다. Spring 과 JPA 를 참조하지 않는다.
 * 반올림 원칙: 애매하면 플레이어에게 유리하게, 단 잔고는 보수적으로.
 */
public final class LiquidationRule {

    /** 중간 계산 scale. 나눗셈은 전부 이 scale 의 HALF_UP 으로 한다. */
    public static final int CALC_SCALE = 8;

    /** 최종 금액·가격 scale. */
    public static final int PRICE_SCALE = 4;

    /** 수수료율. CLAUDE.md §8 미결정 — 플레이해보고 조정한다. */
    public static final BigDecimal FEE_RATE = new BigDecimal("0.0015");

    private LiquidationRule() {
    }

    /**
     * 체결 수량. 입력 margin 은 상한이며 내림한 정수 수량만 잡는다.
     * 0 이면 주문을 거부해야 한다 (사유: 증거금 부족).
     */
    public static long quantity(BigDecimal margin, int leverage, BigDecimal entryPrice) {
        if (margin == null || margin.signum() <= 0) return 0L;
        if (entryPrice == null || entryPrice.signum() <= 0) return 0L;
        if (leverage <= 0) return 0L;
        return margin.multiply(BigDecimal.valueOf(leverage))
                .divide(entryPrice, 0, RoundingMode.DOWN)
                .longValueExact();
    }

    /**
     * 실제 차감 증거금. 체결 수량에서 역산한다.
     *
     * actualMargin x leverage == quantity x entryPrice 가 성립한다.
     * notional 이 leverage 로 나누어떨어지지 않으면 scale 8 반올림 잔차(최대 1e-8)가 남는다.
     * 잔돈 반환 방식은 이 항등식을 깨서 청산 계산이 틀어지므로 쓰지 않는다.
     */
    public static BigDecimal actualMargin(long quantity, BigDecimal entryPrice, int leverage) {
        return BigDecimal.valueOf(quantity)
                .multiply(entryPrice)
                .divide(BigDecimal.valueOf(leverage), CALC_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 청산가 = entryPrice x (1 - 1/leverage).
     *
     * 내림한다 — 청산이 늦게 일어나 플레이어에게 유리하다.
     * 배율 1이면 0 이 되어 사실상 청산되지 않는다.
     */
    public static BigDecimal liquidationPrice(BigDecimal entryPrice, int leverage) {
        BigDecimal ratio = BigDecimal.ONE.subtract(
                BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), CALC_SCALE, RoundingMode.HALF_UP));
        return entryPrice.multiply(ratio).setScale(PRICE_SCALE, RoundingMode.DOWN);
    }

    /** 수수료 = 수량 x 가격 x FEE_RATE. 올림한다 — 잔고가 음수로 새는 것을 막는다. */
    public static BigDecimal fee(long quantity, BigDecimal price) {
        return BigDecimal.valueOf(quantity)
                .multiply(price)
                .multiply(FEE_RATE)
                .setScale(PRICE_SCALE, RoundingMode.UP);
    }

    /** 현재가 <= 청산가 이면 청산. 등호를 포함한다. */
    public static boolean isLiquidated(BigDecimal currentPrice, BigDecimal liquidationPrice) {
        return currentPrice.compareTo(liquidationPrice) <= 0;
    }
}
