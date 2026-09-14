package com.moneygame.engine;

import com.moneygame.position.LiquidationRule;
import com.moneygame.position.PlayerState;
import com.moneygame.position.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 한 판의 진행 전체. 화면과 네트워크를 모른다. (CLAUDE.md M4)
 *
 * Spring 빈이 아니라 일반 객체다. 방 하나당 하나씩 만든다.
 * DB·WebSocket·Spring 어노테이션을 참조하지 않는다.
 *
 * tick() 과 submitOrder() 는 같은 스레드에서 직렬로 호출되어야 한다 (§1.6).
 * 이 클래스는 스스로 동기화하지 않는다 — 직렬 호출 보장은 M6 의 책임이다.
 *
 * 시세는 라벨별 종가 배열로 받는다. 캔들을 종가 배열로 바꾸는 일은 이 모듈
 * 밖(M3/M6)의 몫이라, engine 이 marketdata 를 참조하지 않는다.
 */
public final class GameSession {

    public enum Status { READY, RUNNING, FINISHED }

    private final List<String> symbolLabels;
    private final Map<String, List<BigDecimal>> priceSeries;
    private final Map<Integer, List<String>> newsByTick;
    private final BigDecimal seedMoney;
    private final Set<Integer> allowedLeverages;
    private final int totalTicks;

    private final Map<String, PlayerState> players = new LinkedHashMap<>();

    private Status status = Status.READY;
    private int tickIndex = -1;

    public GameSession(List<String> symbolLabels,
                       Map<String, List<BigDecimal>> priceSeries,
                       BigDecimal seedMoney,
                       Set<Integer> allowedLeverages,
                       int totalTicks) {
        this(symbolLabels, priceSeries, seedMoney, allowedLeverages, totalTicks, Map.of());
    }

    public GameSession(List<String> symbolLabels,
                       Map<String, List<BigDecimal>> priceSeries,
                       BigDecimal seedMoney,
                       Set<Integer> allowedLeverages,
                       int totalTicks,
                       Map<Integer, List<String>> newsByTick) {
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("totalTicks 는 1 이상이어야 합니다: " + totalTicks);
        }
        if (seedMoney == null || seedMoney.signum() <= 0) {
            throw new IllegalArgumentException("seedMoney 는 0 보다 커야 합니다");
        }
        for (String label : symbolLabels) {
            List<BigDecimal> series = priceSeries.get(label);
            if (series == null || series.size() < totalTicks + 1) {
                throw new IllegalArgumentException(
                        "시세가 부족합니다: " + label + " — " + (totalTicks + 1) + "개가 필요합니다");
            }
        }
        this.symbolLabels = List.copyOf(symbolLabels);
        this.priceSeries = Map.copyOf(priceSeries);
        this.seedMoney = seedMoney;
        this.allowedLeverages = Set.copyOf(allowedLeverages);
        this.totalTicks = totalTicks;
        this.newsByTick = Map.copyOf(newsByTick);
    }

    public void addPlayer(String userId, String nickname) {
        if (status != Status.READY) {
            throw new IllegalStateException("시작한 뒤에는 참가할 수 없습니다");
        }
        players.put(userId, new PlayerState(userId, nickname, seedMoney));
    }

    /** 초기 자산 배분은 PlayerState 생성 시 끝난다. 여기서는 0틱을 세운다. */
    public void start() {
        if (status != Status.READY) {
            throw new IllegalStateException("이미 시작했습니다");
        }
        status = Status.RUNNING;
        tickIndex = 0;
    }

    /**
     * 한 틱 진행. CLAUDE.md M4 의 순서를 그대로 따른다.
     * 1) tickIndex++  2) 현재가 갱신  3) 청산 판정  4) 총자산 재계산
     * 5) 뉴스  6) TickResult 반환
     */
    public TickResult tick() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("진행 중인 판이 아닙니다: " + status);
        }
        if (tickIndex >= totalTicks) {
            throw new IllegalStateException("판 길이를 넘었습니다: " + tickIndex + "/" + totalTicks);
        }

        tickIndex++;
        Map<String, BigDecimal> prices = currentPrices();

        List<TickResult.Liquidation> liquidations = new ArrayList<>();
        for (PlayerState player : players.values()) {
            for (String label : new ArrayList<>(player.positions().keySet())) {
                Position position = player.positions().get(label);
                BigDecimal price = prices.get(label);
                if (price != null && position.isLiquidatedAt(price)) {
                    // 증거금은 이미 차감돼 있다. 돌려주지 않는 것이 곧 전액 소멸이다.
                    // 수수료는 종료 사유를 불문하고 1회 부과한다 (§1.5 수수료 정책).
                    player.removePosition(label);
                    BigDecimal fee = player.chargeFee(
                            LiquidationRule.fee(position.quantity(), price));
                    player.countLiquidation();
                    liquidations.add(new TickResult.Liquidation(
                            player.userId(), label, price, position.margin(), fee));
                }
            }
        }

        // 총자산은 파생값이라 저장하지 않는다. PlayerState.totalAsset() 으로 언제든 구한다.

        List<String> news = newsByTick.getOrDefault(tickIndex, List.of());

        return new TickResult(tickIndex, prices, List.copyOf(liquidations),
                List.copyOf(news), tickIndex >= totalTicks);
    }

    public OrderResult submitOrder(OrderRequest order) {
        if (status != Status.RUNNING) {
            return OrderResult.reject(OrderResult.RejectReason.NOT_RUNNING, order.symbolLabel());
        }
        PlayerState player = players.get(order.userId());
        if (player == null) {
            return OrderResult.reject(OrderResult.RejectReason.UNKNOWN_PLAYER, order.symbolLabel());
        }
        if (!priceSeries.containsKey(order.symbolLabel())) {
            return OrderResult.reject(OrderResult.RejectReason.UNKNOWN_SYMBOL, order.symbolLabel());
        }
        return order.action() == OrderRequest.Action.BUY
                ? buy(player, order)
                : sell(player, order);
    }

    private OrderResult buy(PlayerState player, OrderRequest order) {
        String label = order.symbolLabel();
        if (order.margin() == null || order.margin().signum() <= 0) {
            return OrderResult.reject(OrderResult.RejectReason.INVALID_MARGIN, label);
        }
        if (!allowedLeverages.contains(order.leverage())) {
            return OrderResult.reject(OrderResult.RejectReason.LEVERAGE_NOT_ALLOWED, label);
        }
        if (player.positions().containsKey(label)) {
            return OrderResult.reject(OrderResult.RejectReason.POSITION_ALREADY_EXISTS, label);
        }

        BigDecimal price = currentPrice(label);
        long quantity = LiquidationRule.quantity(order.margin(), order.leverage(), price);
        if (quantity == 0L) {
            return OrderResult.reject(OrderResult.RejectReason.INSUFFICIENT_MARGIN, label);
        }

        BigDecimal actualMargin = LiquidationRule.actualMargin(quantity, price, order.leverage());
        BigDecimal fee = LiquidationRule.fee(quantity, price);
        if (player.cash().compareTo(actualMargin.add(fee)) < 0) {
            return OrderResult.reject(OrderResult.RejectReason.INSUFFICIENT_CASH, label);
        }

        player.deduct(actualMargin.add(fee));
        player.openPosition(Position.open(label, price, quantity, order.leverage()));
        player.countTrade();
        return OrderResult.accept(label, quantity, price, actualMargin, fee);
    }

    private OrderResult sell(PlayerState player, OrderRequest order) {
        String label = order.symbolLabel();
        Position position = player.positions().get(label);
        if (position == null) {
            return OrderResult.reject(OrderResult.RejectReason.NO_POSITION, label);
        }

        BigDecimal price = currentPrice(label);

        player.removePosition(label);
        player.add(position.value(price));
        BigDecimal fee = player.chargeFee(LiquidationRule.fee(position.quantity(), price));
        player.countTrade();
        return OrderResult.accept(label, position.quantity(), price, position.margin(), fee);
    }

    /**
     * 종료. 남은 포지션을 현재가로 정리하고 순위를 낸다.
     *
     * 정리에도 매도와 똑같이 수수료를 부과한다. 면제하면 마지막 틱까지 버티는 쪽이
     * 항상 유리해져 「팔지 말지」라는 판단 자체가 사라진다 (§1.5 수수료 정책).
     */
    public GameResult finish() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("진행 중인 판이 아닙니다: " + status);
        }

        Map<String, BigDecimal> prices = currentPrices();
        for (PlayerState player : players.values()) {
            for (String label : new ArrayList<>(player.positions().keySet())) {
                Position position = player.removePosition(label);
                BigDecimal price = prices.get(label);
                if (price != null) {
                    player.add(position.value(price));
                    player.chargeFee(LiquidationRule.fee(position.quantity(), price));
                }
            }
        }
        status = Status.FINISHED;

        List<PlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparing(PlayerState::cash).reversed());

        List<GameResult.Rank> ranks = new ArrayList<>();
        int rank = 1;
        for (PlayerState player : sorted) {
            BigDecimal total = player.cash();
            BigDecimal returnRate = total.subtract(seedMoney)
                    .divide(seedMoney, LiquidationRule.CALC_SCALE, RoundingMode.HALF_UP)
                    .setScale(LiquidationRule.PRICE_SCALE, RoundingMode.HALF_UP);
            ranks.add(new GameResult.Rank(rank++, player.userId(), player.nickname(),
                    total, returnRate, player.tradeCount(), player.liquidatedCount()));
        }
        return new GameResult(List.copyOf(ranks));
    }

    public BigDecimal currentPrice(String symbolLabel) {
        List<BigDecimal> series = priceSeries.get(symbolLabel);
        if (series == null) {
            throw new IllegalArgumentException("모르는 종목입니다: " + symbolLabel);
        }
        return series.get(Math.max(tickIndex, 0));
    }

    private Map<String, BigDecimal> currentPrices() {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (String label : symbolLabels) {
            prices.put(label, currentPrice(label));
        }
        return Collections.unmodifiableMap(prices);
    }

    public Status status() { return status; }
    public int tickIndex() { return tickIndex; }
    public int totalTicks() { return totalTicks; }
    public BigDecimal seedMoney() { return seedMoney; }
    public List<String> symbolLabels() { return symbolLabels; }
    public Set<Integer> allowedLeverages() { return allowedLeverages; }
    public PlayerState player(String userId) { return players.get(userId); }
    public Map<String, PlayerState> players() { return Collections.unmodifiableMap(players); }
}
