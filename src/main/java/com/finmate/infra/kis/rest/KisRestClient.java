package com.finmate.infra.kis.rest;

import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.core.KisProperties;
import com.finmate.infra.kis.core.KisRetryConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class KisRestClient {
    private final KisProperties kisProperties; // KIS BaseURL, appKey, appSecret같은 설정값 관리
    private final KisRetryConnection kisRetryConnection; // KIS 연결 실패 시 재시도
    private final KisTokenService kisTokenService; // API 호출을 위한 access token을 얻는 서비스

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

        // JSON 응답을 각 API 클라이언트에서 정의한 record로 매핑
        // KISRetryConnection을 활용하여 KIS API에 최대 5번까지 재시도 요청을 보낸다.
        T responseBody = kisRetryConnection.connectionAndRetry(request, responseType);
        // KIS API 응답 성공 여부 검증
        validateKisResponse(responseBody);
        return responseBody;
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
