package com.finmate.infra.kis.stock.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.finmate.infra.kis.core.KisApiResponse;
import com.finmate.infra.kis.rest.KisRestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finmate.infra.kis.parser.KisValueParser.firstPresentText;
import static com.finmate.infra.kis.parser.KisValueParser.requiredCode;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KIS API를 통해 해외 거래소별 업종코드 정보를 받는 클라이언트
@Component
@RequiredArgsConstructor
public class KisOverseasStockIndustryCodeClient {
    private static final String INDUSTRY_CODE_PATH =
            "/uapi/overseas-price/v1/quotations/industry-price";
    private static final String INDUSTRY_CODE_TR_ID = "HHDFS76370100";

    private final KisRestClient kisRestClient; // KIS API와 실제 연결을 담당하는 클라이언트

    public List<OverseasIndustryCodeItem> fetchIndustryCodes(String exchangeCode) {
        validateRequired(exchangeCode, "해외 거래소코드는 필수입니다.");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("EXCD", exchangeCode);
        params.put("AUTH", "");

        OverseasIndustryCodeResponse response = kisRestClient.get(
                INDUSTRY_CODE_PATH,
                INDUSTRY_CODE_TR_ID,
                params,
                OverseasIndustryCodeResponse.class);

        return parseIndustryCodeItems(response.output2());
    }

    // KIS API 응답결과를 파싱해서 저장하는 메서드
    static List<OverseasIndustryCodeItem> parseIndustryCodeItems(JsonNode output) {
        List<OverseasIndustryCodeItem> items = new ArrayList<>();
        if (output == null || output.isNull() || output.isMissingNode()) {
            return items;
        }

        if (output.isArray()) {
            output.forEach(node -> addIndustryCodeItem(items, node));
            return items;
        }

        addIndustryCodeItem(items, output);
        return items;
    }

    private static void addIndustryCodeItem(List<OverseasIndustryCodeItem> items, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }

        String industryCode = firstPresentText(
                node,
                "icod",
                "ICOD",
                "industryCode",
                "industry_code",
                "ind_code",
                "indsCode",
                "inds_code",
                "idxCode",
                "idx_code",
                "theme_code",
                "code");
        String industryName = firstPresentText(
                node,
                "inm",
                "INM",
                "inam",
                "INAM",
                "iname",
                "itnm",
                "idnm",
                "industryName",
                "industry_name",
                "ind_name",
                "indsName",
                "inds_name",
                "idxName",
                "idx_name",
                "theme_name",
                "name",
                "kor_name",
                "korn_name",
                "eng_name",
                "ename");

        if (industryCode == null || industryName == null) {
            return;
        }

        items.add(new OverseasIndustryCodeItem(
                requiredCode(industryCode, "해외 업종코드는 필수입니다."),
                industryName.trim()));
    }

    // KIS API로부터 받은 업종 코드정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverseasIndustryCodeResponse(
            @JsonProperty("rt_cd") String rtCd,
            @JsonProperty("msg_cd") String msgCd,
            @JsonProperty("msg1") String msg1,
            @JsonProperty("output1") JsonNode output1,
            @JsonProperty("output2") JsonNode output2
    ) implements KisApiResponse {
    }

    public record OverseasIndustryCodeItem(String industryCode, String name) {
    }
}
