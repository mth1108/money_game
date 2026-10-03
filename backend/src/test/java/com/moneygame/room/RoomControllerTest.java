package com.moneygame.room;

import com.moneygame.user.UserController;
import com.moneygame.user.UserService;
import com.moneygame.web.JsonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M6 REST. 서비스는 가짜로 두고 요청·응답 형식과 오류 매핑만 본다. */
@WebMvcTest({RoomController.class, UserController.class})
@Import(JsonConfig.class)
@EnableConfigurationProperties(RoomProperties.class)   // 슬라이스 테스트는 설정 빈을 자동 등록하지 않는다. application.yml 의 room.* 를 쓴다
@DisplayName("M6 방 REST")
class RoomControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoomService rooms;
    @MockitoBean UserService users;

    private static RoomView waiting(String id) {
        return new RoomView(id, RoomStatus.WAITING, GameMode.DAILY, 4, "7",
                List.of(new RoomView.Participant("7", "철수", false, false)), List.of(), -1, 0);
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
    void 시드머니를_보내면_방_설정에_들어가고_비우면_1억이다() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        when(rooms.create(eq("7"), eq("철수"), any())).thenReturn(waiting("r1"));
        ArgumentCaptor<RoomSettings> captor = ArgumentCaptor.forClass(RoomSettings.class);

        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\",\"seedMoney\":\"5000000\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\"}"))
                .andExpect(status().isOk());

        verify(rooms, times(2)).create(eq("7"), eq("철수"), captor.capture());
        assertEquals(new BigDecimal("5000000"), captor.getAllValues().get(0).seedMoney());
        assertEquals(RoomSettings.DEFAULT_SEED_MONEY, captor.getAllValues().get(1).seedMoney());
    }

    @Test
    void 봇_수를_보내면_방_설정에_들어가고_범위를_넘으면_400() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        when(rooms.create(eq("7"), eq("철수"), any())).thenReturn(waiting("r1"));
        ArgumentCaptor<RoomSettings> captor = ArgumentCaptor.forClass(RoomSettings.class);

        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\",\"maxPlayers\":2,\"bots\":1}"))
                .andExpect(status().isOk());
        verify(rooms).create(eq("7"), eq("철수"), captor.capture());
        assertEquals(1, captor.getValue().bots());

        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\",\"maxPlayers\":4,\"bots\":4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 방_설정_선택지를_내려준다() throws Exception {
        mvc.perform(get("/api/rooms/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTicks[0]").value(60))
                .andExpect(jsonPath("$.allowedTicks[2]").value(240))
                .andExpect(jsonPath("$.defaultTicks").value(240))
                .andExpect(jsonPath("$.modes[0].mode").value("DAILY"))
                .andExpect(jsonPath("$.modes[0].leverages[2]").value(3))
                .andExpect(jsonPath("$.modes[1].leverages[3]").value(10))
                .andExpect(jsonPath("$.maxBots").value(3))
                .andExpect(jsonPath("$.defaultSeedMoney").value("100000000"));
    }

    @Test
    void 판_길이를_보내면_방_설정에_들어가고_비우면_기본값이다() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        when(rooms.create(eq("7"), eq("철수"), any())).thenReturn(waiting("r1"));
        ArgumentCaptor<RoomSettings> captor = ArgumentCaptor.forClass(RoomSettings.class);

        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\",\"ticks\":60}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\"}"))
                .andExpect(status().isOk());

        verify(rooms, times(2)).create(eq("7"), eq("철수"), captor.capture());
        assertEquals(60, captor.getAllValues().get(0).ticks());
        assertEquals(240, captor.getAllValues().get(1).ticks(), "room.default-ticks");
    }

    @Test
    void 시드머니가_0_이하면_400() throws Exception {
        when(users.get(7)).thenReturn(new UserService.User(7, "철수"));
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"mode\":\"DAILY\",\"seedMoney\":\"0\"}"))
                .andExpect(status().isBadRequest());
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
}
