package com.finmate.infra.kis.stock.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// WebSocket 원본 메세지를 파싱하는 클래스
@Component
@RequiredArgsConstructor
public class KisRealtimeMessageParser {
    private final ObjectMapper objectMapper; // Json 문자열 -> 객체 / 객체 -> Json 문자열로 변환

    // Kis Websocket의 원본 문자열을 KisRealtimePayload 객체로 변환
    public Optional<KisRealtimePayload> parsePayload(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return Optional.empty();
        }

        // 메세지 타입을 확인하며 0또는 1이 아니면 empty를 리턴
        char messageType = rawMessage.charAt(0);
        if (messageType != '0' && messageType != '1') {
            return Optional.empty();
        }

        // | 단위로 Message를 분리
        String[] parts = rawMessage.split("\\|", 4);
        if (parts.length < 4) {
            return Optional.empty();
        }

        // WebSocket 메세지로 받은 데이터의 trId를 기반으로 어떤 api를 사용해야 하는지를 결정
        String trId = parts[1];
        Optional<KisRealtimeApi> maybeApi = KisRealtimeApi.findByTrId(trId);
        if (maybeApi.isEmpty()) {
            return Optional.empty();
        }

        // 실제 데이터의 형식에 맞는 api를 받아서 출력한다.
        KisRealtimeApi api = maybeApi.get();
        Map<String, String> values = toValueMap(api, parts[3]);
        String trKey = values.getOrDefault(api.getKeyColumn(), "");

        // 공통 페이로드 생성
        return Optional.of(new KisRealtimePayload(
                api,
                trId,
                trKey,
                values.get(api.getPriceColumn()),
                values.get(api.getChangeColumn()),
                values.get(api.getChangeRateColumn()),
                values.get(api.getTimeColumn()),
                LocalDateTime.now(),
                values));
    }

    public boolean isPingPongMessage(String rawMessage) {
        return "PINGPONG".equals(systemTrId(rawMessage).orElse(null));
    }

    public Optional<String> systemTrId(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank() || rawMessage.charAt(0) != '{') {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            JsonNode trId = root.path("header").path("tr_id");
            if (trId.isMissingNode() || trId.asText().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(trId.asText());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ^로 나 데이터를 컬럼명(실제 api)과 매핑한다.
    private Map<String, String> toValueMap(KisRealtimeApi api, String data) {
        String[] tokens = data.split("\\^", -1);
        Map<String, String> values = new LinkedHashMap<>();

        for (int i = 0; i < api.getColumns().size(); i++) {
            String value = i < tokens.length ? tokens[i] : "";
            values.put(api.getColumns().get(i), value);
        }

        return values;
    }
}
