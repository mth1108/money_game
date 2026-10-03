package com.moneygame.result;

import com.moneygame.marketdata.Interval;
import com.moneygame.scenario.Scenario;
import com.moneygame.web.JsonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M8 결과 REST. 서비스는 가짜로 두고 응답 형식만 본다. */
@WebMvcTest(ResultController.class)
@Import(JsonConfig.class)
@DisplayName("M8 결과 REST")
class ResultControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ResultService results;

    @Test
    void 결과_상세는_실제_종목명과_시간_순_체결을_담고_금액은_문자열이다() throws Exception {
        Scenario scenario = new Scenario(8, "개발용", Interval.ONE_DAY, LocalDateTime.of(2024, 5, 9, 0, 0),
                LocalDateTime.of(2025, 5, 8, 0, 0), 241, List.of(new Scenario.ScenarioSymbol("A", "005930", "삼성전자")));
        when(results.get(1)).thenReturn(new ResultService.GameRecord(1, "ab12cd34", "DAILY", new BigDecimal("100000000"), 240,
                LocalDateTime.of(2026, 10, 3, 9, 0), LocalDateTime.of(2026, 10, 3, 9, 4), scenario,
                List.of(new ResultService.Participant("7", 7L, "철수", false, 1, new BigDecimal("69781505.88333333"),
                        new BigDecimal("-0.3022"), 1, 1)),
                List.of(new ResultService.TradeRow(0, "7", "철수", false, "A", "005930", "삼성전자", "BUY", 1129,
                        new BigDecimal("79700"), new BigDecimal("29993766.66666667"), 3, new BigDecimal("134971.9500")))));

        mvc.perform(get("/api/results/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].finalAsset").value("69781505.88333333"))
                .andExpect(jsonPath("$.participants[0].bot").value(false))
                .andExpect(jsonPath("$.trades[0].symbolName").value("삼성전자"))
                .andExpect(jsonPath("$.trades[0].margin").value("29993766.66666667"))
                .andExpect(jsonPath("$.scenario.symbols[0].name").value("삼성전자"));
    }

    @Test
    void 전적은_최근_판부터() throws Exception {
        when(results.history(7)).thenReturn(List.of(
                new ResultService.HistoryEntry(12, LocalDateTime.of(2026, 10, 3, 9, 4), "DAILY", "개발용", 2, 2,
                        new BigDecimal("69781505.88333333"), new BigDecimal("-0.3022"), 1)));

        mvc.perform(get("/api/users/7/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value(12))
                .andExpect(jsonPath("$[0].rank").value(2))
                .andExpect(jsonPath("$[0].returnRate").value("-0.3022"));
    }

    @Test
    void 없는_결과는_404() throws Exception {
        when(results.get(99)).thenThrow(new NoSuchElementException("결과가 없습니다: 99"));
        mvc.perform(get("/api/results/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("결과가 없습니다: 99"));
    }
}
