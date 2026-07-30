package com.finmate.infra.nxt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NxtMarketDataClient {
    private static final String REQUEST_BODY = "pageIndex=1&pageUnit=1000";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // NXT 공식목록
    @Value("${finmate.nxt.market-data-url:https://www.nextrade.co.kr/brdinfoTime/brdinfoTimeList.do}")
    private String marketDataUrl;

    public List<NxtStockTradingPermission> fetchStockTradingPermissions() {
        try {
            // Http 요청메세지 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(marketDataUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(REQUEST_BODY))
                    .build();
            // HTTP 요청 전송 후 응답 메세지를 받는다.
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 응답 상태코드 검사
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("NXT 거래대상 종목 조회 응답이 실패했습니다. status=" + response.statusCode());
            }

            // 응답 문자열을 Json 문자열로 파싱하고, 최상위 Json에서 brdinfotimeList 필드를 꺼낸다.
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rows = root.path("brdinfoTimeList");
            if (!rows.isArray() || rows.isEmpty()) {
                throw new RuntimeException("NXT 거래대상 종목 목록이 비어 있습니다.");
            }

            // NXT 거래 허용코드 설정
            List<NxtStockTradingPermission> permissions = new ArrayList<>();
            for (JsonNode row : rows) {
                // 각 종목정보를 정규화해서 어떤 종목인지를 식별한다.
                String symbol = normalizeSymbol(row.path("isuSrdCd").asText());
                if (symbol == null) {
                    continue;
                }
                // 각 종목마다 프리마켓 + 정규마켓 + 애프터마켓중 어떤 시간대에 거래가 가능한지를 나타내는 허용코드를 등록한다.
                permissions.add(new NxtStockTradingPermission(
                        symbol,
                        row.path("cptrTrdPmsnCd").asInt(0)));
            }
            if (permissions.isEmpty()) {
                throw new RuntimeException("NXT 거래대상 종목코드를 파싱하지 못했습니다.");
            }
            return List.copyOf(permissions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("NXT 거래대상 종목 조회가 중단되었습니다.", e);
        } catch (Exception e) {
            throw new RuntimeException("NXT 거래대상 종목 조회에 실패했습니다.", e);
        }
    }

    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return null;
        }
        String symbol = rawSymbol.trim();
        return symbol.startsWith("A") ? symbol.substring(1) : symbol;
    }

    public record NxtStockTradingPermission(String symbol, int permissionCode) {
    }
}
