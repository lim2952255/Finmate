package com.finmate.infra.kis.stock.realtime;

import java.time.LocalDateTime;
import java.util.Map;

// 파싱된 실시간 데이터를 담는 DTO
public record KisRealtimePayload(
        KisRealtimeApi api, // 어떤 API에서 온 데이터인지(DOMESTIC_STOCK_TRADE, OVERSEAS_STOCK_TRADE ..)
        String trId, // 종목별 trId
        String trKey, // 구독 키
        String price, // 현재가 or 현재 지수가
        String change, // 전일 대비
        String changeRate, // 등락률
        String tradeTime, // 체결시간
        LocalDateTime receivedAt, // 우리 서버가 KIS 서버로부터 데이터를 받은 시간
        // values를 제외한 모든 필드들 모든 종목이 공통적으로 가지고 있는 필드정보이며, values에는 해당 종목의 api에 맞는 데이터들이 저장되어 있다.
        Map<String, String> values // 원본 컬럼 전체 매핑
) {
}
