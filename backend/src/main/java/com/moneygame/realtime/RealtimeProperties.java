package com.moneygame.realtime;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실시간 통신 설정. (CLAUDE.md §3 M7)
 *
 * @param allowedOrigins WebSocket 허용 출처 패턴. 기본은 로컬 개발 서버뿐이다.
 *                       터널로 임시 공개할 때는 share 프로파일이 터널 도메인을 더한다 (CLAUDE.md §4)
 */
@ConfigurationProperties(prefix = "realtime")
public record RealtimeProperties(List<String> allowedOrigins) {
}
