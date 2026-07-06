package com.finmate.infra.kis.stock.realtime;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Kis WebSocket로 받은 실시간 시세의 최신값을 DB가 아니라, 서버 메모리에 저장하는 저장소 역할을 하는 클래스
@Service
public class KisRealtimeStore {
    private final ConcurrentMap<String, KisRealtimePayload> latestPayloads = new ConcurrentHashMap<>();

    // Kis WebSocket을 통해 방든 Payload를 메모리에 저장한다.
    public void put(KisRealtimePayload payload) {
        if (payload == null || payload.trKey() == null || payload.trKey().isBlank()) {
            return;
        }

        latestPayloads.put(key(payload.api(), payload.trKey()), payload);
    }

    // 메모리에서 특저 종목의 실시간 데이터를 받아오는 메서드
    public Optional<KisRealtimePayload> get(KisRealtimeApi api, String trKey) {
        if (api == null || trKey == null || trKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(latestPayloads.get(key(api, trKey.trim())));
    }

    // 메모리에 저장되어 있는 모든 Payload 데이터를 꺼내는 메서드
    public Collection<KisRealtimePayload> getAll() {
        return latestPayloads.values().stream()
                .sorted(Comparator
                        .comparing((KisRealtimePayload payload) -> payload.api().name()) // API 이름 기반으로 우선 정렬
                        .thenComparing(KisRealtimePayload::trKey)) // API 이름이 같다면 trKey를 기반으로 정렬
                .toList();
    }

    // Trid와 trKey를 기반으로 key 생성
    private String key(KisRealtimeApi api, String trKey) {
        return api.getTrId() + ":" + trKey;
    }
}
