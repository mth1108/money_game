package com.moneygame.room;

import com.moneygame.position.PlayerState;
import com.moneygame.position.Position;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 한 플레이어의 순간 상태. 방의 실행 흐름 안에서 만들어 밖으로 넘긴다 —
 * PlayerState 는 가변이라 그대로 내보내면 다른 스레드에서 읽는 동안 바뀔 수 있다.
 * M7 의 PLAYER_STATE(본인)와 RANKING(전원, 포지션·배율 포함) 재료다.
 */
public record PlayerSnapshot(String userId,
                             String nickname,
                             BigDecimal cash,
                             BigDecimal totalAsset,
                             List<PositionView> positions,
                             int tradeCount,
                             int liquidatedCount) {

    public record PositionView(String symbolLabel,
                               Position.Side side,
                               BigDecimal entryPrice,
                               long quantity,
                               BigDecimal margin,
                               int leverage,
                               BigDecimal liquidationPrice,
                               BigDecimal unrealizedPnl,
                               BigDecimal value) {
    }

    static PlayerSnapshot of(PlayerState p, Map<String, BigDecimal> prices) {
        List<PositionView> positions = new ArrayList<>();
        for (Position pos : p.positions().values()) {
            BigDecimal price = prices.get(pos.symbolLabel());
            positions.add(new PositionView(pos.symbolLabel(), pos.side(), pos.entryPrice(), pos.quantity(),
                    pos.margin(), pos.leverage(), pos.liquidationPrice(),
                    pos.unrealizedPnl(price), pos.value(price)));
        }
        return new PlayerSnapshot(p.userId(), p.nickname(), p.cash(), p.totalAsset(prices),
                List.copyOf(positions), p.tradeCount(), p.liquidatedCount());
    }
}
