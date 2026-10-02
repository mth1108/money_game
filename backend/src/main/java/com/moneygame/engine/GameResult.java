package com.moneygame.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 판 종료 결과. M8 이 이 값을 한 트랜잭션으로 DB 에 적재한다.
 *
 * @param rankings 총자산 내림차순. 동점이면 같은 순위이고 다음 순위는 건너뛴다 (1, 1, 3)
 * @param trades   판 전체의 체결 내역. 시간 순이다 (종료 정리 포함)
 */
public record GameResult(List<Rank> rankings, List<Trade> trades) {

    public record Rank(int rank,
                       String userId,
                       String nickname,
                       BigDecimal totalAsset,
                       BigDecimal returnRate,
                       int tradeCount,
                       int liquidatedCount) {
    }
}
