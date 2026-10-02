package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.marketdata.Interval;
import com.moneygame.scenario.Scenario;
import com.moneygame.user.UserController;
import com.moneygame.user.UserService;
import com.moneygame.web.JsonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M6 REST. 서비스는 가짜로 두고 요청·응답 형식과 오류 매핑만 본다. */
@WebMvcTest({RoomController.class, UserController.class})
@Import(JsonConfig.class)
@DisplayName("M6 방 REST")
class RoomControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoomService rooms;
    @MockitoBean UserService users;

    private static RoomView waiting(String id) {
        return new RoomView(id, RoomStatus.WAITING, GameMode.DAILY, 4, "7",
                List.of(new RoomView.Participant("7", "철수", false)), List.of(), -1, 0);
    }

    @Test
    void 닉네임으로_입장하면_사용자_ID_를_돌려준다() throws Exception {
        when(users.registerOrGet("철수")).thenReturn(new UserService.User(7, "철수"));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"철수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nickname").value("철수"));
    }

    @Test
    void 방을_만들면_만든_사람이_방장으로_들어간다() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        when(rooms.create(eq("7"), eq("철수"), any())).thenReturn(waiting("ab12cd34"));

        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ab12cd34"))
                .andExpect(jsonPath("$.hostUserId").value("7"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void userId_가_없으면_400() throws Exception {
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DAILY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void 모드가_잘못되면_400() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"WEEKLY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지금_할_수_없는_요청은_409_없는_방은_404() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        when(rooms.join("full", "7", "철수")).thenThrow(new IllegalStateException("방이 가득 찼습니다: 4명"));
        when(rooms.view("nope")).thenThrow(new NoSuchElementException("방이 없습니다: nope"));

        mvc.perform(post("/api/rooms/full/join").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("방이 가득 찼습니다: 4명"));
        mvc.perform(get("/api/rooms/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 금액은_문자열로_나간다() throws Exception {
        GameResult result = new GameResult(List.of(new GameResult.Rank(1, "7", "철수",
                new BigDecimal("100000000"), new BigDecimal("0.0000"), 0, 0)), List.of());
        Scenario scenario = new Scenario(8, "개발용", Interval.ONE_DAY, LocalDateTime.of(2024, 5, 9, 0, 0),
                LocalDateTime.of(2025, 5, 8, 0, 0), 241, List.of(new Scenario.ScenarioSymbol("A", "005930", "삼성전자")));
        when(rooms.result("r1")).thenReturn(new RoomResult("r1", result, scenario));

        mvc.perform(get("/api/rooms/r1/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rankings[0].totalAsset").value("100000000"))
                .andExpect(jsonPath("$.result.rankings[0].returnRate").value("0.0000"))
                .andExpect(jsonPath("$.scenario.symbols[0].name").value("삼성전자"));
    }
}
