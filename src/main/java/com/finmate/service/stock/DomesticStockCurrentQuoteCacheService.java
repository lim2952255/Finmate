package com.finmate.service.stock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.stock.dto.detail.DomesticStockCurrentQuoteSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 국내 주식 종목의 현재가 시세를 Redis에 캐싱하는 서비스
// 만약 Redis에 캐싱을 실패하는 경우를 대비하여 JVM 메모리에도 데이터를 저장해둔다(FallBack)
// 현재는 단일 서버만으로 동작하기 때문에 Redis없이 JVM 메모리만을 사용해도 되지만, 추후에 MSA로 확장하게 되면, 각 서비스들이 메모리를 공유할 수 있도록 Redis를 사용해야 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DomesticStockCurrentQuoteCacheService {
    private static final String KEY_PREFIX = "stock:price"; // Redis에서 사용할 키 설정

    private final StringRedisTemplate stringRedisTemplate; // Redis에서 String, 즉 Json 문자열을 저장할 수 있도록 지원하는 객체
    private final ObjectMapper objectMapper; // 객체를 Json 문자열로 변환하거나, Json 문자열을 객체로 변환하는 로직
    // Redis에 캐싱을 실패하는 경우, FallBack을 할 수 있는 JVM Memory
    private final ConcurrentHashMap<String, DomesticStockCurrentQuoteSnapshot> localFallback =
            new ConcurrentHashMap<>();

    public Optional<DomesticStockCurrentQuoteSnapshot> get(String symbol, Duration ttl) {
        try {
            // 만약 Redis에 데이터가 캐싱되어 있다면 캐싱된 Json 문자열을 객체로 변환해서 리턴한다.
            String cachedValue = stringRedisTemplate.opsForValue().get(key(symbol));
            if (cachedValue != null && !cachedValue.isBlank()) {
                return Optional.of(objectMapper.readValue(cachedValue, DomesticStockCurrentQuoteSnapshot.class));
            }
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("국내 종목 현재가 Redis 조회에 실패했습니다. symbol={}", symbol, e);
        }
        // 만약 Redis 조회시에 에러가 발생한 경우, JVM 메모리에 저장해둔 데이터를 리턴한다.
        DomesticStockCurrentQuoteSnapshot local = localFallback.get(symbol);
        if (local == null || local.fetchedAt() == null || local.fetchedAt().plus(ttl).isBefore(LocalDateTime.now())) {
            localFallback.remove(symbol);
            return Optional.empty();
        }
        return Optional.of(local);
    }

    public void put(String symbol, DomesticStockCurrentQuoteSnapshot snapshot, Duration ttl) {
        if (snapshot == null) {
            return;
        }
        // Redis에 문제가 발생하는 경우를 대비해서 JVM 메모리에 우선적으로 캐싱한다.
        localFallback.put(symbol, snapshot);
        try {
            // Redis에 객체를 Json 문자열로 변환해서 저장한다.
            stringRedisTemplate.opsForValue().set(key(symbol), objectMapper.writeValueAsString(snapshot), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("국내 종목 현재가 Redis 저장에 실패해 JVM 캐시만 사용합니다. symbol={}", symbol, e);
        }
    }

    private String key(String symbol) {
        return KEY_PREFIX + ":" + symbol;
    }
}
