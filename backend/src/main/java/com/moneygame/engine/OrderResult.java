package com.moneygame.engine;

import java.math.BigDecimal;

/**
 * 주문 처리 결과. 본인에게만 전달된다 (CLAUDE.md M7).
 */
public record OrderResult(boolean accepted,
                          RejectReason reason,
                          String symbolLabel,
                          long quantity,
                          BigDecimal price,
                          BigDecimal actualMargin,
                          BigDecimal fee) {

    public enum RejectReason {
        NONE,
        /** margin <= 0 */
        INVALID_MARGIN,
        /** 체결 수량이 0. 증거금이 1주 값에 못 미친다 */
        INSUFFICIENT_MARGIN,
        /** cash < actualMargin + fee */
        INSUFFICIENT_CASH,
        LEVERAGE_NOT_ALLOWED,
        POSITION_ALREADY_EXISTS,
        NO_POSITION,
        UNKNOWN_SYMBOL,
        UNKNOWN_PLAYER,
        NOT_RUNNING
    }

    public static OrderResult accept(String symbolLabel, long quantity, BigDecimal price,
                                     BigDecimal actualMargin, BigDecimal fee) {
        return new OrderResult(true, RejectReason.NONE, symbolLabel, quantity, price, actualMargin, fee);
    }

    public static OrderResult reject(RejectReason reason, String symbolLabel) {
        return new OrderResult(false, reason, symbolLabel, 0L, null, null, null);
    }
}
