package com.finmate.service.stock.price;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockPriceLine;
import com.finmate.domain.user.User;
import com.finmate.repository.stock.StockPriceLineRepository;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

// 주식차트에서 사용자가 특정 가격에 그어둔 가로선을 DB에 저장/조회/삭제하는 서비스
@Service
@RequiredArgsConstructor
public class StockPriceLineService {
    private final StockPriceLineRepository priceLineRepository; // 사용자 가로선을 저장하는 레파지터리
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<StockPriceLine> getLines(Long userId, Long stockId) {
        requireStock(stockId);
		// 특정 사용자가 특정 종목에 설정해둔 모든 가로선정보를 조회한다.
        return priceLineRepository.findAllByUser_IdAndStock_IdOrderByCreatedAtAsc(userId, stockId);
    }

	// 특정 사용자가 특정 종목에 새로운 가로선을 추가한다.
    @Transactional
    public StockPriceLine createLine(Long userId, Long stockId, BigDecimal price) {
        BigDecimal normalizedPrice = normalizePrice(price);
		// 만약 이미 해당하는 가로선이 존재한다면 해당 가로선을 리턴한다.
		// 만약 해당 가로선이 존재하지 않다면 새로운 가로선 엔티티를 생성해서 저장한다.
        return priceLineRepository.findByUser_IdAndStock_IdAndPrice(userId, stockId, normalizedPrice)
                .orElseGet(() -> priceLineRepository.save(StockPriceLine.create(
                        requireUser(userId), requireStock(stockId), normalizedPrice)));
    }

	// 특정 사용자 가로선을 제거한다.
    @Transactional
    public void deleteLine(Long userId, Long stockId, Long lineId) {
        StockPriceLine line = priceLineRepository.findByIdAndUser_IdAndStock_Id(lineId, userId, stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가로선을 찾을 수 없습니다."));
        priceLineRepository.delete(line);
    }

	// 모든 사용자 가로선을 제거한다.
    @Transactional
    public void deleteAllLines(Long userId, Long stockId) {
        requireStock(stockId);
        priceLineRepository.deleteAllByUser_IdAndStock_Id(userId, stockId);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "가로선 가격은 0보다 커야 합니다.");
        }
        if (price.precision() - price.scale() > 13 || Math.max(price.scale(), 0) > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "가로선 가격의 자릿수가 너무 큽니다.");
        }
        return price.stripTrailingZeros();
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Stock requireStock(Long stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
    }
}
