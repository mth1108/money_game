package com.moneygame.result;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.Trade;
import com.moneygame.marketdata.SymbolRepository;
import com.moneygame.result.entity.GameParticipantEntity;
import com.moneygame.result.entity.GameRoomEntity;
import com.moneygame.result.entity.TradeEntity;
import com.moneygame.room.FinishedGame;
import com.moneygame.room.GameRecorder;
import com.moneygame.scenario.Scenario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 끝난 판을 기록한다. (CLAUDE.md §3 M8)
 *
 * 방·참가자·체결 내역을 **한 트랜잭션에 일괄 INSERT** 한다. 판이 끝나는 순간에만 불린다 (§1.2).
 * 상태 UPDATE 는 없다 — 진행 중 상태는 메모리(GameSession)에만 있었다 (§1.3).
 */
@Service
public class GameRecordService implements GameRecorder {

    private final GameRoomRepository rooms;
    private final GameParticipantRepository participants;
    private final TradeRepository trades;
    private final SymbolRepository symbols;

    public GameRecordService(GameRoomRepository rooms, GameParticipantRepository participants,
                             TradeRepository trades, SymbolRepository symbols) {
        this.rooms = rooms;
        this.participants = participants;
        this.trades = trades;
        this.symbols = symbols;
    }

    @Override
    @Transactional
    public long record(FinishedGame game) {
        // 라벨이 가린 실제 종목. 체결 내역에 종목 ID 로 남긴다
        Map<String, Long> symbolIdByLabel = new HashMap<>();
        for (Scenario.ScenarioSymbol s : game.scenario().symbols()) {
            Long id = symbols.findByCode(s.code())
                    .orElseThrow(() -> new IllegalStateException("symbols 에 없는 종목입니다: " + s.code()))
                    .getId();
            symbolIdByLabel.put(s.label(), id);
        }

        GameRoomEntity room = rooms.save(new GameRoomEntity(game.roomCode(), game.scenario().id(),
                game.mode().name(), game.seedMoney(), game.totalTicks(), game.periodStart(), game.periodEnd(),
                game.startedAt(), game.finishedAt()));

        Map<String, GameResult.Rank> rankByPlayer = new HashMap<>();
        for (GameResult.Rank r : game.result().rankings()) {
            rankByPlayer.put(r.userId(), r);
        }
        Map<String, Long> participantIdByPlayer = new HashMap<>();
        for (FinishedGame.Player p : game.players()) {
            GameResult.Rank r = rankByPlayer.get(p.playerKey());
            if (r == null) {
                throw new IllegalStateException("순위에 없는 참가자입니다: " + p.playerKey());
            }
            GameParticipantEntity saved = participants.save(new GameParticipantEntity(room.getId(), p.playerKey(),
                    p.userId(), p.nickname(), p.bot(), r.rank(), r.totalAsset(), r.returnRate(),
                    r.tradeCount(), r.liquidatedCount()));
            participantIdByPlayer.put(p.playerKey(), saved.getId());
        }

        List<TradeEntity> rows = new ArrayList<>();
        for (Trade t : game.result().trades()) {
            rows.add(new TradeEntity(room.getId(), participantIdByPlayer.get(t.userId()), t.tickIndex(),
                    t.symbolLabel(), symbolIdByLabel.get(t.symbolLabel()), t.kind().name(), t.quantity(),
                    t.price(), t.margin(), t.leverage(), t.fee()));
        }
        trades.saveAll(rows);
        return room.getId();
    }
}
