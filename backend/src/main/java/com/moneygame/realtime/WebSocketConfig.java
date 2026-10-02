package com.moneygame.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 엔드포인트 /ws. (CLAUDE.md §3 M7)
 *
 * 게임 서버(웹 애플리케이션)에서만 켠다. collector · scenario 프로파일은 웹 서버가 없다.
 * 허용 출처는 로컬 개발용이다 (P0 개발 서버가 다른 포트에서 뜬다). 배포할 때 다시 정한다.
 */
@Configuration
@EnableWebSocket
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameSocketHandler handler;

    public WebSocketConfig(GameSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
