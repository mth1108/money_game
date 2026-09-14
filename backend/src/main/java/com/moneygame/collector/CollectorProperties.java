package com.moneygame.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집 동작 설정.
 *
 * 레이트리밋은 초당 요청 수가 아니라 토큰 버킷이다. 연속 요청이 버킷을 말리므로
 * 요청 사이에 대기를 둔다. 실측상 300ms 면 잔량이 19/20 로 유지된다 (CLAUDE.md §3 M1).
 */
@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(long requestDelayMs, int maxRetries, String csvDir) {
}
