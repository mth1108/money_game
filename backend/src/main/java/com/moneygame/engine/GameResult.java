package com.moneygame.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 판 종료 결과. M8 이 이 값을 한 트랜잭션으로 DB 에 적재한다.
 */
public record GameResult(List<Rank> rankings) {

    public record Rank(int rank,
                       String userId,
                       String nickname,
                       BigDecimal totalAsset,
                       BigDecimal returnRate,
                       int tradeCount,
                       int liquidatedCount) {
    }
}
