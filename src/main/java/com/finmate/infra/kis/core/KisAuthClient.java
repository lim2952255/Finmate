package com.finmate.infra.kis.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

// KIS API를 사용하기 위해 access token을 발급받기 위 클라이언트 클래스
@Component
@RequiredArgsConstructor
public class KisAuthClient {
    private static final String TOKEN_PATH = "/oauth2/tokenP";

    private final KisProperties kisProperties;
    private final KisRateLimiter kisRateLimiter;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public KisAccessTokenResponse issueAccessToken() {
        kisProperties.validateApiCredentials();

        // api호출에 필요한 쿼리 파라미터 정보를 전달
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", kisProperties.getAppKey(),
                "appsecret", kisProperties.getAppSecret());

        try {
            String body = objectMapper.writeValueAsString(requestBody);
            URI requestUri = URI.create(kisProperties.getNormalizedBaseUrl() + TOKEN_PATH);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("content-type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            kisRateLimiter.waitTurn();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("KIS access_token 발급에 실패했습니다. status="
                        + response.statusCode()
                        + ", url=" + requestUri
                        + ", body=" + response.body());
            }

            KisAccessTokenResponse tokenResponse = objectMapper.readValue(
                    response.body(),
                    KisAccessTokenResponse.class);
            if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new RuntimeException("KIS access_token 응답이 비어 있습니다. body=" + response.body());
            }

            // KIS access_token 획득
            return tokenResponse;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new RuntimeException("KIS access_token 발급 중 오류가 발생했습니다.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KisAccessTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("access_token_token_expired") String accessTokenExpiredAt
    ) {
    }
}
