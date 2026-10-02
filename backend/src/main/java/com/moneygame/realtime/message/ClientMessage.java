package com.moneygame.realtime.message;

import java.math.BigDecimal;

/**
 * 클라이언트 → 서버 메시지. (CLAUDE.md §3 M7)
 *
 *   {"type":"JOIN",  "roomId":"ab12cd34", "userId":5}
 *   {"type":"READY", "ready":true}
 *   {"type":"ORDER", "symbolLabel":"A", "action":"BUY", "margin":"10000000", "leverage":2}
 *   {"type":"ORDER", "symbolLabel":"A", "action":"SELL"}
 *
 * JOIN 으로 이 연결을 방·사용자에 묶는다. READY·ORDER 는 묶인 방·사용자로 처리한다.
 * margin 은 문자열·숫자 모두 받는다. 정밀도를 지키려면 문자열로 보낸다 (§1.5).
 */
public record ClientMessage(Type type,
                            String roomId,
                            Long userId,
                            Boolean ready,
                            String symbolLabel,
                            String action,
                            BigDecimal margin,
                            Integer leverage) {

    public enum Type { JOIN, READY, ORDER }
}
