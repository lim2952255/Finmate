package com.finmate.infra.kis.stock.ranking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.ranking.StockRankingType;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.core.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Component
@RequiredArgsConstructor
public class KisStockRankingClient {
    // 국내 랭킹 API
    private static final String DOMESTIC_RANKING_PATH =
            "/uapi/domestic-stock/v1/quotations/volume-rank";
    // 해외 거래량 랭킹 API
    private static final String OVERSEAS_VOLUME_RANKING_PATH =
            "/uapi/overseas-stock/v1/ranking/trade-vol";
    // 해외 거래대금 랭킹 API
    private static final String OVERSEAS_TRADE_AMOUNT_RANKING_PATH =
            "/uapi/overseas-stock/v1/ranking/trade-pbmn";

    private static final String DOMESTIC_RANKING_TR_ID = "FHPST01710000";
    private static final String OVERSEAS_VOLUME_RANKING_TR_ID = "HHDFS76310010";
    private static final String OVERSEAS_TRADE_AMOUNT_RANKING_TR_ID = "HHDFS76320010";

    // API 호출을 위한 공통 Client
    private final KisRestClient kisRestClient;

    // 국내 랭킹 API를 통해 랭킹 데이터를 받아 Record로 저장
    public DomesticRankingResponse fetchDomesticRanking(StockMarketType marketType,
                                                        StockRankingType rankingType) {
        // 입력 파라미터 검증
        validateRequired(marketType, "국내 시장 구분은 필수입니다.");
        validateRequired(rankingType, "랭킹 구분은 필수입니다.");

        // 국내 랭킹을 조회하기 때문에 marketType이 KOSPI 또는 KOSDAQ인지 검증
        if (marketType != StockMarketType.KOSPI && marketType != StockMarketType.KOSDAQ) {
            throw new RuntimeException("국내 거래량/거래대금 순위 조회 대상 시장이 아닙니다: " + marketType);
        }

        // API호출을 위한 쿼리파라미터 정보를 담는다.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J"); // 종목시장 J:KRX
        params.put("FID_COND_SCR_DIV_CODE", "20171"); // 조건 화면 분류 코드. 거래량순위 API의 고정 화면 코드
        params.put("FID_INPUT_ISCD", domesticMarketCode(marketType)); // 종목 시장 코드(KOSPI / KOSDAQ)
        params.put("FID_DIV_CLS_CODE", "0"); // 종목 분류 0 -> 전체, 1 -> 보통주, 2 -> 우선주
        params.put("FID_BLNG_CLS_CODE", domesticRankingCode(rankingType)); // 랭킹 기준. 0 -> 거래량, 3 -> 거래대금
        params.put("FID_TRGT_CLS_CODE", "111111111"); // 대상 구분 코드 (증거금/신용 관련 대상군을 9자리 1/0 플래그로 지정)
        params.put("FID_TRGT_EXLS_CLS_CODE", "000000"); // 제외 대상 구분 코드
        params.put("FID_INPUT_PRICE_1", ""); // 가격 하한
        params.put("FID_INPUT_PRICE_2", ""); // 가격 상한
        params.put("FID_VOL_CNT", ""); // 거래량 하한
        params.put("FID_INPUT_DATE_1", ""); // 이 API에서는 사실상 사용하지 않는 날짜 입력값

        // 설정한 Parameter값과 API path를 기반으로 KisRestClient를 통해 API를 호출하여 데이터를 받고, 이를 Record 데이터로 매핑해서 반환
        return kisRestClient.get(
                DOMESTIC_RANKING_PATH,
                DOMESTIC_RANKING_TR_ID,
                params,
                DomesticRankingResponse.class);
    }

    // 해외 랭킹 API를 통해 NASDAQ 거래량/거래대금 랭킹 데이터를 받아 Record로 저장
    public OverseasRankingResponse fetchOverseasRanking(StockRankingType rankingType) {
        // 입력 파라미터 검증
        validateRequired(rankingType, "랭킹 구분은 필수입니다.");

        // API 호출을 위한 쿼리파라미터 정보를 담는다.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("KEYB", ""); // 연속조회용 NEXT KEY. 첫 조회는 공란
        params.put("AUTH", ""); // 사용자 권한 정보. 일반 조회는 공란
        params.put("EXCD", "NAS"); // 해외 거래소 코드. NAS -> 나스닥
        params.put("NDAY", "0"); // 조회 기준 기간. 0 -> 당일
        params.put("VOL_RANG", "0"); // 거래량 조건. 0 -> 전체
        params.put("PRC1", ""); // 현재가 필터 하한
        params.put("PRC2", ""); // 현재가 필터 상한

        // 거래대금 순위는 별도의 해외 거래대금 랭킹 API를 호출
        if (rankingType == StockRankingType.TRADE_AMOUNT) {
            return kisRestClient.get(
                    OVERSEAS_TRADE_AMOUNT_RANKING_PATH,
                    OVERSEAS_TRADE_AMOUNT_RANKING_TR_ID,
                    params,
                    OverseasRankingResponse.class);
        }

        // 거래량 순위는 해외 거래량 랭킹 API를 호출
        return kisRestClient.get(
                OVERSEAS_VOLUME_RANKING_PATH,
                OVERSEAS_VOLUME_RANKING_TR_ID,
                params,
                OverseasRankingResponse.class);
    }

    // 국내 시장 타입을 KIS 국내 거래량순위 API의 시장 코드로 변환
    private String domesticMarketCode(StockMarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "0001";
            case KOSDAQ -> "1001";
            case NASDAQ -> throw new RuntimeException("국내 시장 구분이 아닙니다: " + marketType);
        };
    }

    // 랭킹 타입을 KIS 국내 거래량순위 API의 랭킹 기준 코드로 변환
    private String domesticRankingCode(StockRankingType rankingType) {
        return switch (rankingType) {
            case VOLUME -> "0";
            case TRADE_AMOUNT -> "3";
        };
    }

    // 국내 랭킹 API 전체 응답
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticRankingResponse(
            @JsonProperty("rt_cd") String rtCd, // 성공 실패 여부
            @JsonProperty("msg_cd") String msgCd, // 응답 코드
            @JsonProperty("msg1") String msg1, // 응답 메시지
            @JsonProperty("output") List<DomesticRankingItem> output // 국내 랭킹 데이터 목록
    ) implements KisApiResponse {
    }

    // 국내 랭킹 데이터 하나
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticRankingItem(
            @JsonProperty("hts_kor_isnm") String nameKo, // HTS 한글 종목명
            @JsonProperty("mksc_shrn_iscd") String symbol, // 유가증권 단축 종목코드
            @JsonProperty("data_rank") String rank, // 데이터 순위
            @JsonProperty("stck_prpr") String currentPrice, // 현재가
            @JsonProperty("prdy_vrss") String changeAmount, // 전일 대비 가격
            @JsonProperty("prdy_ctrt") String changeRate, // 전일 대비율
            @JsonProperty("acml_vol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount // 누적 거래대금
    ) {
    }

    // 해외 랭킹 API 전체 응답
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasRankingResponse(
            @JsonProperty("rt_cd") String rtCd, // 성공 실패 여부
            @JsonProperty("msg_cd") String msgCd, // 응답 코드
            @JsonProperty("msg1") String msg1, // 응답 메시지
            @JsonProperty("output1") OverseasRankingSummary output1, // 해외 랭킹 조회 요약 정보
            @JsonProperty("output2") List<OverseasRankingItem> output2 // 해외 랭킹 데이터 목록
    ) implements KisApiResponse {
    }

    // 해외 랭킹 조회 요약 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasRankingSummary(
            @JsonProperty("zdiv") String decimalPointPosition, // 소수점 위치
            @JsonProperty("stat") String status, // 조회 상태
            @JsonProperty("crec") String currentRecordCount, // 현재 조회 건수
            @JsonProperty("trec") String totalRecordCount, // 전체 조회 건수
            @JsonProperty("nrec") String recordCount // 다음 조회 건수
    ) {
    }

    // 해외 랭킹 데이터 하나
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasRankingItem(
            @JsonProperty("excd") String exchangeCode, // 거래소 코드
            @JsonProperty("symb") String symbol, // 종목 코드
            @JsonProperty("name") String name, // 종목명
            @JsonProperty("ename") String nameEn, // 영문 종목명
            @JsonProperty("rank") String rank, // 순위
            @JsonProperty("last") String currentPrice, // 현재가
            @JsonProperty("diff") String changeAmount, // 전일 대비 가격
            @JsonProperty("rate") String changeRate, // 전일 대비율
            @JsonProperty("tvol") String accumulatedVolume, // 누적 거래량
            @JsonProperty("tamt") String accumulatedTradeAmount // 누적 거래대금
    ) {
    }
}
