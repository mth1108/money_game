package com.moneygame.room;

/**
 * 끝난 판을 기록한다 (M6 → M8 전달, CLAUDE.md §3 M6 「결과를 M8 에 전달」).
 *
 * 판이 끝나는 순간 방 스레드에서 한 번 불린다. 판 종료 시 쓰기라 §1.2 에 맞는다.
 * 실패하면 RoomService 가 로그를 남기고 결과 ID 없이 종료를 알린다 — 게임은 멈추지 않는다.
 */
public interface GameRecorder {

    /** @return 결과 ID (game_rooms.id). GAME_END 의 resultId 로 나간다 */
    long record(FinishedGame game);
}
