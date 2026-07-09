package com.finmate.infra.kis.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@Setter
@Component
// finmate.kis로 시작하는 설정값들을 읽어서 자동으로 데이터를 채워준다.
@ConfigurationProperties(prefix = "finmate.kis")
public class KisProperties {
    private static final long DEFAULT_REQUEST_INTERVAL_MILLIS = 700L;

    // api호출에 필요한 url, api_key등을 관리
    private String baseUrl;
    private String appKey;
    private String appSecret;
    private String webSocketUrl = "ws://ops.koreainvestment.com:21000";
    private String webSocketPath = "/tryitout";
    private Long requestIntervalMillis = DEFAULT_REQUEST_INTERVAL_MILLIS;

    public String getNormalizedBaseUrl() {
        validateRequired(baseUrl, "KIS API baseUrl 설정은 필수입니다.");

        String normalizedBaseUrl = baseUrl.trim();

        if (normalizedBaseUrl.endsWith("/")) {
            return normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        return normalizedBaseUrl;
    }

    public void validateApiCredentials() {
        getNormalizedBaseUrl();
        validateRequired(appKey, "KIS API appKey 설정은 필수입니다.");
        validateRequired(appSecret, "KIS API appSecret 설정은 필수입니다.");
    }

    public long getSafeRequestIntervalMillis() {
        if (requestIntervalMillis == null || requestIntervalMillis < 0) {
            return DEFAULT_REQUEST_INTERVAL_MILLIS;
        }

        return requestIntervalMillis;
    }
    // KIS WebSocket 연결용 URI를 생성하는 메서드
    public String getNormalizedWebSocketEndpoint() {
        validateRequired(webSocketUrl, "KIS websocket url setting is required.");
        validateRequired(webSocketPath, "KIS websocket path setting is required.");

        String normalizedUrl = webSocketUrl.trim();
        String normalizedPath = webSocketPath.trim();

        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }

        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        return normalizedUrl + normalizedPath;
    }
}
