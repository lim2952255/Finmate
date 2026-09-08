package com.finmate.service.stock.disclosure;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.disclosure.StockDisclosure;
import com.finmate.domain.stock.disclosure.StockDisclosureRefreshState;
import com.finmate.repository.stock.disclosure.StockDisclosureRefreshStateRepository;
import com.finmate.repository.stock.disclosure.StockDisclosureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 신규 제목 저장과 마지막 정상 갱신시각 변경을 하나의 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
public class StockDisclosurePersistenceService {
    // 분석이 완료된 신규 시황/공시 항목을 저장한다.
    private final StockDisclosureRepository disclosureRepository;
    // 종목별 마지막 정상 갱신시각을 조회하고 저장한다.
    private final StockDisclosureRefreshStateRepository refreshStateRepository;

    // 분석된 신규 항목이 없어도 정상 조회였다면 갱신 상태는 기록한다.
    @Transactional
    public void storeSuccessfulRefresh(
            Stock stock,
            List<StockDisclosureService.AnalyzedDisclosure> disclosures, // 이번에 새로 조회한 시황/공시정보 목록
            LocalDateTime refreshedAt) {
        if (!disclosures.isEmpty()) {
            // 외부 호출과 FinBERT 추론은 트랜잭션 밖에서 끝내고 저장 작업만 짧게 수행한다.
            List<StockDisclosure> entities = disclosures.stream()
                    // 서비스의 분석 결과를 JPA 엔티티로 변환한다.
                    .map(disclosure -> StockDisclosure.create(
                            stock,
                            disclosure.externalKey(),
                            disclosure.contentSerialNumber(),
                            disclosure.providerCode(),
                            disclosure.title(),
                            disclosure.source(),
                            disclosure.categoryCode(),
                            disclosure.disclosedAt(),
                            disclosure.sentiment(),
                            refreshedAt))
                    .toList();
            disclosureRepository.saveAll(entities);
        }

        // 기존 갱신 상태가 없으면 새로 만들고, 있으면 같은 엔티티의 성공 시각을 변경한다.
        StockDisclosureRefreshState state = refreshStateRepository.findByStock_Id(stock.getId())
                .orElseGet(() -> StockDisclosureRefreshState.create(stock, refreshedAt));
        // 조회 결과가 0건이어도 전체 처리가 성공했으므로 정상 갱신시각을 기록한다.
        state.markSuccessful(refreshedAt);
        refreshStateRepository.save(state);
    }
}
