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

    /**
     * 수수료를 부과하되 잔여 현금을 넘기지 않는다. 실제로 부과된 금액을 돌려준다.
     *
     * 증거금 전액 소멸이 이미 최대 페널티다. 그 위에 더 빼면 페널티 강화가 아니라
     * 계산의 부산물로 총자산이 음수가 되고, 「청산돼도 나머지 현금은 살아 있다」는
     * 증거금 격리가 깨진다 (CLAUDE.md §3 M5).
     *
     * cash >= 0 불변식은 여기서 지킨다.
     */
    public BigDecimal chargeFee(BigDecimal fee) {
        if (fee == null || fee.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal charged = fee.min(cash);
        if (charged.signum() < 0) {
            charged = BigDecimal.ZERO;
        }
        cash = cash.subtract(charged);
        return charged;
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
