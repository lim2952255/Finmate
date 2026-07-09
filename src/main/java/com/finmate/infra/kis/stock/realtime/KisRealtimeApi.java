package com.finmate.infra.kis.stock.realtime;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
// 국내 종목 / 해외 종목 / 국내 주가지수, 실시간 호가별 메타데이터를 정의
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
    DOMESTIC_STOCK_ORDERBOOK(
            "H0UNASP0",
            List.of(
                    "MKSC_SHRN_ISCD",
                    "BSOP_HOUR",
                    "HOUR_CLS_CODE",
                    "ASKP1",
                    "ASKP2",
                    "ASKP3",
                    "ASKP4",
                    "ASKP5",
                    "ASKP6",
                    "ASKP7",
                    "ASKP8",
                    "ASKP9",
                    "ASKP10",
                    "BIDP1",
                    "BIDP2",
                    "BIDP3",
                    "BIDP4",
                    "BIDP5",
                    "BIDP6",
                    "BIDP7",
                    "BIDP8",
                    "BIDP9",
                    "BIDP10",
                    "ASKP_RSQN1",
                    "ASKP_RSQN2",
                    "ASKP_RSQN3",
                    "ASKP_RSQN4",
                    "ASKP_RSQN5",
                    "ASKP_RSQN6",
                    "ASKP_RSQN7",
                    "ASKP_RSQN8",
                    "ASKP_RSQN9",
                    "ASKP_RSQN10",
                    "BIDP_RSQN1",
                    "BIDP_RSQN2",
                    "BIDP_RSQN3",
                    "BIDP_RSQN4",
                    "BIDP_RSQN5",
                    "BIDP_RSQN6",
                    "BIDP_RSQN7",
                    "BIDP_RSQN8",
                    "BIDP_RSQN9",
                    "BIDP_RSQN10",
                    "TOTAL_ASKP_RSQN",
                    "TOTAL_BIDP_RSQN",
                    "OVTM_TOTAL_ASKP_RSQN",
                    "OVTM_TOTAL_BIDP_RSQN",
                    "ANTC_CNPR",
                    "ANTC_CNQN",
                    "ANTC_VOL",
                    "ANTC_CNTG_VRSS",
                    "ANTC_CNTG_VRSS_SIGN",
                    "ANTC_CNTG_PRDY_CTRT",
                    "ACML_VOL",
                    "TOTAL_ASKP_RSQN_ICDC",
                    "TOTAL_BIDP_RSQN_ICDC",
                    "OVTM_TOTAL_ASKP_ICDC",
                    "OVTM_TOTAL_BIDP_ICDC",
                    "STCK_DEAL_CLS_CODE",
                    "KMID_PRC",
                    "KMID_TOTAL_RSQN",
                    "KMID_CLS_CODE",
                    "NMID_PRC",
                    "NMID_TOTAL_RSQN",
                    "NMID_CLS_CODE"
            ),
            "MKSC_SHRN_ISCD",
            "ASKP1",
            "",
            "",
            "BSOP_HOUR"
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
    OVERSEAS_STOCK_ORDERBOOK(
            "HDFSASP0",
            List.of(
                    "symb",
                    "zdiv",
                    "xymd",
                    "xhms",
                    "kymd",
                    "khms",
                    "bvol",
                    "avol",
                    "bdvl",
                    "advl",
                    "pbid1",
                    "pask1",
                    "vbid1",
                    "vask1",
                    "dbid1",
                    "dask1"
            ),
            "symb",
            "pask1",
            "",
            "",
            "xhms"
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

    public boolean isOrderbook() {
        return this == DOMESTIC_STOCK_ORDERBOOK || this == OVERSEAS_STOCK_ORDERBOOK;
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
