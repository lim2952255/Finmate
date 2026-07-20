package com.finmate.service.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// 해외 주가지수 / 환율 분봉 데이터를 1분마다 KIS API요청을 통해 받은 다음, 이를 Redis에 캐싱하는 서비스
// 이때 StringRedisTemplate을 사용하기 때문에, Redis에는 객체가 그대로 저장되는게 아니라 문자열로 저장된다. 따라서 ObjectMapper가 필수적이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimeCacheService {
    private static final String KEY_PREFIX = "market:realtime"; // redis에 저장할때 사용할 Key 이름

    private final StringRedisTemplate stringRedisTemplate; // Redis에서 문자열 형태의 키와 값 저장하고 조회하는 스프링에서 제공하는 객체
    private final ObjectMapper objectMapper; // JSON 문자열 <-> 자바 객체 매핑

    // Redis에서 캐싱된 데이터를 조회하는 메서드
    public Optional<MarketRealtimeMessage> get(MarketIndicatorSymbol indicator) {
        try {
            // Redis에 캐싱된 데이터를 Key를 기반으로 조회한다. 이때 조회된 데이터는 JSON 문자열형태이다.
            String cachedValue = stringRedisTemplate.opsForValue().get(key(indicator));
            if (cachedValue == null || cachedValue.isBlank()) {
                return Optional.empty();
            }
            // 조회한 JSON 문자열을 MarketRealtimeMessage 객체로 매핑한다.
            return Optional.of(objectMapper.readValue(cachedValue, MarketRealtimeMessage.class));
        } catch (RuntimeException e) {
            log.warn("시장지표 실시간 Redis 조회에 실패했습니다. indicator={}", indicator, e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("시장지표 실시간 캐시 역직렬화에 실패했습니다. indicator={}", indicator, e);
            return Optional.empty();
        }
    }

    // Redis에 데이터를 캐싱하는 메서드
    public void put(MarketRealtimeMessage message, Duration ttl) {
        if (message == null || message.indicator() == null) {
            return;
        }

        try {
            // 캐싱하고자 하는 데이터(객체)를 JSON 문자열로 변환한다.
            String cacheValue = objectMapper.writeValueAsString(message);
            // Redis에 TTL을 설정해서 캐싱한다.
            stringRedisTemplate.opsForValue().set(key(message.indicator()), cacheValue, ttl);
        } catch (RuntimeException e) {
            log.warn("시장지표 실시간 Redis 저장에 실패했습니다. indicator={}", message.indicator(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("시장지표 실시간 캐시 직렬화에 실패했습니다.", e);
        }
    }

    // PREFIX를 붙여 key 이름을 완성하는 메서드
    private String key(MarketIndicatorSymbol indicator) {
        return KEY_PREFIX + ":" + indicator.name();
    }
}
