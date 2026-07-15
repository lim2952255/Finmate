package com.finmate.infra.kis.rest;

import com.finmate.infra.kis.rest.KisAuthClient.KisAccessTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class KisTokenService {
    private static final DateTimeFormatter TOKEN_EXPIRED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int REFRESH_BEFORE_EXPIRE_MINUTES = 5;

    private final KisAuthClient kisAuthClient;

    private String accessToken;
    private LocalDateTime expiresAt;

    // access token이 있으면 accessToken 리턴, 없으면 kisAuthClient를 활용해서 access token 획득
    public synchronized String getAccessToken() {
        if (isTokenUsable()) {
            return accessToken;
        }

        KisAccessTokenResponse response = kisAuthClient.issueAccessToken();
        this.accessToken = response.accessToken();
        this.expiresAt = resolveExpiresAt(response);
        return accessToken;
    }

    // access Token을 만료시킨다.
    // access Token 만료 기한도 초기화시킨다.
    public synchronized void clear() {
        this.accessToken = null;
        this.expiresAt = null;
    }

    // 발급받은 토큰정보가 있는지 확인 또는 만료되었는지 확인
    private boolean isTokenUsable() {
        if (accessToken == null || accessToken.isBlank() || expiresAt == null) {
            return false;
        }

        return LocalDateTime.now()
                .plusMinutes(REFRESH_BEFORE_EXPIRE_MINUTES)
                .isBefore(expiresAt);
    }

    private LocalDateTime resolveExpiresAt(KisAccessTokenResponse response) {
        if (response.accessTokenExpiredAt() != null && !response.accessTokenExpiredAt().isBlank()) {
            return LocalDateTime.parse(response.accessTokenExpiredAt(), TOKEN_EXPIRED_AT_FORMATTER);
        }

        if (response.expiresIn() != null && response.expiresIn() > 0) {
            return LocalDateTime.now().plusSeconds(response.expiresIn());
        }

        return LocalDateTime.now().plusHours(23);
    }
}
