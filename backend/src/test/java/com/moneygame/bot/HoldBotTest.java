package com.moneygame.bot;

import com.moneygame.engine.GameSession;
import com.moneygame.engine.OrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M9 존버 봇 — 0틱에 종목마다 균등 분할로 전액 매수(배율 1), 끝까지 보유. (CLAUDE.md §3 M9, 2026-10-03 결정) */
@DisplayName("M9 존버 봇")
class HoldBotTest {

    private static final BigDecimal SEED = new BigDecimal("100000000");
    private static final Set<Integer> DAILY = Set.of(1, 2, 3);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static BotContext ctx(BigDecimal cash, Map<String, BigDecimal> prices, Set<Integer> leverages) {
        return new BotContext("bot-1", 0, List.copyOf(prices.keySet()), prices, cash, leverages);
    }

    private static Map<String, BigDecimal> prices(String... pairs) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], bd(pairs[i + 1]));
        }
        return map;
    }

    @Test
    void 세_종목에_같은_예산을_배율_1로_건다() {
        List<OrderRequest> orders = new HoldBot().onStart(ctx(SEED, prices("A", "175400", "B", "79700", "C", "217038"), DAILY));

        assertEquals(List.of("A", "B", "C"), orders.stream().map(OrderRequest::symbolLabel).toList());
        // 1억 / (3 x 1.0015) = 33283408.22... -> 내림 33283408 -> 여유 1원 = 33283407
        for (OrderRequest o : orders) {
            assertEquals(OrderRequest.Action.BUY, o.action());
            assertEquals(1, o.leverage());
            assertEquals(bd("33283407"), o.margin());
            assertEquals("bot-1", o.userId());
        }
    }

    @Test
    void 엔진에서_모두_체결되고_현금이_음수가_되지_않는다() {
        // 가격이 예산을 정확히 나누는 최악의 경우도 넣는다 — 수수료 올림까지 감당해야 한다
        Map<String, BigDecimal> p = prices("A", "33283407", "B", "79700", "C", "217038");
        Map<String, List<BigDecimal>> series = new LinkedHashMap<>();
        p.forEach((label, price) -> series.put(label, new ArrayList<>(List.of(price, price))));
        GameSession game = new GameSession(List.copyOf(p.keySet()), series, SEED, DAILY, 1);
        game.addPlayer("bot-1", HoldBot.NAME);
        game.start();

        for (OrderRequest o : new HoldBot().onStart(ctx(SEED, p, DAILY))) {
            assertTrue(game.submitOrder(o).accepted(), "거부되면 안 된다: " + o.symbolLabel());
        }

        BigDecimal cash = game.player("bot-1").cash();
        assertEquals(3, game.player("bot-1").positions().size());
        assertTrue(cash.signum() >= 0, "현금이 음수가 되지 않는다: " + cash);
        assertTrue(cash.compareTo(bd("300000")) < 0, "거의 전액을 건다 — 남은 현금 " + cash);
    }

    @Test
    void 한_종목이면_그_종목에_전액() {
        List<OrderRequest> orders = new HoldBot().onStart(ctx(SEED, prices("A", "10000"), DAILY));

        assertEquals(1, orders.size());
        // 1억 / 1.0015 = 99850224.66... -> 99850224 -> 99850223
        assertEquals(bd("99850223"), orders.get(0).margin());
    }

    @Test
    void 예산으로_1주도_못_사는_종목은_건너뛴다() {
        // 100만 원 / 3 종목 -> 종목당 약 33만 원. B 는 1주에 200만 원
        List<OrderRequest> orders = new HoldBot().onStart(ctx(bd("1000000"),
                prices("A", "100000", "B", "2000000", "C", "50000"), DAILY));

        assertEquals(List.of("A", "C"), orders.stream().map(OrderRequest::symbolLabel).toList());
    }

    @Test
    void 배율_1이_허용되지_않으면_주문하지_않는다() {
        assertTrue(new HoldBot().onStart(ctx(SEED, prices("A", "10000"), Set.of(3, 5))).isEmpty());
    }

    @Test
    void 매_틱에는_아무것도_하지_않는다() {
        assertTrue(new HoldBot().onTick(ctx(SEED, prices("A", "10000"), DAILY)).isEmpty(), "끝까지 보유한다");
    }
}
