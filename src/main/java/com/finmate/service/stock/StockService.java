package com.finmate.service.stock;

import com.finmate.domain.stock.FavoriteStock;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.favorite.FavoriteStockPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchPageInfo;
import com.finmate.domain.user.User;
import com.finmate.global.pagination.PaginationInfo;
import com.finmate.repository.stock.FavoriteStockRepository;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private static final int FAVORITE_STOCK_PAGE_SIZE = 10;
    private static final int STOCK_SEARCH_PAGE_SIZE = 10;

    private final StockRepository stockRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public StockSearchPageInfo getStockSearchPageInfo(Long userId, String keyword, int page) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Pageable pageable = PageRequest.of(
                PaginationInfo.safePage(page),
                STOCK_SEARCH_PAGE_SIZE,
                Sort.unsorted());

        Page<Stock> stockPage = findStockWithKeyword(userId, normalizedKeyword, pageable);

        return new StockSearchPageInfo(normalizedKeyword, stockPage);
    }

    @Transactional(readOnly = true)
    public Page<Stock> findStockWithKeyword(Long userId, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        return stockRepository.searchByKeywordWithFavoriteFirst(userId, keyword.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Long> findFavoriteStockIds(Long userId) {
        return favoriteStockRepository.findStockIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public FavoriteStockPageInfo getFavoriteStockPageInfo(Long userId, int page) {
        Pageable pageable = PageRequest.of(
                PaginationInfo.safePage(page),
                FAVORITE_STOCK_PAGE_SIZE);

        Page<FavoriteStock> favoriteStockPage = favoriteStockRepository.findPageByUserId(userId, pageable);

        return new FavoriteStockPageInfo(favoriteStockPage);
    }

    @Transactional
    public void toggleFavoriteStock(Long userId, Long stockId) {
        favoriteStockRepository.findByUser_IdAndStock_Id(userId, stockId)
                .ifPresentOrElse(
                        // 해당 종목이 이미 관심종목이였다면 관심종목 해제
                        favoriteStockRepository::delete,
                        // 해당 종목이 아직 관심종목이 아니였다면 관심종목 추가
                        () -> favoriteStockRepository.save(createFavoriteStock(userId, stockId))
                );
    }

    private FavoriteStock createFavoriteStock(Long userId, Long stockId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));

        return FavoriteStock.create(user, stock);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
