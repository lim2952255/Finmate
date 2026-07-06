package com.finmate.domain.stock.dto.master;

import com.finmate.domain.stock.StockMarketType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 마스터 파일에서 파싱한 해외 주식 데이터들을 저장할 dto
public class OverseasStockMasterDto {
    private final StockMarketType marketType;
    private final String nationalCode;
    private final String exchangeId;
    private final String exchangeCode;
    private final String exchangeName;
    private final String symbol;
    private final String realtimeSymbol;
    private final String nameKo;
    private final String nameEn;
    private final String securityTypeCode;
    private final String currency;

    private String floatPosition;
    private String dataType;
    private String basePrice;
    private String bidOrderSize;
    private String askOrderSize;
    private String marketStartTime;
    private String marketEndTime;
    private String drYn;
    private String drCountryCode;
    private String industryCode;
    private String indexConstituentYn;
    private String tickSizeType;
    private String etpType;
    private String tickSizeTypeDetail;

    public OverseasStockMasterDto(StockMarketType marketType,
                                  String nationalCode,
                                  String exchangeId,
                                  String exchangeCode,
                                  String exchangeName,
                                  String symbol,
                                  String realtimeSymbol,
                                  String nameKo,
                                  String nameEn,
                                  String securityTypeCode,
                                  String currency) {
        this.marketType = marketType;
        this.nationalCode = nationalCode;
        this.exchangeId = exchangeId;
        this.exchangeCode = exchangeCode;
        this.exchangeName = exchangeName;
        this.symbol = symbol;
        this.realtimeSymbol = realtimeSymbol;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.securityTypeCode = securityTypeCode;
        this.currency = currency;
    }
}
