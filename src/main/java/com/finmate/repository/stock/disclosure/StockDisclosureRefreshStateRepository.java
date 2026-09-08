package com.finmate.repository.stock.disclosure;

import com.finmate.domain.stock.disclosure.StockDisclosureRefreshState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 종목별 시황/공시 마지막 정상 갱신 상태를 조회한다. 이를 통해 마지막 갱신시각으로부터 특정 TTL이 지난 종목들에 대해서만 시황/공시정보를 다시 조회한다.
public interface StockDisclosureRefreshStateRepository extends JpaRepository<StockDisclosureRefreshState, Long> {
    Optional<StockDisclosureRefreshState> findByStock_Id(Long stockId);
}
