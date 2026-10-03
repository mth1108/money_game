package com.moneygame.bot;

import com.moneygame.engine.OrderRequest;
import com.moneygame.position.LiquidationRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 존버 봇 — 0틱에 전액 매수(배율 1), 끝까지 보유. (CLAUDE.md §3 M9)
 *
 * 종목이 여럿이면 현금을 **종목 수로 균등하게 나눠** 모두 산다 (2026-10-03 결정).
 * 「아무것도 안 하고 시장을 따라간 결과」라는 기준선이다. 플레이어가 이기면 판단이 시장보다 나았다는 뜻이다.
 *
 * 수수료 정책(§1.5)상 판 종료 정리에도 수수료가 붙으므로, 끝까지 버티는 이 봇이 구조적으로 유리하지 않다.
 */
public final class HoldBot implements Bot {

    public static final String NAME = "존버봇";

    @Override
    public List<OrderRequest> onStart(BotContext ctx) {
        if (!ctx.leverages().contains(1) || ctx.labels().isEmpty()) {
            return List.of();
        }
        // 종목당 예산 = 현금 / 종목 수 / (1 + 수수료율), 정수로 내림한 뒤 1원 더 뺀다.
        // 배율 1 이면 실제 차감 = 수량 x 가격 x (1 + 수수료율) 이고 수수료는 소수 4자리 올림이라,
        // 1원 여유를 두면 마지막 종목까지 현금 부족 없이 체결된다.
        BigDecimal perSymbol = ctx.cash()
                .divide(BigDecimal.valueOf(ctx.labels().size()).multiply(BigDecimal.ONE.add(LiquidationRule.FEE_RATE)),
                        0, RoundingMode.DOWN)
                .subtract(BigDecimal.ONE);
        List<OrderRequest> orders = new ArrayList<>();
        for (String label : ctx.labels()) {
            BigDecimal price = ctx.prices().get(label);
            // 1주 값도 안 되면 그 종목은 건너뛴다 (엔진이 「증거금 부족」으로 거부할 주문을 내지 않는다)
            if (price != null && perSymbol.compareTo(price) >= 0) {
                orders.add(OrderRequest.buy(ctx.playerId(), label, perSymbol, 1));
            }
        }
        return orders;
    }
}
