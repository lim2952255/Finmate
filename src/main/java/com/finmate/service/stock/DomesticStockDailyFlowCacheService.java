package com.finmate.service.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.stock.dto.detail.DomesticStockDailyFlowSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// 장중에 계속 변하는 투자자 수급·공매도·대차의 금일 스냅샷을 DB에 계속 update하는 것이 아니라, Redis에 캐싱한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockDailyFlowCacheService {
    private static final String KEY_PREFIX = "stock:daily-flow";

    private final StringRedisTemplate stringRedisTemplate; // Redis에 문자열 형태(JSON)로 데이터를 저장하기 위해 사용하는 Redis 객체
    private final ObjectMapper objectMapper; // Json 문자열을 객체로 변환하는 객체

    public Optional<DomesticStockDailyFlowSnapshot> get(String symbol) {
        try {
            // Redis에서 데이터 조회 후, DTO로 변환항 리턴한다.
            String value = stringRedisTemplate.opsForValue().get(key(symbol));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, DomesticStockDailyFlowSnapshot.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("국내 종목 금일 수급 Redis 조회에 실패했습니다. symbol={}", symbol, e);
            return Optional.empty();
        }
    }

    public void put(String symbol, DomesticStockDailyFlowSnapshot snapshot, Duration ttl) {
        if (snapshot == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(symbol), objectMapper.writeValueAsString(snapshot), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("국내 종목 금일 수급 Redis 저장에 실패했습니다. symbol={}", symbol, e);
        }
    }

    // Redis에서 데이터 삭제
    public void evict(String symbol) {
        try {
            stringRedisTemplate.delete(key(symbol));
        } catch (RuntimeException e) {
            log.warn("국내 종목 금일 수급 Redis 삭제에 실패했습니다. symbol={}", symbol, e);
        }
    }

    private String key(String symbol) {
        return KEY_PREFIX + ":" + symbol;
    }
}
