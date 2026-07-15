package com.finmate.infra.kis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

// KIS HTTP 요청 및 WebSocket 연결을 재시도하는 서비스
@Service
@RequiredArgsConstructor
public class KisRetryConnection {
    // 최대 연결 재시도 횟수(5회)
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    // 재시도 주기: 2초
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    // 호출 횟수 초과 코드
    private static final String RATE_LIMIT_EXCEEDED_CODE = "EGW00201";
    // API 호출속도를 조절하기 위한 서비스
    private final KisRateLimiter kisRateLimiter;
    // API 응답을 통해 받은 JSON 문자열을 객체로 매핑해주는 클래스
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // REST API에 연결및 재시도를 수행하는 메서드
    // 이때 파라미터로 리턴타입과 요청에 필요한 데이터들을 받는다.
    public <T> T connectionAndRetry(HttpRequest request, Class<T> responseType) {
        RuntimeException lastException = null;
        // KIS API 연결 실패 시 최대 5번까지 재시도를 수행한다.
        for (int attempt = 1; attempt <= MAX_CONNECTION_ATTEMPTS; attempt++) {
            try {
                // API 호출속도를 조절
                kisRateLimiter.waitTurn();

                // KIS API 요청 후 응답을 받는다.
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // API 요청 성공시 응답 데이터를 응답 타입에 맞는 객체로 매핑해서 리턴한다.
                if (isSuccessful(response)) {
                    return parseResponse(response, responseType);
                }

                // 예외 발생시 실패 예외를 lastException에 담아둔다
                lastException = new RuntimeException("KIS 연결 요청에 실패했습니다. status="
                        + response.statusCode()
                        + ", url=" + request.uri()
                        + ", body=" + response.body());

                // 재시도가 불가능 상황이거나, 최대 재시도 횟수를 초과한 경우
                if (!isRetryable(response) || attempt == MAX_CONNECTION_ATTEMPTS) {
                    throw lastException;
                }
            } catch (InterruptedException exception) {
                // 인터럽트에 의해 스레드가 중단된경우, catch를 하게 되면 인터럽트정보가 사라지기 때문에, 다시 인터럽트를 발생시킨다.
                Thread.currentThread().interrupt();
                throw new RuntimeException("KIS 연결 재시도 중 스레드가 중단되었습니다.", exception);
            } catch (IOException exception) {
                lastException = new RuntimeException("KIS 연결 요청 중 오류가 발생했습니다. url=" + request.uri(), exception);

                // 만약 최대 재시도 횟수가 충족되지 않은 경우에는 재시도 수행, 아니라면 예외를 던진다.
                if (attempt == MAX_CONNECTION_ATTEMPTS) {
                    throw lastException;
                }
            }
            // 연결에 실패하여 재시도를 수행하기 전에, 2초동안 대기한다.
            waitBeforeRetry();
        }

        throw lastException;
    }

    // KIS WebSocket API에 연결 및 재시도를 수행하는 메서드
    // 이때 파라미터로 요청에 필요한 데이터들을 받는다.
    public WebSocket webSocketConnectionAndRetry(URI endpoint, WebSocket.Listener listener) {
        RuntimeException lastException = null;

        // 최대 5번까지 WebSocket에 연결을 시도한다.
        for (int attempt = 1; attempt <= MAX_CONNECTION_ATTEMPTS; attempt++) {
            try {
                // KIS WebSocket에 재시도를 시도한다.
                return httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(endpoint, listener)
                        .join();
            } catch (CompletionException | CancellationException exception) {
                lastException = new RuntimeException("KIS WebSocket 연결 요청 중 오류가 발생했습니다. endpoint=" + endpoint, exception);

                // WebSocket에 연결 시도중 예외 발생 시, 최대 재시도 횟수를 충족하지 않으면 재시도,
                // 만약 최대 재시도 횟수를 충족했다면 예외를 던진다.
                if (attempt == MAX_CONNECTION_ATTEMPTS) {
                    throw lastException;
                }
            }

            // 재시도를 수행하기 전에 일정 시간동안 대기한다.
            waitBeforeRetry();
        }

        throw lastException;
    }

    // KIS 응답 데이터로 받은 JSON 문자열을 특정 ResponseType 객체로 매핑한다.
    private <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("KIS 응답 파싱에 실패했습니다. body=" + response.body(), exception);
        }
    }

    // KIS API 성공여부를 판단.
    private boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() >= 200
                && response.statusCode() < 300
                && !isRateLimitExceeded(response.body());
    }
    // statusCode를 기반으로 재시도가 가능한지를 확인한다.
    private boolean isRetryable(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        return statusCode == 429
                || (statusCode >= 500 && statusCode < 600)
                || isRateLimitExceeded(response.body());
    }

    // KIS API에 응답을 받았지만, 호출횟수 초과 예외인지를 확인한다.
    private boolean isRateLimitExceeded(String responseBody) {
        return responseBody != null && responseBody.contains(RATE_LIMIT_EXCEEDED_CODE);
    }

    // 재시도를 수행하기 전에 일정 시간동안 대기한다.
    private void waitBeforeRetry() {
        try {
            // RETRY_DELAY동안 대기한다.
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIS 연결 재시도 대기 중 스레드가 중단되었습니다.", exception);
        }
    }
}
