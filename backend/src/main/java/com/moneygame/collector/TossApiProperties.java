package com.moneygame.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스 Open API 접속 정보.
 * client-id / client-secret 은 application-local.yml 에만 둔다 (.gitignore 대상, CLAUDE.md §1.1).
 */
@ConfigurationProperties(prefix = "toss.api")
public record TossApiProperties(String baseUrl, String clientId, String clientSecret) {

    public void requireCredentials() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("""
                    토스 API 자격증명이 없습니다.
                    backend/src/main/resources/application-local.yml 에 아래를 채우세요.
                      toss.api.client-id
                      toss.api.client-secret
                    이 파일은 .gitignore 대상입니다. 절대 커밋하지 마세요.""");
        }
    }
}
