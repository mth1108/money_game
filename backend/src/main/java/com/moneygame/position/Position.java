package com.moneygame.position;

import java.math.BigDecimal;

/**
 * 배율이 걸린 포지션 하나. (CLAUDE.md M5)
 *
 * 진입 시점에 확정되는 값만 갖는다. 현재가는 밖에서 받는다.
 * 종목당 포지션 1개 제한이 있어 평단가와 부분 청산을 다루지 않는다.
 */
public final class Position {

    /** SHORT 는 자료구조에만 두고 로직은 LONG 만 구현한다 (CLAUDE.md §1.4). */
    public enum Side { LONG, SHORT }

    private final String symbolLabel;
    private final Side side;
    private final BigDecimal entryPrice;
    private final long quantity;
    private final BigDecimal margin;
    private final int leverage;
    private final BigDecimal liquidationPrice;

    private Position(String symbolLabel, Side side, BigDecimal entryPrice, long quantity,
                     BigDecimal margin, int leverage, BigDecimal liquidationPrice) {
        this.symbolLabel = symbolLabel;
        this.side = side;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.margin = margin;
        this.leverage = leverage;
        this.liquidationPrice = liquidationPrice;
    }

    /** 진입. 증거금과 청산가는 체결 수량에서 계산한다. */
    public static Position open(String symbolLabel, BigDecimal entryPrice, long quantity, int leverage) {
        return new Position(symbolLabel, Side.LONG, entryPrice, quantity,
                LiquidationRule.actualMargin(quantity, entryPrice, leverage),
                leverage,
                LiquidationRule.liquidationPrice(entryPrice, leverage));
    }

    /** 미실현손익 = (현재가 - 진입가) x 수량 */
    public BigDecimal unrealizedPnl(BigDecimal currentPrice) {
        return currentPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
    }

    /** 포지션가치 = 증거금 + 미실현손익. 청산가에서 0 에 수렴한다. */
    public BigDecimal value(BigDecimal currentPrice) {
        return margin.add(unrealizedPnl(currentPrice));
    }

    public boolean isLiquidatedAt(BigDecimal currentPrice) {
        return LiquidationRule.isLiquidated(currentPrice, liquidationPrice);
    }

    public String symbolLabel() { return symbolLabel; }
    public Side side() { return side; }
    public BigDecimal entryPrice() { return entryPrice; }
    public long quantity() { return quantity; }
    public BigDecimal margin() { return margin; }
    public int leverage() { return leverage; }
    public BigDecimal liquidationPrice() { return liquidationPrice; }
}
