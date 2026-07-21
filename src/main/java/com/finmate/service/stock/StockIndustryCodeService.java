package com.finmate.service.stock;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.industry.StockIndustryClassification;
import com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils;
import com.finmate.domain.stock.industry.DomesticStockSectorCode;
import com.finmate.domain.stock.industry.OverseasStockIndustryCode;
import com.finmate.domain.stock.metadata.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import com.finmate.infra.kis.stock.master.KisOverseasStockIndustryCodeClient;
import com.finmate.infra.kis.stock.master.KisOverseasStockIndustryCodeClient.OverseasIndustryCodeItem;
import com.finmate.repository.stock.industry.DomesticStockSectorCodeRepository;
import com.finmate.repository.stock.industry.OverseasStockIndustryCodeRepository;
import com.finmate.repository.stock.metadata.DomesticStockMetadataRepository;
import com.finmate.repository.stock.metadata.OverseasStockMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.displayNameOrCode;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.isNoneCode;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.mostSpecificDomesticDisplayName;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.unknownIfBlank;

// 국내 업종코드, 해외 업종코드를 받아서 리턴하는 서비스 (만약 해외 업종코드가 없다면 온디멘드 방식으로 KIS API에 요청해서 저장)
@Slf4j
@Service
@RequiredArgsConstructor
public class StockIndustryCodeService {
    private final DomesticStockMetadataRepository domesticStockMetadataRepository;
    private final OverseasStockMetadataRepository overseasStockMetadataRepository;
    private final DomesticStockSectorCodeRepository domesticStockSectorCodeRepository;
    private final OverseasStockIndustryCodeRepository overseasStockIndustryCodeRepository;
    private final KisOverseasStockIndustryCodeClient kisOverseasStockIndustryCodeClient; // 해외 거래소별 업종 코드정보를 받는 클라이언트

    // 각 종목마다 화면에 표시할 업종명을 반환한다.
    // 종목 목록/포트폴리오 화면에서 사용할 업종명을 종목 ID별로 반환한다.
    // 국내 종목은 소업종 -> 중업종 -> 대업종 순서로 가장 세부적인 업종 하나만 선택한다.
    @Transactional
    public Map<Long, String> resolveIndustryNamesByStocks(Collection<Stock> stocks) {
        return resolveIndustryClassificationsByStocks(stocks).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // 종목 아이디를 Map의 키로 활용한다.
                        entry -> entry.getValue().displayName(),
                        (existingName, duplicateName) -> existingName,
                        LinkedHashMap::new));
    }

    // 포트폴리오 업종 비중 집계에 사용할 업종 분류를 종목 ID별로 반환한다.
    // 국내 종목은 국내 단일 분류체계를 쓰고, 해외 종목은 거래소별 업종 코드체계가 달라 거래소명을 집계 그룹으로 함께 사용한다.
    @Transactional
    public Map<Long, StockIndustryClassification> resolveIndustryClassificationsByStocks(Collection<Stock> stocks) {
        List<Stock> requestedStocks = stocks == null
                ? List.of()
                : stocks.stream()
                .filter(Objects::nonNull)
                .filter(stock -> stock.getId() != null)
                .toList();
        if (requestedStocks.isEmpty()) {
            return Map.of();
        }

        List<Long> stockIds = requestedStocks.stream()
                .map(Stock::getId)
                .distinct()
                .toList();
        // 국내 종목 메타데이터 정보 조회
        Map<Long, DomesticStockMetadata> domesticMetadataByStockId =
                domesticStockMetadataRepository.findByStockIdsWithStock(stockIds).stream()
                        .collect(Collectors.toMap(
                                metadata -> metadata.getStock().getId(),
                                Function.identity()));
        // 해외 종목 메타데이터 정보 조회
        Map<Long, OverseasStockMetadata> overseasMetadataByStockId =
                overseasStockMetadataRepository.findByStockIdsWithStock(stockIds).stream()
                        .collect(Collectors.toMap(
                                metadata -> metadata.getStock().getId(),
                                Function.identity()));

        // 국내 업종 비중을 계산하기 위해 필요한 모든 업종코드를 수집
        Map<String, String> domesticSectorNames = findDomesticSectorNames(domesticMetadataByStockId.values().stream()
                .flatMap(metadata -> Arrays.stream(new String[]{
                        metadata.getSectorLargeDivisionCode(),
                        metadata.getSectorMediumDivisionCode(),
                        metadata.getSectorSmallDivisionCode()
                }))
                .map(StockIndustryCodeService::normalizeCodeOrNull)
                .filter(Objects::nonNull)
                .filter(code -> !isNoneCode(code))
                .toList());

        Map<Long, StockIndustryClassification> industryClassificationsByStockId = new LinkedHashMap<>();
        for (Stock stock : requestedStocks) {
            // 국내 종목의 경우에는 유효한 가장 세부적인 업종명을 찾아서 저장한다.
            DomesticStockMetadata domesticMetadata = domesticMetadataByStockId.get(stock.getId());
            if (domesticMetadata != null) {
                String industryName = mostSpecificDomesticDisplayName(
                        domesticMetadata.getSectorLargeDivisionCode(),
                        domesticMetadata.getSectorMediumDivisionCode(),
                        domesticMetadata.getSectorSmallDivisionCode(),
                        domesticSectorNames::get);
                industryClassificationsByStockId.put(stock.getId(), StockIndustryClassification.domestic(industryName));
                continue;
            }

            // 해외 종목의 경우에는 거래소도 함께 저장한다.
            OverseasStockMetadata overseasMetadata = overseasMetadataByStockId.get(stock.getId());
            if (overseasMetadata != null) {
                resolveOverseasIndustryDisplayName(overseasMetadata)
                        .ifPresent(industryName -> industryClassificationsByStockId.put(
                                stock.getId(),
                                StockIndustryClassification.overseas(
                                        overseasExchangeName(stock, overseasMetadata),
                                        industryName)));
            }
        }

        return industryClassificationsByStockId;
    }

    @Transactional(readOnly = true)
    public Map<String, String> findDomesticSectorNames(Collection<String> codes) {
        List<String> normalizedCodes = codes == null
                ? List.of()
                : codes.stream()
                .map(this::normalizeCode)
                .flatMap(Optional::stream)
                .distinct()
                .toList();

        if (normalizedCodes.isEmpty()) {
            return Map.of();
        }

        // 국내 업종코드를 기반으로 업종 코드와 매핑되어 있는 업종명을 찾아서 반환한다.
        return domesticStockSectorCodeRepository.findByCodeIn(normalizedCodes).stream()
                .collect(Collectors.toMap(
                        DomesticStockSectorCode::getCode,
                        DomesticStockSectorCode::getNameKo,
                        (existingName, duplicateName) -> existingName,
                        LinkedHashMap::new));
    }

    private Optional<String> resolveOverseasIndustryDisplayName(OverseasStockMetadata metadata) {
        String normalizedIndustryCode = normalizeCodeOrNull(metadata.getIndustryCode());
        if (normalizedIndustryCode == null) {
            return Optional.empty();
        }
        if (isNoneCode(normalizedIndustryCode)) {
            return Optional.of(displayNameOrCode(normalizedIndustryCode, null));
        }

        String industryName = resolveOverseasIndustryName(metadata.getExchangeCode(), normalizedIndustryCode)
                .orElse(null);
        return Optional.ofNullable(displayNameOrCode(normalizedIndustryCode, industryName));
    }

    private String overseasExchangeName(Stock stock, OverseasStockMetadata metadata) {
        String exchangeName = unknownIfBlank(metadata.getExchangeName());
        if (!StockIndustryClassification.UNCLASSIFIED_NAME.equals(exchangeName)
                && !StockIndustryDisplayUtils.UNKNOWN_DISPLAY_VALUE.equals(exchangeName)) {
            return exchangeName;
        }

        String exchangeCode = unknownIfBlank(metadata.getExchangeCode());
        if (!StockIndustryDisplayUtils.UNKNOWN_DISPLAY_VALUE.equals(exchangeCode)) {
            return exchangeCode;
        }

        String stockExchangeCode = unknownIfBlank(stock.getExchangeCode());
        if (!StockIndustryDisplayUtils.UNKNOWN_DISPLAY_VALUE.equals(stockExchangeCode)) {
            return stockExchangeCode;
        }

        return stock.getMarketType() == null
                ? StockIndustryClassification.OVERSEAS_GROUP_NAME
                : stock.getMarketType().name();
    }

    // 해외 업종코드 정보를 찾아서 반환한다.
    public Optional<String> resolveOverseasIndustryName(String exchangeCode, String industryCode) {
        Optional<String> normalizedExchangeCode = normalizeCode(exchangeCode);
        Optional<String> normalizedIndustryCode = normalizeCode(industryCode);
        if (normalizedExchangeCode.isEmpty() || normalizedIndustryCode.isEmpty()) {
            return Optional.empty();
        }

        // 해외 업종코드가 DB에 저장되어 있는지 확인하고, 있으면 바로 리턴
        Optional<String> savedName = findOverseasIndustryName(
                normalizedExchangeCode.get(),
                normalizedIndustryCode.get());
        if (savedName.isPresent()) {
            return savedName;
        }

        try {
            // 해외 업종코드가 DB에 저장되어 있지 않으면 KisOverseasStockIndustryCodeClient를 통해서 데이터를 받는다.
            refreshOverseasIndustryCodes(normalizedExchangeCode.get());
        } catch (Exception e) {
            log.warn("해외 업종코드 조회에 실패했습니다. exchangeCode={}", normalizedExchangeCode.get(), e);
            return Optional.empty();
        }

        return findOverseasIndustryName(normalizedExchangeCode.get(), normalizedIndustryCode.get());
    }

    @Transactional(readOnly = true)
    public Optional<String> findOverseasIndustryName(String exchangeCode, String industryCode) {
        return overseasStockIndustryCodeRepository
                .findByExchangeCodeAndIndustryCode(exchangeCode, industryCode)
                .map(OverseasStockIndustryCode::getName);
    }

    // 해외 거래소 코드를 기반으로 해외 거래소별 업종코드 정보를 KisOverseasStockIndustryCodeClient를 통해 받아서 DB에 저장한다.
    @Transactional
    public void refreshOverseasIndustryCodes(String exchangeCode) {
        String normalizedExchangeCode = normalizeCode(exchangeCode)
                .orElseThrow(() -> new RuntimeException("해외 거래소코드는 필수입니다."));
        LocalDateTime syncedAt = LocalDateTime.now();
        List<OverseasIndustryCodeItem> items =
                kisOverseasStockIndustryCodeClient.fetchIndustryCodes(normalizedExchangeCode);

        for (OverseasIndustryCodeItem item : items) {
            String normalizedIndustryCode = normalizeCode(item.industryCode())
                    .orElseThrow(() -> new RuntimeException("해외 업종코드는 필수입니다."));
            OverseasStockIndustryCode industryCode = overseasStockIndustryCodeRepository
                    .findByExchangeCodeAndIndustryCode(normalizedExchangeCode, normalizedIndustryCode)
                    .orElseGet(() -> OverseasStockIndustryCode.create(
                            normalizedExchangeCode,
                            normalizedIndustryCode,
                            item.name(),
                            syncedAt));
            industryCode.updateName(item.name(), syncedAt);
            overseasStockIndustryCodeRepository.save(industryCode);
        }
    }

    private Optional<String> normalizeCode(String value) {
        return Optional.ofNullable(normalizeCodeOrNull(value));
    }

    private static String normalizeCodeOrNull(String value) {
        return StockIndustryDisplayUtils.normalizeCode(value);
    }
}
