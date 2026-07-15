package com.finmate.infra.kis.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.infra.kis.core.KisProperties;
import com.finmate.infra.kis.core.KisRetryConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

// KIS API를 사용하기 위해 access token을 발급받기 위 클라이언트 클래스
@Component
@RequiredArgsConstructor
public class KisAuthClient {
    private static final String TOKEN_PATH = "/oauth2/tokenP";

    private final KisProperties kisProperties;
    private final KisRetryConnection kisRetryConnection;
    private final ObjectMapper objectMapper;

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

            // KisRetryConnection을 통해 Auth Token을 KIS API를 통해 요청한다.
            // 만약 요청에 실패하는경우 최대 4번까지 재시도를 수행한다.
            KisAccessTokenResponse tokenResponse = kisRetryConnection.connectionAndRetry(
                    request,
                    KisAccessTokenResponse.class);
            if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new RuntimeException("KIS access_token 응답이 비어 있습니다.");
            }

            // KIS access_token 획득
            return tokenResponse;
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("KIS access_token 요청 생성 중 오류가 발생했습니다.", exception);
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
