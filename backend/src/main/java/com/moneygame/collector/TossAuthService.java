package com.moneygame.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 액세스 토큰 발급과 캐시. (CLAUDE.md §3 M1 토큰 제약)
 *
 * client 당 유효한 토큰은 1개다. 재발급하면 이전 토큰이 즉시 무효화되고,
 * 죽은 토큰으로 호출하면 token-revoked 가 돌아온다.
 * 그래서 토큰을 캐시해 재사용하고 만료 직전에만 다시 받는다.
 * 수집기를 동시에 두 개 띄우면 서로의 토큰을 죽인다.
 *
 * refresh token 은 제공되지 않는다. 만료되면 같은 엔드포인트로 재발급한다.
 */
@Service
@Profile("collector")
public class TossAuthService {

    private static final Logger log = LoggerFactory.getLogger(TossAuthService.class);

    /** 만료 직전에 미리 갱신할 여유. */
    private static final Duration RENEW_MARGIN = Duration.ofMinutes(5);

    private final TossApiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public TossAuthService(TossApiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 캐시된 토큰을 돌려준다. 없거나 만료가 임박하면 새로 발급한다. */
    public synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(expiresAt.minus(RENEW_MARGIN))) {
            return token;
        }
        issue();
        return token;
    }

    private void issue() {
        properties.requireCredentials();

        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(properties.clientId())
                + "&client_secret=" + encode(properties.clientSecret());

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                // 본문에는 자격증명이 담기지 않는다. 토큰 자체는 어디에도 로그하지 않는다.
                throw new IllegalStateException("토큰 발급 실패 — HTTP " + response.statusCode()
                        + " : " + abbreviate(response.body()));
            }
            JsonNode node = mapper.readTree(response.body());
            if (!node.hasNonNull("access_token")) {
                throw new IllegalStateException("토큰 응답에 access_token 이 없습니다");
            }
            token = node.get("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(86400L);
            expiresAt = Instant.now().plusSeconds(expiresIn);
            log.info("토큰 발급 완료 — {}초 후 만료. 같은 client_id 의 기존 토큰은 무효화됐습니다", expiresIn);
        } catch (IOException e) {
            throw new IllegalStateException("토큰 발급 중 통신 오류", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토큰 발급이 중단되었습니다", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String body) {
        if (body == null) return "";
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
