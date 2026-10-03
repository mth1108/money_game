package com.moneygame.result;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.Trade;
import com.moneygame.marketdata.SymbolRepository;
import com.moneygame.room.FinishedGame;
import com.moneygame.room.GameMode;
import com.moneygame.scenario.NewsEventRepository;
import com.moneygame.scenario.ScenarioRepository;
import com.moneygame.scenario.ScenarioService;
import com.moneygame.scenario.ScenarioSymbolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 결과 기록과 조회. 로컬 MySQL 이 필요하다 — ./gradlew integrationTest
 *
 * M8 완료 판정 「판이 끝나면 결과 화면에서 내 거래를 시간 순으로 볼 수 있다」의 서버 쪽을 확인한다.
 * 임시 종목·사용자·시나리오를 넣고 트랜잭션 롤백으로 지운다.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("M8 결과 기록 (MySQL)")
class GameRecordIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired GameRoomRepository rooms;
    @Autowired GameParticipantRepository participants;
    @Autowired TradeRepository trades;
    @Autowired SymbolRepository symbols;
    @Autowired ScenarioRepository scenarioRepository;
    @Autowired ScenarioSymbolRepository scenarioSymbols;
    @Autowired NewsEventRepository news;

    private GameRecordService recorder;
    private ResultService results;
    private ScenarioService scenarios;
    private long userId;
    private long scenarioId;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO symbols (code, name, market) VALUES ('ZZRA', '테스트A', 'KRX'), ('ZZRB', '테스트B', 'KRX')");
        jdbc.update("INSERT INTO users (nickname, created_at) VALUES ('zz결과테스터', NOW())");
        userId = jdbc.queryForObject("SELECT id FROM users WHERE nickname = 'zz결과테스터'", Long.class);
        jdbc.update("INSERT INTO scenarios (title, bar_interval, start_ts, end_ts, bar_count, created_at) "
                + "VALUES ('zz결과용', '1d', '2024-01-01', '2024-08-28', 241, NOW())");
        scenarioId = jdbc.queryForObject("SELECT id FROM scenarios WHERE title = 'zz결과용'", Long.class);
        jdbc.update("INSERT INTO scenario_symbols (scenario_id, label, symbol_id) "
                + "SELECT ?, 'A', id FROM symbols WHERE code = 'ZZRA'", scenarioId);
        jdbc.update("INSERT INTO scenario_symbols (scenario_id, label, symbol_id) "
                + "SELECT ?, 'B', id FROM symbols WHERE code = 'ZZRB'", scenarioId);

        scenarios = new ScenarioService(scenarioRepository, scenarioSymbols, news, symbols,
                (code, interval, from, to) -> List.of());
        recorder = new GameRecordService(rooms, participants, trades, symbols);
        results = new ResultService(rooms, participants, trades, scenarios);
    }

    /** 사람 1명(배율 3 청산) + 존버 봇 1명(B 를 끝까지 보유) */
    private FinishedGame game() {
        String human = Long.toString(userId);
        GameResult result = new GameResult(
                List.of(new GameResult.Rank(1, "bot-1", "존버봇", bd("103000000.12345678"), bd("0.0300"), 1, 0),
                        new GameResult.Rank(2, human, "zz결과테스터", bd("69781505.88333333"), bd("-0.3022"), 1, 1)),
                List.of(new Trade(0, human, "A", Trade.Kind.BUY, 1129, bd("79700"), bd("29993766.66666667"), 3, bd("134971.9500")),
                        new Trade(0, "bot-1", "B", Trade.Kind.BUY, 500, bd("199700"), bd("99850000"), 1, bd("149775.0000")),
                        new Trade(124, human, "A", Trade.Kind.LIQUIDATION, 1129, bd("53000"), bd("29993766.66666667"), 3, bd("89755.5000")),
                        new Trade(240, "bot-1", "B", Trade.Kind.SETTLEMENT, 500, bd("206000"), bd("99850000"), 1, bd("154500.0000"))));
        return new FinishedGame("ab12cd34", GameMode.DAILY, bd("100000000"), 240, scenarios.get(scenarioId),
                LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2024, 8, 28, 0, 0),
                LocalDateTime.of(2026, 10, 3, 9, 0), LocalDateTime.of(2026, 10, 3, 9, 4),
                List.of(new FinishedGame.Player(human, userId, "zz결과테스터", false),
                        new FinishedGame.Player("bot-1", null, "존버봇", true)),
                result);
    }

    @Test
    void 끝난_판을_기록하고_결과_상세로_읽는다() {
        long id = recorder.record(game());

        ResultService.GameRecord r = results.get(id);

        assertEquals("ab12cd34", r.roomCode());
        assertEquals("DAILY", r.mode());
        assertEquals(0, bd("100000000").compareTo(r.seedMoney()));
        assertEquals("zz결과용", r.scenario().title());
        assertEquals(240, r.totalTicks());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), r.periodStart(), "실제로 쓴 구간");
        assertEquals(LocalDateTime.of(2024, 8, 28, 0, 0), r.periodEnd());

        // 순위 순. 봇은 사용자 ID 없이 봇으로 표시 (2026-10-03 결정)
        assertEquals(List.of("bot-1", Long.toString(userId)), r.participants().stream().map(ResultService.Participant::playerKey).toList());
        ResultService.Participant bot = r.participants().get(0);
        assertTrue(bot.bot());
        assertNull(bot.userId());
        ResultService.Participant me = r.participants().get(1);
        assertFalse(me.bot());
        assertEquals(userId, me.userId());
        assertEquals(0, bd("69781505.88333333").compareTo(me.finalAsset()), "소수 8자리까지 그대로");
        assertEquals(0, bd("-0.3022").compareTo(me.returnRate()));
        assertEquals(1, me.liquidatedCount());
    }

    @Test
    void 체결_내역은_시간_순이고_실제_종목명이_붙는다() {
        long id = recorder.record(game());

        List<ResultService.TradeRow> rows = results.get(id).trades();

        assertEquals(List.of(0, 0, 124, 240), rows.stream().map(ResultService.TradeRow::tickIndex).toList());
        assertEquals(List.of("BUY", "BUY", "LIQUIDATION", "SETTLEMENT"), rows.stream().map(ResultService.TradeRow::kind).toList());
        ResultService.TradeRow liq = rows.get(2);
        assertEquals("zz결과테스터", liq.nickname());
        assertEquals("A", liq.symbolLabel());
        assertEquals("ZZRA", liq.symbolCode());
        assertEquals("테스트A", liq.symbolName(), "라벨이 가렸던 실제 종목을 공개한다");
        assertEquals(0, bd("29993766.66666667").compareTo(liq.margin()));
        assertEquals(3, liq.leverage());
        assertTrue(rows.get(3).bot());
    }

    @Test
    void 사용자별_전적이_나온다() {
        long first = recorder.record(game());
        long second = recorder.record(game());

        List<ResultService.HistoryEntry> history = results.history(userId);

        assertEquals(List.of(second, first), history.stream().map(ResultService.HistoryEntry::gameId).toList(), "최근 판부터");
        ResultService.HistoryEntry h = history.get(0);
        assertEquals(2, h.rank());
        assertEquals(2, h.playerCount());
        assertEquals("zz결과용", h.scenarioTitle());
        assertEquals(1, h.liquidatedCount());
        assertEquals(240, h.totalTicks());
    }

    @Test
    void 없는_결과는_NoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> results.get(-1));
    }
}
