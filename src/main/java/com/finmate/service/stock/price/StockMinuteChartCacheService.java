package com.finmate.service.stock.price;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.stock.dto.detail.StockMinuteChartCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// KIS API로부터 받은 분봉데이터를 Redis에 캐싱하는 서비스
// 이때 기본적으로 Redis에 분봉데이터를 캐싱하되, Redis에 문제가 생기는 경우를 대비해서 JVM 메모리에도 데이터를 캐싱한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMinuteChartCacheService {
    private static final String KEY_PREFIX = "stock:minute-candles"; // Redis에 저장할 키의 Prefix

    private final StringRedisTemplate stringRedisTemplate; // Redis에 문자열 형식(JSON)으로 데이터를 저장해주는 Redis 객체
    private final ObjectMapper objectMapper; // 자바 객체를 JSON 문자열로 변환해주는 매퍼
    // Redis 장애 시에만 사용하는 동일 프로세스 내 fallback 캐시
    private final ConcurrentHashMap<Long, StockMinuteChartCache> localFallback = new ConcurrentHashMap<>();

    @Value("${finmate.stock-detail.minute-chart-cache-days:3}")
    private long cacheDays; // 캐싱 기준일

    // Redis에서 분봉 데이터를 조회하고, 조회 실패나 캐시 미스가 발생하면 JVM fallback을 확인한다.
    public Optional<StockMinuteChartCache> get(Long stockId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key(stockId));
            if (value != null && !value.isBlank()) {
                StockMinuteChartCache cache = objectMapper.readValue(value, StockMinuteChartCache.class);
                localFallback.put(stockId, cache);
                return Optional.of(cache);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("종목 분봉 Redis 조회에 실패해 JVM 캐시를 확인합니다. stockId={}", stockId, e);
        }
        StockMinuteChartCache fallback = localFallback.get(stockId);
        if (fallback == null) {
            return Optional.empty();
        }
        if (fallback.fetchedAt() == null
                || !fallback.fetchedAt().plusDays(cacheDays).isAfter(LocalDateTime.now())) {
            localFallback.remove(stockId, fallback);
            return Optional.empty();
        }
        return Optional.of(fallback);
    }

    // JVM 메모리와 Redis에 동일한 분봉 스냅샷을 저장한다.
    public void put(Long stockId, StockMinuteChartCache cache) {
        if (stockId == null || cache == null) {
            return;
        }
        localFallback.put(stockId, cache);
        try {
            stringRedisTemplate.opsForValue().set(
                    key(stockId), objectMapper.writeValueAsString(cache), Duration.ofDays(cacheDays));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("종목 분봉 Redis 저장에 실패해 JVM 캐시만 사용합니다. stockId={}", stockId, e);
        }
    }

    private String key(Long stockId) {
        return KEY_PREFIX + ":" + stockId;
    }
}
