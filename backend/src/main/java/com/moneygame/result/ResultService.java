package com.moneygame.result;

import com.moneygame.result.entity.GameParticipantEntity;
import com.moneygame.result.entity.GameRoomEntity;
import com.moneygame.result.entity.TradeEntity;
import com.moneygame.scenario.Scenario;
import com.moneygame.scenario.ScenarioService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 결과 상세와 사용자별 전적 조회. (CLAUDE.md §3 M8)
 * 결과 상세는 실제 종목명을 공개한다 — 게임 중에는 라벨만 보였다.
 */
@Service
public class ResultService {

    /** 전적은 최근 판부터 이만큼만 */
    static final int HISTORY_LIMIT = 50;

    private final GameRoomRepository rooms;
    private final GameParticipantRepository participants;
    private final TradeRepository trades;
    private final ScenarioService scenarios;

    public ResultService(GameRoomRepository rooms, GameParticipantRepository participants,
                         TradeRepository trades, ScenarioService scenarios) {
        this.rooms = rooms;
        this.participants = participants;
        this.trades = trades;
        this.scenarios = scenarios;
    }

    /**
     * 결과 상세. 순위·수익률·체결 내역(시간 순)·청산 내역·실제 종목명.
     *
     * @param periodStart 실제로 쓴 시세 구간의 시작 (KST)
     * @param periodEnd   실제로 쓴 시세 구간의 끝. 짧은 판이면 시나리오 기간보다 짧다
     */
    public record GameRecord(long id, String roomCode, String mode, BigDecimal seedMoney, int totalTicks,
                             LocalDateTime periodStart, LocalDateTime periodEnd,
                             LocalDateTime startedAt, LocalDateTime finishedAt, Scenario scenario,
                             List<Participant> participants, List<TradeRow> trades) {
    }

    public record Participant(String playerKey, Long userId, String nickname, boolean bot, int rank,
                              BigDecimal finalAsset, BigDecimal returnRate, int tradeCount, int liquidatedCount) {
    }

    /** @param kind BUY | SELL | LIQUIDATION | SETTLEMENT */
    public record TradeRow(int tickIndex, String playerKey, String nickname, boolean bot, String symbolLabel,
                           String symbolCode, String symbolName, String kind, long quantity, BigDecimal price,
                           BigDecimal margin, int leverage, BigDecimal fee) {
    }

    /** 전적 한 줄 */
    public record HistoryEntry(long gameId, LocalDateTime finishedAt, String mode, int totalTicks, String scenarioTitle,
                               int rank, long playerCount, BigDecimal finalAsset, BigDecimal returnRate,
                               int liquidatedCount) {
    }

    @Transactional(readOnly = true)
    public GameRecord get(long gameId) {
        GameRoomEntity room = rooms.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("결과가 없습니다: " + gameId));
        Scenario scenario = scenarios.get(room.getScenarioId());
        Map<String, Scenario.ScenarioSymbol> symbolByLabel = new HashMap<>();
        scenario.symbols().forEach(s -> symbolByLabel.put(s.label(), s));

        List<Participant> list = new ArrayList<>();
        Map<Long, GameParticipantEntity> byId = new HashMap<>();
        for (GameParticipantEntity p : participants.findByGameIdOrderByFinalRankAscIdAsc(gameId)) {
            byId.put(p.getId(), p);
            list.add(new Participant(p.getPlayerKey(), p.getUserId(), p.getNickname(), p.isBot(), p.getFinalRank(),
                    p.getFinalAsset(), p.getReturnRate(), p.getTradeCount(), p.getLiquidatedCount()));
        }

        List<TradeRow> rows = new ArrayList<>();
        for (TradeEntity t : trades.findByGameIdOrderByTickIndexAscIdAsc(gameId)) {
            GameParticipantEntity p = byId.get(t.getParticipantId());
            Scenario.ScenarioSymbol s = symbolByLabel.get(t.getSymbolLabel());
            rows.add(new TradeRow(t.getTickIndex(), p.getPlayerKey(), p.getNickname(), p.isBot(), t.getSymbolLabel(),
                    s == null ? null : s.code(), s == null ? null : s.name(), t.getKind(), t.getQuantity(),
                    t.getPrice(), t.getMargin(), t.getLeverage(), t.getFee()));
        }
        return new GameRecord(room.getId(), room.getRoomCode(), room.getMode(), room.getSeedMoney(),
                room.getTotalTicks(), room.getPeriodStart(), room.getPeriodEnd(),
                room.getStartedAt(), room.getFinishedAt(), scenario,
                List.copyOf(list), List.copyOf(rows));
    }

    /** 사용자별 전적. 최근 판부터 50판 */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(long userId) {
        List<HistoryEntry> list = new ArrayList<>();
        for (GameParticipantEntity p : participants.findByUserIdOrderByGameIdDesc(userId,
                PageRequest.of(0, HISTORY_LIMIT))) {
            GameRoomEntity room = rooms.findById(p.getGameId()).orElseThrow();
            list.add(new HistoryEntry(room.getId(), room.getFinishedAt(), room.getMode(), room.getTotalTicks(),
                    scenarios.get(room.getScenarioId()).title(), p.getFinalRank(),
                    participants.countByGameId(room.getId()), p.getFinalAsset(), p.getReturnRate(),
                    p.getLiquidatedCount()));
        }
        return list;
    }
}
