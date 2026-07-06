package com.finmate.infra.kis.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class KisRestClient {
    private static final String RATE_LIMIT_EXCEEDED_CODE = "EGW00201";
    private static final int MAX_RETRY_COUNT = 2;

    private final KisProperties kisProperties; // KIS BaseURL, appKey, appSecret같은 설정값 관리
    private final KisRateLimiter kisRateLimiter; // API 호출 제한을 넘지 않게 호출 속도를 조절
    private final KisTokenService kisTokenService; // API 호출을 위한 access token을 얻는 서비스
    private final ObjectMapper objectMapper; // API 호출 결과의 JSON 데이터를 record로 매핑하기 위한 객체

    // API 호출을 위한 HttpClient 생성
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // KIS API 공통 GET 호출 메서드
    public <T extends KisApiResponse> T get(String path,
                                            String trId,
                                            Map<String, String> params,
                                            Class<T> responseType) {
        // API 호출 전에 KIS 설정값 검증
        kisProperties.validateApiCredentials();

        // API 호출에 필요한 HTTP 요청 생성
        URI requestUri = buildUri(path, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", authorizationHeader())
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", trId)
                .header("custtype", "P")
                .header("tr_cont", "")
                .GET()
                .build();

        try {
            // Rate Limit을 고려해서 API 요청 전송
            HttpResponse<String> response = sendWithRetry(request);
            // JSON 응답을 각 API 클라이언트에서 정의한 record로 매핑
            T responseBody = objectMapper.readValue(response.body(), responseType);
            // KIS API 응답 성공 여부 검증
            validateKisResponse(responseBody);
            return responseBody;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new RuntimeException("KIS API 호출 중 오류가 발생했습니다.", e);
        }
    }

    // 호출 제한 초과 응답이 오면 잠시 대기 후 재시도
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        RuntimeException lastException = null;

        for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            kisRateLimiter.waitTurn();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response;
            }

            lastException = new RuntimeException("KIS API 호출에 실패했습니다. status="
                    + response.statusCode()
                    + ", url=" + request.uri()
                    + ", body=" + response.body());

            if (!isRateLimitExceeded(response.body()) || retryCount == MAX_RETRY_COUNT) {
                throw lastException;
            }

            kisRateLimiter.waitAfterRateLimitExceeded();
        }

        throw lastException;
    }

    private boolean isRateLimitExceeded(String responseBody) {
        return responseBody != null && responseBody.contains(RATE_LIMIT_EXCEEDED_CODE);
    }

    // 쿼리 파라미터를 KIS API 요청 형식에 맞게 인코딩해서 URI 생성
    private URI buildUri(String path, Map<String, String> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((key, value) ->
                query.add(encode(key) + "=" + encode(value == null ? "" : value)));

        return URI.create(kisProperties.getNormalizedBaseUrl() + path + "?" + query);
    }

    // access token에 Bearer prefix가 없으면 추가
    private String authorizationHeader() {
        String accessToken = kisTokenService.getAccessToken().trim();
        if (accessToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return accessToken;
        }

        return "Bearer " + accessToken;
    }

    // KIS API 응답 코드가 실패면 예외 처리
    private void validateKisResponse(KisApiResponse response) {
        if (!"0".equals(response.rtCd())) {
            throw new RuntimeException("KIS API 응답이 실패했습니다. code="
                    + response.msgCd() + ", message=" + response.msg1());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
