package com.moneygame.room;

public enum RoomStatus {
    /** 대기 중. 입장·퇴장·준비가 가능하다 */
    WAITING,
    /** 판 진행 중. 1초마다 틱이 흐른다 */
    PLAYING,
    /** 판이 끝났다. 결과 조회용으로 잠시 남아 있다가 지워진다 */
    FINISHED
}
