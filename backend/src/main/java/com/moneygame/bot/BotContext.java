package com.moneygame.bot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 봇이 판단에 쓰는 순간 정보. 사람이 화면에서 보는 것과 같은 수준이다.
 *
 * @param playerId  엔진에서 이 봇의 플레이어 ID
 * @param tickIndex 지금 틱. 판 시작 직후는 0
 * @param prices    라벨별 현재가
 * @param cash      이 봇의 현금
 * @param leverages 이 판에서 허용된 배율
 */
public record BotContext(String playerId,
                         int tickIndex,
                         List<String> labels,
                         Map<String, BigDecimal> prices,
                         BigDecimal cash,
                         Set<Integer> leverages) {
}
