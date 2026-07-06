package com.finmate.infra.kis.stock.realtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.infra.kis.core.KisProperties;
import com.finmate.infra.kis.core.KisRateLimiter;
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

// KIS WebSocket에 연결하기 위해서는 ApprovalKey를 발급받아야 한다.
@Component
@RequiredArgsConstructor
public class KisWebSocketApprovalClient {
    private static final String APPROVAL_PATH = "/oauth2/Approval";

    private final KisProperties kisProperties; // Kis api를 활용하기 위한 기본 설정정보
    private final KisRateLimiter kisRateLimiter; // 요청속도 제한 / 조절
    private final ObjectMapper objectMapper; // 응답받은 Json 문자열을 객체로 변환
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ApprovalKey를 발급받는 메서드
    public KisWebSocketApprovalResponse issueApprovalKey() {
        kisProperties.validateApiCredentials(); // 설정정보 검증
        // api 요청에 필요한 파라미터정보들을 저장
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", kisProperties.getAppKey(),
                "secretkey", kisProperties.getAppSecret());

        try {
            // objectMapper를 통해 객체를 Json 문자열로 변환
            String body = objectMapper.writeValueAsString(requestBody);
            URI requestUri = URI.create(kisProperties.getNormalizedBaseUrl() + APPROVAL_PATH);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(requestUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("content-type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            kisRateLimiter.waitTurn(); // 요청 속도 조절
            // api 호출후 응답 데이터를 받는다.
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("KIS websocket approval key request failed. status="
                        + response.statusCode()
                        + ", url=" + requestUri
                        + ", body=" + response.body());
            }
            // api 응답 메세지의 body(Json 문자열)를 KisWebSocketApprovalResponse 객체로 변환한다.
            KisWebSocketApprovalResponse approvalResponse = objectMapper.readValue(
                    response.body(),
                    KisWebSocketApprovalResponse.class);
            if (approvalResponse.approvalKey() == null || approvalResponse.approvalKey().isBlank()) {
                throw new RuntimeException("KIS websocket approval key response is empty. body=" + response.body());
            }

            return approvalResponse;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new RuntimeException("KIS websocket approval key request failed.", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KisWebSocketApprovalResponse(
            @JsonProperty("approval_key") String approvalKey
    ) {
    }
}
