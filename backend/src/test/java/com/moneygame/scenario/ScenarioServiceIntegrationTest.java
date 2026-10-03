package com.moneygame.scenario;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.GameSession;
import com.moneygame.engine.OrderRequest;
import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.CsvPriceDataProvider;
import com.moneygame.marketdata.Interval;
import com.moneygame.marketdata.SymbolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3 시나리오 서비스 통합 테스트. 로컬 MySQL 이 필요하다 — ./gradlew integrationTest
 *
 * M3 완료 판정 「시나리오 ID 를 주면 캔들 배열 N개 + 이벤트 목록이 나온다」를 확인한다.
 * 임시 종목(ZZ*)과 임시 CSV 로 검증하고, 트랜잭션 롤백으로 개발 DB 에 흔적을 남기지 않는다.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("M3 ScenarioService (MySQL)")
class ScenarioServiceIntegrationTest {

    private static final List<String> CODES = List.of("ZZSA", "ZZSB", "ZZSC");
    private static final LocalDateTime DAY0 = LocalDateTime.of(2024, 1, 1, 0, 0);

    @Autowired JdbcTemplate jdbc;
    @Autowired ScenarioRepository scenarios;
    @Autowired ScenarioSymbolRepository scenarioSymbols;
    @Autowired NewsEventRepository newsEvents;
    @Autowired SymbolRepository symbols;

    @TempDir Path dir;

    private ScenarioService service;

    @BeforeEach
    void setUp() throws IOException {
        for (String code : CODES) {
            jdbc.update("INSERT INTO symbols (code, name, market) VALUES (?, ?, 'KRX')", code, "테스트" + code);
        }
        // 세 종목 모두 같은 날짜 300봉. 각각 다른 폭으로 오른다 (모두 적격)
        TestCandles.writeCsv(dir, "ZZSA", "1d", TestCandles.daily(DAY0, 300, 10000, 0.60));
        TestCandles.writeCsv(dir, "ZZSB", "1d", TestCandles.daily(DAY0, 300, 50000, 0.90));
        TestCandles.writeCsv(dir, "ZZSC", "1d", TestCandles.daily(DAY0, 300, 300000, -0.50));
        service = new ScenarioService(scenarios, scenarioSymbols, newsEvents, symbols, new CsvPriceDataProvider(dir));
    }

    private Scenario register() {
        return service.register("테스트 시나리오", Interval.ONE_DAY, CODES, DAY0.plusDays(10), new SplittableRandom(42));
    }

    @Test
    void 등록하면_241봉_구간과_무작위_라벨이_붙는다() {
        Scenario s = register();

        assertEquals(DAY0.plusDays(10), s.start());
        assertEquals(DAY0.plusDays(250), s.end(), "시작 + 240일");
        assertEquals(241, s.barCount());
        assertEquals(3, s.symbols().size());
        assertEquals(List.of("A", "B", "C"), s.labels(), "라벨 순으로 돌려준다");
        Set<String> codes = new HashSet<>();
        s.symbols().forEach(sym -> codes.add(sym.code()));
        assertEquals(Set.copyOf(CODES), codes);
    }

    @Test
    void 시나리오_ID_로_라벨별_241봉과_뉴스가_나온다() {
        Scenario s = register();
        jdbc.update("INSERT INTO news_events (scenario_id, tick_index, headline) VALUES (?, 5, '첫 뉴스')", s.id());
        jdbc.update("INSERT INTO news_events (scenario_id, tick_index, headline) VALUES (?, 5, '같은 틱 두 번째')", s.id());
        jdbc.update("INSERT INTO news_events (scenario_id, tick_index, headline) VALUES (?, 120, '중간 뉴스')", s.id());

        LoadedScenario loaded = service.load(s.id());

        assertEquals(List.of("A", "B", "C"), List.copyOf(loaded.candlesByLabel().keySet()));
        for (List<Candle> candles : loaded.candlesByLabel().values()) {
            assertEquals(241, candles.size());
        }
        assertEquals(Map.of(5, List.of("첫 뉴스", "같은 틱 두 번째"), 120, List.of("중간 뉴스")), loaded.newsByTick());

        // 라벨이 가리키는 종목의 종가가 그대로 들어간다
        Scenario.ScenarioSymbol a = s.symbols().get(0);
        List<Candle> expected = new CsvPriceDataProvider(dir).getCandles(a.code(), Interval.ONE_DAY, s.start(), s.end());
        List<BigDecimal> closesA = loaded.closeSeries().get("A");
        assertEquals(241, closesA.size());
        assertEquals(0, expected.get(0).close().compareTo(closesA.get(0)));
        assertEquals(0, expected.get(240).close().compareTo(closesA.get(240)));
    }

    @Test
    void 적재한_시나리오로_엔진이_한_판을_끝까지_돈다() {
        LoadedScenario loaded = service.load(register().id());

        // 시드 1억 원 (2026-10-02 결정), 일봉 배율 1·2·3
        GameSession game = new GameSession(loaded.scenario().labels(), loaded.closeSeries(),
                new BigDecimal("100000000"), Set.of(1, 2, 3), ScenarioRules.TICKS, loaded.newsByTick());
        game.addPlayer("u1", "p1");
        game.start();
        assertTrue(game.submitOrder(OrderRequest.buy("u1", "A", new BigDecimal("10000000"), 2)).accepted());
        for (int i = 0; i < ScenarioRules.TICKS; i++) {
            game.tick();
        }
        GameResult r = game.finish();

        assertEquals(1, r.rankings().size());
        assertEquals(2, r.trades().size());
    }

    @Test
    void 뉴스가_없으면_빈_맵이다() {
        assertTrue(service.load(register().id()).newsByTick().isEmpty());
    }

    @Test
    void 무작위_선택은_같은_단위의_시나리오_중에서_고른다() {
        register();
        Scenario picked = service.pickRandom(Interval.ONE_DAY, 241, new SplittableRandom(7)).orElseThrow();
        assertEquals(Interval.ONE_DAY, picked.interval());
    }

    @Test
    void 적격하지_않은_구간은_등록하지_않는다() throws IOException {
        // ZZSC 를 횡보로 바꾼다 — 이동폭 0
        TestCandles.writeCsv(dir, "ZZSC", "1d", TestCandles.daily(DAY0, 300, 300000, 0.0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, this::register);
        assertTrue(e.getMessage().contains("ZZSC") && e.getMessage().contains("이동폭"), e.getMessage());
    }

    @Test
    void symbols_에_없는_종목은_등록하지_않는다() {
        assertThrows(IllegalArgumentException.class, () -> service.register("x", Interval.ONE_DAY,
                List.of("ZZSA", "ZZNONE"), DAY0.plusDays(10), new SplittableRandom(1)));
    }

    @Test
    void 종목별_시각이_어긋나면_등록하지_않는다() throws IOException {
        // ZZSB 만 20번째 날이 빠졌다
        List<Candle> b = new java.util.ArrayList<>(TestCandles.daily(DAY0, 300, 50000, 0.90));
        b.remove(20);
        TestCandles.writeCsv(dir, "ZZSB", "1d", b);
        assertThrows(IllegalStateException.class, this::register);
    }

    @Test
    void 등록_뒤_시세가_달라져_봉_수가_어긋나면_판을_시작하지_않는다() throws IOException {
        Scenario s = register();
        List<Candle> a = new java.util.ArrayList<>(TestCandles.daily(DAY0, 300, 10000, 0.60));
        a.remove(100);
        String codeA = s.symbols().stream().filter(sym -> sym.code().equals("ZZSA")).findFirst().orElseThrow().code();
        TestCandles.writeCsv(dir, codeA, "1d", a);

        assertThrows(IllegalStateException.class, () -> service.load(s.id()));
    }

    @Test
    void 종목은_1개부터_4개까지다() {
        assertThrows(IllegalArgumentException.class, () -> service.register("x", Interval.ONE_DAY,
                List.of(), DAY0.plusDays(10), new SplittableRandom(1)));
        Scenario one = service.register("한 종목", Interval.ONE_DAY, List.of("ZZSA"), DAY0.plusDays(10),
                new SplittableRandom(1));
        assertEquals(List.of("A"), one.labels());
    }
}
