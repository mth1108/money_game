package com.moneygame.engine;

import java.math.BigDecimal;

/**
 * 주문 요청. (CLAUDE.md M4 / M7 의 ORDER 메시지에 대응)
 *
 * margin 은 "이만큼까지 쓰겠다"는 상한이다. 실제 차감액은 체결 수량에서 역산된다 (§1.5).
 */
public record OrderRequest(String userId,
                           String symbolLabel,
                           Action action,
                           BigDecimal margin,
                           int leverage) {

    public enum Action { BUY, SELL }

    public static OrderRequest buy(String userId, String symbolLabel, BigDecimal margin, int leverage) {
        return new OrderRequest(userId, symbolLabel, Action.BUY, margin, leverage);
    }

    /** 매도는 종목당 포지션 1개 제한 덕분에 수량·배율을 받지 않는다. */
    public static OrderRequest sell(String userId, String symbolLabel) {
        return new OrderRequest(userId, symbolLabel, Action.SELL, null, 0);
    }
}
