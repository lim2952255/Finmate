package com.finmate.repository.market.news;

import com.finmate.domain.market.news.MarketReportCache;
import com.finmate.domain.market.news.MarketReportTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketReportCacheRepository extends JpaRepository<MarketReportCache, Long> {
    Optional<MarketReportCache> findByTopic(MarketReportTopic topic);
}
