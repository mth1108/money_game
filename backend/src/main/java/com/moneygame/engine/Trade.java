package com.moneygame.engine;

import java.math.BigDecimal;

/**
 * 체결 한 건. (CLAUDE.md §5 trades)
 *
 * 판이 도는 동안에는 GameSession 메모리에만 쌓는다 (§1.2).
 * 판이 끝나면 GameResult 에 담겨 M8 이 trades 테이블에 한 트랜잭션으로 일괄 INSERT 한다.
 *
 * @param tickIndex 체결된 틱. 0 은 start() 직후 (첫 tick() 전)
 * @param price     체결가. 강제 청산은 청산된 틱의 현재가다
 * @param margin    포지션 증거금 (actualMargin). 진입과 종료 모두 같은 값이다
 * @param fee       실제로 부과된 수수료. 잔여 현금을 넘지 않도록 깎였을 수 있다 (§3 M5 잔고 불변식)
 */
public record Trade(int tickIndex,
                    String userId,
                    String symbolLabel,
                    Kind kind,
                    long quantity,
                    BigDecimal price,
                    BigDecimal margin,
                    int leverage,
                    BigDecimal fee) {

    public enum Kind {
        /** 매수 (진입) */
        BUY,
        /** 정상 청산 — 플레이어가 직접 판 것 */
        SELL,
        /** 강제 청산 — 현재가가 청산가 이하로 내려가 증거금이 전액 소멸했다 */
        LIQUIDATION,
        /** 판 종료 시 정리 — 매도와 똑같이 처리된다 (§1.5 수수료 정책) */
        SETTLEMENT
    }

    /** trades 테이블의 「매수/매도」 구분. BUY 만 매수다. */
    public boolean isBuy() {
        return kind == Kind.BUY;
    }

    /** trades 테이블의 「청산 여부」. */
    public boolean isLiquidation() {
        return kind == Kind.LIQUIDATION;
    }
}
