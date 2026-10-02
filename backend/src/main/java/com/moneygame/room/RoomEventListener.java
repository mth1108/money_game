package com.moneygame.room;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.OrderResult;
import com.moneygame.engine.TickResult;
import com.moneygame.scenario.Scenario;

import java.util.Map;

/**
 * 방에서 일어난 일을 바깥(M7 실시간 통신)에 알린다. M6 는 네트워크를 모른다 (§3 M6 「하지 않는 일」).
 *
 * 모든 호출은 그 방의 실행 흐름(단일 스레드)에서 일어난다. 오래 걸리는 일을 하면 틱이 밀린다.
 * 리스너가 예외를 던져도 게임은 계속된다.
 */
public interface RoomEventListener {

    /** 입장·퇴장·준비·시작·종료로 방 모습이 바뀌었다. ROOM_STATE */
    default void onRoomChanged(RoomView room) {
    }

    /** 판이 시작됐다. GAME_START */
    default void onGameStarted(String roomId, GameStartInfo start) {
    }

    /**
     * 한 틱이 지났다. TICK / PLAYER_STATE / RANKING / LIQUIDATED / NEWS 재료다.
     *
     * @param bars    라벨별 이번 틱 캔들 (시각 없음)
     * @param players 전원의 순간 상태
     */
    default void onTick(String roomId, TickResult tick, Map<String, Bar> bars, Map<String, PlayerSnapshot> players) {
    }

    /** 주문을 처리했다. ORDER_RESULT (본인에게만) */
    default void onOrderResult(String roomId, String userId, OrderResult result) {
    }

    /**
     * 판이 끝났다. GAME_END. scenario 로 실제 종목명을 공개한다.
     * M8 이 생기면 여기서 결과를 저장한다 (지금은 메모리에만 둔다 — CLAUDE.md §9).
     */
    default void onGameFinished(String roomId, GameResult result, Scenario scenario) {
    }
}
