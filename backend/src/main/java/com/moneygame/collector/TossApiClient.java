package com.moneygame.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 토스 Open API 클라이언트.
 *
 * ┌─ CLAUDE.md §1.1 절대 규칙 ─────────────────────────────────────────┐
 * │ 이 클래스가 호출하는 엔드포인트는 GET /api/v1/candles 하나뿐이다.      │
 * │ 주문·계좌·잔고 엔드포인트를 호출하는 메서드를 추가하지 않는다.          │
 * │ 토큰 하나로 실제 주문이 나갈 수 있다. 이 규칙은 타협 대상이 아니다.     │
 * └───────────────────────────────────────────────────────────────────┘
 *
 * 레이트리밋은 토큰 버킷이다. 429 를 받으면 Retry-After 만큼 쉬고 지수 백오프로
 * 재시도한다 (CLAUDE.md §3 M1).
 */
@Component
@Profile("collector")
public class TossApiClient implements CandleSource {

    private static final Logger log = LoggerFactory.getLogger(TossApiClient.class);

    private final TossApiProperties properties;
    private final CollectorProperties collectorProperties;
    private final TossAuthService auth;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public TossApiClient(TossApiProperties properties,
                         CollectorProperties collectorProperties,
                         TossAuthService auth,
                         ObjectMapper mapper) {
        this.properties = properties;
        this.collectorProperties = collectorProperties;
        this.auth = auth;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 최신순(내림차순)으로 최대 count 개. before 가 null 이면 가장 최근 봉부터. */
    @Override
    public CandlePage candles(String symbol, Interval interval, OffsetDateTime before,
                              int count, boolean adjusted) {
        StringBuilder url = new StringBuilder(properties.baseUrl())
                .append("/api/v1/candles?symbol=").append(encode(symbol))
                .append("&interval=").append(interval.code())
                .append("&count=").append(Math.min(count, MAX_COUNT))
                .append("&adjusted=").append(adjusted);
        if (before != null) {
            url.append("&before=").append(encode(before.toString()));
        }
        return parse(getWithRetry(url.toString()));
    }

    private String getWithRetry(String url) {
        int attempt = 0;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    // Accept-Encoding 을 직접 지정하지 않는다. JDK HttpClient 는 gzip 을
                    // 자동으로 풀어주지 않아서, 압축을 요청하면 본문이 깨진 채로 들어온다.
                    .header("Authorization", "Bearer " + auth.accessToken())
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("캔들 조회 중 통신 오류", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("캔들 조회가 중단되었습니다", e);
            }

            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 429) {
                attempt++;
                if (attempt > collectorProperties.maxRetries()) {
                    throw new IllegalStateException("429 재시도 " + collectorProperties.maxRetries() + "회를 초과했습니다");
                }
                long wait = backoffSeconds(response, attempt);
                log.warn("429 — {}초 대기 후 재시도 ({}/{})", wait, attempt, collectorProperties.maxRetries());
                sleep(wait * 1000L);
                continue;
            }
            throw new IllegalStateException("캔들 조회 실패 — HTTP " + response.statusCode()
                    + " : " + abbreviate(response.body()));
        }
    }

    /** Retry-After 와 지수 백오프 중 큰 쪽. 최대 60초. */
    private static long backoffSeconds(HttpResponse<String> response, int attempt) {
        long retryAfter = response.headers().firstValue("Retry-After")
                .map(v -> { try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 0L; } })
                .orElse(0L);
        long exponential = 1L << Math.min(attempt - 1, 6);
        return Math.min(Math.max(retryAfter, exponential), 60L);
    }

    private CandlePage parse(String body) {
        try {
            JsonNode result = mapper.readTree(body).path("result");
            List<Candle> candles = new ArrayList<>();
            for (JsonNode node : result.path("candles")) {
                candles.add(new Candle(
                        OffsetDateTime.parse(node.get("timestamp").asText()),
                        decimal(node, "openPrice"),
                        decimal(node, "highPrice"),
                        decimal(node, "lowPrice"),
                        decimal(node, "closePrice"),
                        decimal(node, "volume")));
            }
            JsonNode next = result.path("nextBefore");
            OffsetDateTime nextBefore = next.isNull() || next.isMissingNode()
                    ? null : OffsetDateTime.parse(next.asText());
            return new CandlePage(candles, nextBefore);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "캔들 응답을 파싱할 수 없습니다: " + abbreviate(body), e);
        }
    }

    /** 가격은 API 가 decimal 문자열로 준다. 그대로 BigDecimal 로 받는다 (§1.5). */
    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기 중 중단되었습니다", e);
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
