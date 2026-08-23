package com.finmate.infra.kis.stock.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KIS API로부터 분봉데이터를 요청하고, 결과 DTO를 반환하는 클라이언트
@Component
@RequiredArgsConstructor
public class KisStockMinutePriceClient {
    private static final String DOMESTIC_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice";
    private static final String DOMESTIC_TR_ID = "FHKST03010230";
    private static final String OVERSEAS_PATH =
            "/uapi/overseas-price/v1/quotations/inquire-time-itemchartprice";
    private static final String OVERSEAS_TR_ID = "HHDFS76950200";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final int MAX_PAGE_COUNT = 12;

    private final KisRestClient kisRestClient;

	// 국내종목의 분봉데이터를 KIS API로부터 호출해서 리턴하는 메서드
    public List<DomesticMinutePriceItem> fetchDomesticMinutePrices(String symbol, LocalDate tradeDate) {
        validateRequired(symbol, "국내 종목코드는 필수입니다.");
        validateRequired(tradeDate, "분봉 거래일자는 필수입니다.");

        List<DomesticMinutePriceItem> result = new ArrayList<>();
		// KIS API로부터 분봉데이터를 조회할때에도 120개 청크단위로 나눠서 조회한다.
        LocalTime cursor = LocalTime.of(20, 0); // KIS API에게 전달할 기준시각
        for (int page = 0; page < MAX_PAGE_COUNT; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("FID_COND_MRKT_DIV_CODE", "UN");
            params.put("FID_INPUT_ISCD", symbol);
            params.put("FID_INPUT_HOUR_1", cursor.format(TIME));  // 데이터를 조회할 기준시각
            params.put("FID_INPUT_DATE_1", tradeDate.format(DATE)); // 데이터를 조회할 기준일자
            params.put("FID_PW_DATA_INCU_YN", "N");
            params.put("FID_FAKE_TICK_INCU_YN", "");
			// KIS API로부터 데이터를 받아 DomesticMinutePriceResponse DTO를 리턴받는다.
            DomesticMinutePriceResponse response = kisRestClient.get(
                    DOMESTIC_PATH, DOMESTIC_TR_ID, params, DomesticMinutePriceResponse.class);

			// DomesticMinutePriceResponse DTO에서 실제 분봉데이터가 담겨있는 DomesticMinutePriceItem 리스트를 꺼낸다.
            List<DomesticMinutePriceItem> pageItems = response.output2() == null ? List.of() : response.output2();
            if (pageItems.isEmpty()) {
                break;
            }
            result.addAll(pageItems);
            if (pageItems.size() < 120) {
                break;
            }
			// 이번 청크에서 조회한 데이터의 시각중 가장 오래된 시각을 계산한다.
            LocalTime oldest = pageItems.stream()
                    .map(DomesticMinutePriceItem::tradeTime)
                    .filter(value -> value != null && value.length() >= 6)
                    .map(value -> LocalTime.parse(value.substring(0, 6), TIME))
                    .min(LocalTime::compareTo)
                    .orElse(null);
            if (oldest == null || oldest.equals(LocalTime.MIN)) {
                break;
            }
			// 다음 분봉 데이터 조회 시각을 이번청크에서 조회한 데이터시각중 가장 오래된 시각 -1 로 설정한다.
            LocalTime nextCursor = oldest.minusMinutes(1);
            if (!nextCursor.isBefore(cursor)) {
                break;
            }
            cursor = nextCursor;
        }
        return result;
    }
	// 해외종목의 분봉데이터를 KIS API로부터 호출해서 리턴하는 메서드
    public List<OverseasMinutePriceItem> fetchOverseasMinutePrices(String exchangeCode, String symbol) {
        validateRequired(exchangeCode, "해외 거래소코드는 필수입니다.");
        validateRequired(symbol, "해외 종목코드는 필수입니다.");

        List<OverseasMinutePriceItem> result = new ArrayList<>();
        String keyBuffer = ""; // 데이터를 조회할 기준 시각
		// 해외종목의 분봉데이터를 조회할때에도 120개 청크단위로 조회한다.
        for (int page = 0; page < MAX_PAGE_COUNT; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("AUTH", "");
            params.put("EXCD", exchangeCode);
            params.put("SYMB", symbol);
            params.put("NMIN", "1");
            params.put("PINC", "1");
            params.put("NEXT", page == 0 ? "" : "1");
            params.put("NREC", "120");
            params.put("FILL", "");
            params.put("KEYB", keyBuffer);
			// KIS API로부터 해외 분봉데이터를 요청하고, OverseasMinutePriceResponse DTO를 리턴받는다.
            OverseasMinutePriceResponse response = kisRestClient.get(
                    OVERSEAS_PATH, OVERSEAS_TR_ID, params, OverseasMinutePriceResponse.class);
			// OverseasMinutePriceResponse DTO로부터 실제 분봉데이터가 들어있는 OverseasMinutePriceItem 리스트를 꺼낸다.
            List<OverseasMinutePriceItem> pageItems = response.output2() == null ? List.of() : response.output2();
            if (pageItems.isEmpty()) {
                break;
            }
            result.addAll(pageItems);
			// 이번 청크에서 조회한 데이터중 가장 오래된 시각을 계산한다.
            OverseasMinutePriceItem oldest = pageItems.stream()
                    .filter(item -> item.localDate() != null && item.localTime() != null)
                    .min((left, right) -> minuteDateTime(left).compareTo(minuteDateTime(right)))
                    .orElse(null);
            if (oldest == null) {
                break;
            }
			// 다음에 조회할 데이터 기준 시각을 이번 청크에서 조회한 데이터중 가장 오래된 시각 -1 로 설정한다.
            String nextKey = minuteDateTime(oldest).minusMinutes(1).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            if (nextKey.equals(keyBuffer)) {
                break;
            }
            keyBuffer = nextKey;
            if (pageItems.size() < 120) {
                break;
            }
        }
        return result;
    }

	// Item에서 거래일자와 기준시각를 꺼낸다.
    private LocalDateTime minuteDateTime(OverseasMinutePriceItem item) {
        return LocalDateTime.of(
                LocalDate.parse(item.localDate(), DATE),
                LocalTime.parse(item.localTime().substring(0, 6), TIME));
    }

	// KIS API로부터 받은 국내 분봉 데이터목록(정보)을 저장하는 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticMinutePriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output2") List<DomesticMinutePriceItem> output2
    ) implements KisApiResponse {
    }

	// 실제 국내 분봉데이터를 저장하는 레코드
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomesticMinutePriceItem(
            @JsonProperty("stck_bsop_date") String tradeDate,
            @JsonProperty("stck_cntg_hour") String tradeTime,
            @JsonProperty("stck_prpr") String closePrice,
            @JsonProperty("stck_oprc") String openPrice,
            @JsonProperty("stck_hgpr") String highPrice,
            @JsonProperty("stck_lwpr") String lowPrice,
            @JsonProperty("cntg_vol") String volume,
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount
    ) {
    }

	// KIS API로부터 받은 해외 분봉 데이터목록(정보)을 저장하는 DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasMinutePriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output2") List<OverseasMinutePriceItem> output2
    ) implements KisApiResponse {
    }

	// 실제 해외 분봉데이터를 저장하는 레코드
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasMinutePriceItem(
            @JsonProperty("xymd") String localDate,
            @JsonProperty("xhms") String localTime,
            @JsonProperty("open") String openPrice,
            @JsonProperty("high") String highPrice,
            @JsonProperty("low") String lowPrice,
            @JsonProperty("last") String closePrice,
            @JsonProperty("evol") String volume,
            @JsonProperty("eamt") String tradeAmount
    ) {
    }
}
