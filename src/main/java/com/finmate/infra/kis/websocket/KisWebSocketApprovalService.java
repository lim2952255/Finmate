package com.finmate.infra.kis.websocket;

import com.finmate.infra.kis.websocket.KisWebSocketApprovalClient.KisWebSocketApprovalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// KisWebSocketApprovalClient를 활용해서 approvalKey를 발급받는 서비스레이어
@Service
@RequiredArgsConstructor
public class KisWebSocketApprovalService {
    private static final int DEFAULT_APPROVAL_KEY_TTL_HOURS = 23; // APPROVAL_Key의 지속시간
    private static final int REFRESH_BEFORE_EXPIRE_MINUTES = 5;

    private final KisWebSocketApprovalClient approvalClient; // approvalKey를 실제로 요청하고 발급받는 client

    // 발급받은 approvalKey 관리 및 만료기간 관리
    private String approvalKey;
    private LocalDateTime expiresAt;

    // 만약 이미 ApprovalKey가 존재한다면 approvalKey를 리턴하고, 없으면 approvalClient를 통해서 approvalKey를 발급받는다.
    public synchronized String getApprovalKey() {
        if (isApprovalKeyUsable()) {
            return approvalKey;
        }
        // KisWebSocketApprovalResponse를 활용하여 ApprovalKey를 발급받는다.
        KisWebSocketApprovalResponse response = approvalClient.issueApprovalKey();
        this.approvalKey = response.approvalKey();
        this.expiresAt = LocalDateTime.now().plusHours(DEFAULT_APPROVAL_KEY_TTL_HOURS);
        return approvalKey;
    }

    // approvalKey와 만료기한을 초기화하는 메서드
    public synchronized void clear() {
        this.approvalKey = null;
        this.expiresAt = null;
    }
    // approvalKey를 발급받은 상태인지를 확인 + 기한이 만료되지 않았는지를 확인
    private boolean isApprovalKeyUsable() {
        if (approvalKey == null || approvalKey.isBlank() || expiresAt == null) {
            return false;
        }

        return LocalDateTime.now()
                .plusMinutes(REFRESH_BEFORE_EXPIRE_MINUTES)
                .isBefore(expiresAt);
    }
}
