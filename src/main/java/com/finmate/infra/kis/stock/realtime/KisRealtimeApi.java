package com.finmate.infra.kis.stock.realtime;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
// 국내 종목 / 해외 종목 / 국내 주가지 종류별 메타데이터를 정의
public enum KisRealtimeApi {
    DOMESTIC_STOCK_TRADE(
            "H0UNCNT0",
            List.of(
                    "MKSC_SHRN_ISCD",
                    "STCK_CNTG_HOUR",
                    "STCK_PRPR",
                    "PRDY_VRSS_SIGN",
                    "PRDY_VRSS",
                    "PRDY_CTRT",
                    "WGHN_AVRG_STCK_PRC",
                    "STCK_OPRC",
                    "STCK_HGPR",
                    "STCK_LWPR",
                    "ASKP1",
                    "BIDP1",
                    "CNTG_VOL",
                    "ACML_VOL",
                    "ACML_TR_PBMN",
                    "SELN_CNTG_CSNU",
                    "SHNU_CNTG_CSNU",
                    "NTBY_CNTG_CSNU",
                    "CTTR",
                    "SELN_CNTG_SMTN",
                    "SHNU_CNTG_SMTN",
                    "CNTG_CLS_CODE",
                    "SHNU_RATE",
                    "PRDY_VOL_VRSS_ACML_VOL_RATE",
                    "OPRC_HOUR",
                    "OPRC_VRSS_PRPR_SIGN",
                    "OPRC_VRSS_PRPR",
                    "HGPR_HOUR",
                    "HGPR_VRSS_PRPR_SIGN",
                    "HGPR_VRSS_PRPR",
                    "LWPR_HOUR",
                    "LWPR_VRSS_PRPR_SIGN",
                    "LWPR_VRSS_PRPR",
                    "BSOP_DATE",
                    "NEW_MKOP_CLS_CODE",
                    "TRHT_YN",
                    "ASKP_RSQN1",
                    "BIDP_RSQN1",
                    "TOTAL_ASKP_RSQN",
                    "TOTAL_BIDP_RSQN",
                    "VOL_TNRT",
                    "PRDY_SMNS_HOUR_ACML_VOL",
                    "PRDY_SMNS_HOUR_ACML_VOL_RATE",
                    "HOUR_CLS_CODE",
                    "MRKT_TRTM_CLS_CODE",
                    "VI_STND_PRC"
            ),
            "MKSC_SHRN_ISCD",
            "STCK_PRPR",
            "PRDY_VRSS",
            "PRDY_CTRT",
            "STCK_CNTG_HOUR"
    ),
    OVERSEAS_STOCK_TRADE(
            "HDFSCNT0",
            List.of(
                    "SYMB",
                    "ZDIV",
                    "TYMD",
                    "XYMD",
                    "XHMS",
                    "KYMD",
                    "KHMS",
                    "OPEN",
                    "HIGH",
                    "LOW",
                    "LAST",
                    "SIGN",
                    "DIFF",
                    "RATE",
                    "PBID",
                    "PASK",
                    "VBID",
                    "VASK",
                    "EVOL",
                    "TVOL",
                    "TAMT",
                    "BIVL",
                    "ASVL",
                    "STRN",
                    "MTYP"
            ),
            "SYMB",
            "LAST",
            "DIFF",
            "RATE",
            "XHMS"
    ),
    DOMESTIC_INDEX_TRADE(
            "H0UPCNT0",
            List.of(
                    "bstp_cls_code",
                    "bsop_hour",
                    "prpr_nmix",
                    "prdy_vrss_sign",
                    "bstp_nmix_prdy_vrss",
                    "acml_vol",
                    "acml_tr_pbmn",
                    "pcas_vol",
                    "pcas_tr_pbmn",
                    "prdy_ctrt",
                    "oprc_nmix",
                    "nmix_hgpr",
                    "nmix_lwpr",
                    "oprc_vrss_nmix_prpr",
                    "oprc_vrss_nmix_sign",
                    "hgpr_vrss_nmix_prpr",
                    "hgpr_vrss_nmix_sign",
                    "lwpr_vrss_nmix_prpr",
                    "lwpr_vrss_nmix_sign",
                    "prdy_clpr_vrss_oprc_rate",
                    "prdy_clpr_vrss_hgpr_rate",
                    "prdy_clpr_vrss_lwpr_rate",
                    "uplm_issu_cnt",
                    "ascn_issu_cnt",
                    "stnr_issu_cnt",
                    "down_issu_cnt",
                    "lslm_issu_cnt",
                    "qtqt_ascn_issu_cnt",
                    "qtqt_down_issu_cnt",
                    "tick_vrss"
            ),
            "bstp_cls_code",
            "prpr_nmix",
            "bstp_nmix_prdy_vrss",
            "prdy_ctrt",
            "bsop_hour"
    );

    private final String trId;
    private final List<String> columns;
    private final String keyColumn;
    private final String priceColumn;
    private final String changeColumn;
    private final String changeRateColumn;
    private final String timeColumn;

    KisRealtimeApi(String trId,
                   List<String> columns,
                   String keyColumn,
                   String priceColumn,
                   String changeColumn,
                   String changeRateColumn,
                   String timeColumn) {
        this.trId = trId;
        this.columns = columns;
        this.keyColumn = keyColumn;
        this.priceColumn = priceColumn;
        this.changeColumn = changeColumn;
        this.changeRateColumn = changeRateColumn;
        this.timeColumn = timeColumn;
    }
    // WebSocket 메세지에서 받 trId 어떤 api인지를 찾는 메서드
    public static Optional<KisRealtimeApi> findByTrId(String trId) {
        if (trId == null || trId.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(api -> api.trId.equals(trId))
                .findFirst();
    }

    public String getTrId() {
        return trId;
    }

    public List<String> getColumns() {
        return columns;
    }

    public String getKeyColumn() {
        return keyColumn;
    }

    public String getPriceColumn() {
        return priceColumn;
    }

    public String getChangeColumn() {
        return changeColumn;
    }

    public String getChangeRateColumn() {
        return changeRateColumn;
    }

    public String getTimeColumn() {
        return timeColumn;
    }
}
