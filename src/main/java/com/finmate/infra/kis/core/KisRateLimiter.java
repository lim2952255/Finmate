package com.finmate.infra.kis.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// KIS API 호출 속도 제한 (너무 짧은 시간에 많이 호출할 경우, 초당 호출횟수 제한이 걸릴 수 있기 때문에, 이경우 wait을 통해 잠시 대기했다가 다시 실행)
@Component
@RequiredArgsConstructor
public class KisRateLimiter {
    private final KisProperties kisProperties;

    private long lastRequestTimeMillis;

    // 마지막 api 호출 이후, 설정한 임계치만큼의 시간이 지나지 않았으면 해당 시간만큼 대기하여 너무 빠르게 api 호출하는 것을 방지한다.
    public synchronized void waitTurn() {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - lastRequestTimeMillis;
        long waitMillis = kisProperties.getSafeRequestIntervalMillis() - elapsedMillis;

        if (waitMillis > 0) {
            sleep(waitMillis);
        }

        lastRequestTimeMillis = System.currentTimeMillis();
    }

    // 초당 호출횟수를 초과한 경우, 일정 시간동안 대기
    public void waitAfterRateLimitExceeded() {
        sleep(Math.max(1500L, kisProperties.getSafeRequestIntervalMillis() * 2));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIS API 호출 대기 중 스레드가 중단되었습니다.", e);
        }
    }
}
