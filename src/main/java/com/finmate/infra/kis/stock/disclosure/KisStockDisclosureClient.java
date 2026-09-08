package com.finmate.infra.kis.stock.disclosure;

import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KIS 종합 시황/공시(제목) API 호출 규격을 캡슐화한다.
@Component
@RequiredArgsConstructor
public class KisStockDisclosureClient {
    static final String DISCLOSURE_TITLE_PATH = "/uapi/domestic-stock/v1/quotations/news-title";
    static final String DISCLOSURE_TITLE_TR_ID = "FHKST01011800";

    private final KisRestClient kisRestClient; // 실제로 KIS API에 요청을 보내고 응답을 받는 클라이언트 객체

    // 종목코드가 연결된 최신 제목을 조회하며 시황과 공시는 자료원으로 임의 분리하지 않는다.
    public KisStockDisclosureResponse fetchLatestTitles(String symbol) {
        validateRequired(symbol, "시황/공시 조회 종목코드는 필수입니다.");

        // KIS 명세상 모든 파라미터를 보내야 하므로 사용하지 않는 검색 조건도 빈 문자열로 전달한다.
		// KIS API를 통해 시황/공시정보를 요청하기 위해 필요한 파라미터정보들을 추가한다.
        Map<String, String> params = new LinkedHashMap<>();
        // 뉴스 제공업체 코드다. 빈 값이면 특정 제공업체로 제한하지 않는다.
        params.put("FID_NEWS_OFER_ENTP_CODE", "");
        // 조건 시장 구분 코드다. 빈 값이면 특정 시장 구분으로 제한하지 않는다.
        params.put("FID_COND_MRKT_CLS_CODE", "");
        // 조회할 국내 종목코드다. 예: 삼성전자 005930.
        params.put("FID_INPUT_ISCD", symbol.trim());
        // 제목 검색어다. 빈 값이면 제목 키워드로 결과를 제한하지 않는다.
        params.put("FID_TITL_CNTT", "");
        // 조회 기준 날짜다. 빈 값이면 KIS의 현재 기준 최신 데이터를 조회한다.
        params.put("FID_INPUT_DATE_1", "");
        // 조회 기준 시간이다. 빈 값이면 KIS의 현재 기준 시간을 사용한다.
        params.put("FID_INPUT_HOUR_1", "");
        // 결과 정렬 구분 코드다. 빈 값이면 KIS의 기본 정렬 순서를 사용한다.
        params.put("FID_RANK_SORT_CLS_CODE", "");
        // 연속 조회에 사용하는 입력 일련번호다. 빈 값이면 최초 구간부터 조회한다.
        params.put("FID_INPUT_SRNO", "");

		// KisRestClient에게 파라미터정보들을 전달하면서 응답결과를 리턴받는다.
        return kisRestClient.get(
                DISCLOSURE_TITLE_PATH,
                DISCLOSURE_TITLE_TR_ID,
                params,
                KisStockDisclosureResponse.class);
    }
}
