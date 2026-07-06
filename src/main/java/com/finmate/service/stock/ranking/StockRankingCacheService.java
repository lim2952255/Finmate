package com.finmate.service.stock.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.ranking.StockRankingBoard;
import com.finmate.domain.stock.dto.ranking.StockRankingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

// Redis에 랭킹 데이터를 저장/조회하는 캐시 서비스
// Redis에는 객체를 저장할 수 없기 때문에, StockRankingBoard 객체를 Json 문자열로 변환하여 Redis에 저장한다.
// 이후 Redis에서 Json 문자열을 조회하여 StockRankingBoard 객체로 복원한다.

// Redis는 메모리에 데이터를 저장하며, key-value 구조로 저장하고, TTL을 설정할 수 있다.
// 그리고 캐시, 세션, 토큰, 랭킹, 카운터 등에 사용한다. 즉 외부 API결과를 잠깐 저장해두는 캐시 저장소 역할을 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRankingCacheService {
    // Redis에 데이터를 저장할 때 key 이름의 앞부분(prefix)를 설정한다. Redis에서는 key를 구분할 때 기본적으로 :로 설정한다.
    // stock:ranking:KOSPI:TRADE_AMOUNT
    // stock:ranking:KOSPI:VOLUME
    // stock:ranking:KOSDAQ:TRADE_AMOUNT
    // stock:ranking:KOSDAQ:VOLUME
    // stock:ranking:NASDAQ:TRADE_AMOUNT
    // stock:ranking:NASDAQ:VOLUME
    private static final String KEY_PREFIX = "stock:ranking";

    // Redis에 실제로 접근하는 객체 (Spring Data Redis가 제공하는 클래스)
    // Redis의 key와 value를 문자열 중심으로 다루는 도구
    // 사실상 StringRedisTemplate을 통해서 Redis 캐시에 데이터를 저장하고, 캐시에서 데이터를 조회할 수 있다.
    private final StringRedisTemplate stringRedisTemplate;
    // 객체 -> Json 또는 Json -> 객체로 변환하는 자바 객체
    private final ObjectMapper objectMapper;

    // Redis에 랭킹 데이터를 조회하는 메서드
    public Optional<StockRankingBoard> get(StockMarketType marketType, StockRankingType rankingType) {
        try {
            // Redis에서 키를 기반으로 value(문자열 데이터)를 조회한다.
            String cachedValue = stringRedisTemplate.opsForValue().get(key(marketType, rankingType));
            if (cachedValue == null || cachedValue.isBlank()) {
                // 만약 키에 해당하는 데이터가 존재하지 않다면 empty를 리턴한다.
                return Optional.empty();
            }
            // 키에 해당하는 데이터가 존재한다면 ObjectMapper를 활용해서 value(문자열 데이터)를 StockRankingBoard 객체로 변환한다.
            return Optional.of(objectMapper.readValue(cachedValue, StockRankingBoard.class));
        } catch (RuntimeException e) {
            log.warn("거래량/거래대금 랭킹 Redis 조회에 실패했습니다. market={}, type={}", marketType, rankingType, e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("거래량/거래대금 랭킹 캐시 역직렬화에 실패했습니다. market={}, type={}", marketType, rankingType, e);
            return Optional.empty();
        }
    }

    // StockRankingBoard를 Redis에 저장하는 메서드
    // Redis에 데이터를 저장할때에는 항상 문자열 형태로 저장해야되기 때문에 ObjectMapper를 활용하여 객체를 Json 문자열로 변환해야 한다.
    public void put(StockRankingBoard rankingBoard, Duration ttl) {
        try {
            // StockRankingBoard 객체를 Json 문자열로 변환한다.
            String cacheValue = objectMapper.writeValueAsString(rankingBoard);
            // Json 데이터를 Redis에 저장할때 key와 데이터, ttl을 설정해서 Redis에 저장한다.
            stringRedisTemplate.opsForValue().set(
                    key(rankingBoard.getMarketType(), rankingBoard.getRankingType()),
                    cacheValue,
                    ttl);
        } catch (RuntimeException e) {
            log.warn("거래량/거래대금 랭킹 Redis 저장에 실패했습니다. market={}, type={}",
                    rankingBoard.getMarketType(),
                    rankingBoard.getRankingType(),
                    e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("거래량/거래대금 랭킹 캐시 직렬화에 실패했습니다.", e);
        }
    }

    // Redis에 데이터를 저장할때, Key의 이름을 생성하는 메서드
    private String key(StockMarketType marketType, StockRankingType rankingType) {
        return KEY_PREFIX + ":" + marketType.name() + ":" + rankingType.name();
    }
}
