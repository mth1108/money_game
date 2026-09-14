package com.moneygame.position;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한 플레이어의 진행 중 상태. (CLAUDE.md M5)
 *
 * 이 상태는 서버 메모리에만 존재한다. DB 테이블로 만들지 않는다 (§1.3).
 * 봇도 사람과 같은 방식으로 이 객체를 갖는다 (M9).
 */
public final class PlayerState {

    private final String userId;
    private final String nickname;
    private final BigDecimal seedMoney;

    private BigDecimal cash;
    private final Map<String, Position> positions = new LinkedHashMap<>();
    private int tradeCount;
    private int liquidatedCount;

    public PlayerState(String userId, String nickname, BigDecimal seedMoney) {
        this.userId = userId;
        this.nickname = nickname;
        this.seedMoney = seedMoney;
        this.cash = seedMoney;
    }

    /** 총자산 = 현금 + 모든 포지션 가치. 순위 기준값이다. */
    public BigDecimal totalAsset(Map<String, BigDecimal> currentPrices) {
        BigDecimal total = cash;
        for (Position p : positions.values()) {
            BigDecimal price = currentPrices.get(p.symbolLabel());
            if (price != null) {
                total = total.add(p.value(price));
            }
        }
        return total;
    }

    public void deduct(BigDecimal amount) {
        cash = cash.subtract(amount);
    }

    public void add(BigDecimal amount) {
        cash = cash.add(amount);
    }

    public void openPosition(Position position) {
        positions.put(position.symbolLabel(), position);
    }

    public Position removePosition(String symbolLabel) {
        return positions.remove(symbolLabel);
    }

    public void countTrade() {
        tradeCount++;
    }

    public void countLiquidation() {
        liquidatedCount++;
    }

    public String userId() { return userId; }
    public String nickname() { return nickname; }
    public BigDecimal seedMoney() { return seedMoney; }
    public BigDecimal cash() { return cash; }
    public Map<String, Position> positions() { return Collections.unmodifiableMap(positions); }
    public int tradeCount() { return tradeCount; }
    public int liquidatedCount() { return liquidatedCount; }
}
