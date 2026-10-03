package com.moneygame.room;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 판 시작 정보. M7 의 GAME_START 재료다 (종목 라벨, 초기 캔들, 시드머니, 허용 배율).
 *
 * @param history     라벨별 시작 전 과거 봉 (오래된 순, 차트 배경). 시각은 담지 않는다
 * @param initialBars 0틱(시작가) 캔들. 시각은 담지 않는다 (Bar 참고)
 */
public record GameStartInfo(GameMode mode,
                            List<String> labels,
                            Map<String, List<Bar>> history,
                            Map<String, Bar> initialBars,
                            BigDecimal seedMoney,
                            Set<Integer> leverages,
                            int totalTicks,
                            Map<String, PlayerSnapshot> players) {
}
