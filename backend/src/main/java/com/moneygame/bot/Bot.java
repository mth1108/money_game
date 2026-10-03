package com.moneygame.bot;

import com.moneygame.engine.OrderRequest;

import java.util.List;

/**
 * 싱글 모드의 상대. (CLAUDE.md §3 M9)
 *
 * 봇은 M4 입장에서 사람과 구분되지 않는다 — 돌려준 주문은 사람 주문과 똑같이 GameSession.submitOrder 로 들어간다.
 * 이 모듈은 Spring·DB·네트워크를 모른다. 방의 실행 흐름(M6)이 정해진 때에 불러 준다.
 */
public interface Bot {

    /** 판 시작 직후(0틱)에 낼 주문. */
    List<OrderRequest> onStart(BotContext context);

    /** 매 틱이 지난 뒤 낼 주문. 기본은 아무것도 하지 않는다. 마지막 틱 뒤에는 불리지 않는다. */
    default List<OrderRequest> onTick(BotContext context) {
        return List.of();
    }
}
