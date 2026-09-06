package com.finmate.infra.naver.news;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 네이버 뉴스 API를 호출할때 필요한 설정값들 한 군데에 모아두는 설정 클래스
// 운영환경에서는 스프링 부트가 application properties에서 필요한 설정정보들을 읽어서 값을 바인딩해준다.
// 평가환경에서는 스프링 부트를 사용하지 않으므로 application properties를 활용할 수 없으므로, 직접 환경변수에서 해당 변수값들을 읽어 바인딩해줘야 한다.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "finmate.news")
public class NaverNewsProperties {
    private static final String DEFAULT_BASE_URL = "https://naverapihub.apigw.ntruss.com";
    private static final long DEFAULT_CACHE_TTL_HOURS = 6L;

    private String baseUrl = DEFAULT_BASE_URL;
    private String clientId; // 설정파일에 들어가 있는 clientId, clientSecret, TTL을 주입받는다.
    private String clientSecret;
    private Long cacheTtlHours = DEFAULT_CACHE_TTL_HOURS;

    public String getNormalizedBaseUrl() {
        validateRequired(baseUrl, "NAVER 뉴스 API baseUrl 설정은 필수입니다.");
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public void validateCredentials() {
        validateRequired(clientId, "NAVER 뉴스 API Client ID 설정은 필수입니다.");
        validateRequired(clientSecret, "NAVER 뉴스 API Client Secret 설정은 필수입니다.");
    }

    public long getSafeCacheTtlHours() {
        if (cacheTtlHours == null || cacheTtlHours <= 0) {
            return DEFAULT_CACHE_TTL_HOURS;
        }
        return cacheTtlHours;
    }
}
