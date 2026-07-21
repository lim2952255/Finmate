package com.finmate.service.stock;

import com.finmate.domain.stock.FavoriteStock;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.favorite.FavoriteStockPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchType;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockService {
    private static final int FAVORITE_STOCK_PAGE_SIZE = 10;
    private static final int STOCK_SEARCH_PAGE_SIZE = 10;

    private final StockRepository stockRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final UserRepository userRepository;
    private final StockIndustryCodeService stockIndustryCodeService;

    // 검색 시 종목명 / 업종명을 선택할 수 있다.
    public StockSearchPageInfo getStockSearchPageInfo(Long userId, String keyword, StockSearchType searchType, int page) {
        return getStockSearchPageInfo(userId, keyword, searchType, null, page);
    }

    public StockSearchPageInfo getStockSearchPageInfo(Long userId,
                                                      String keyword,
                                                      StockSearchType searchType,
                                                      StockMarketType marketType,
                                                      int page) {
        String normalizedKeyword = normalizeKeyword(keyword);
        // searchType의 기본값은 종목명 기반
        StockSearchType selectedSearchType = searchType == null ? StockSearchType.STOCK : searchType;
        Pageable pageable = PageRequest.of(
                PaginationInfo.safePage(page),
                STOCK_SEARCH_PAGE_SIZE,
                Sort.unsorted());

        // searchType에 따라 적절한 종목을 검색한다음 결과를 반환한다.
        Page<Stock> stockPage = findStockWithKeyword(userId, normalizedKeyword, selectedSearchType, marketType, pageable);
        // 검색된 종목별로 표시할 가장 세부적인 업종명을 반환한다.
        Map<Long, String> industryNamesByStockId =
                stockIndustryCodeService.resolveIndustryNamesByStocks(stockPage.getContent());

        return new StockSearchPageInfo(normalizedKeyword, selectedSearchType, marketType, stockPage, industryNamesByStockId);
    }
    // searchType에 따라 다른 SQL을 실행한다.
    @Transactional(readOnly = true)
    public Page<Stock> findStockWithKeyword(Long userId, String keyword, StockSearchType searchType, Pageable pageable) {
        return findStockWithKeyword(userId, keyword, searchType, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Stock> findStockWithKeyword(Long userId,
                                            String keyword,
                                            StockSearchType searchType,
                                            StockMarketType marketType,
                                            Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        String normalizedKeyword = keyword.trim();
        StockSearchType selectedSearchType = searchType == null ? StockSearchType.STOCK : searchType;
        if (selectedSearchType == StockSearchType.INDUSTRY) {
            return stockRepository.searchByIndustryKeywordWithFavoriteFirst(userId, normalizedKeyword, marketType, pageable);
        }

        return stockRepository.searchByKeywordWithFavoriteFirst(userId, normalizedKeyword, marketType, pageable);
    }

    @Transactional(readOnly = true)
    public List<Long> findFavoriteStockIds(Long userId) {
        return favoriteStockRepository.findStockIdsByUserId(userId);
    }

    public FavoriteStockPageInfo getFavoriteStockPageInfo(Long userId, int page) {
        Pageable pageable = PageRequest.of(
                PaginationInfo.safePage(page),
                FAVORITE_STOCK_PAGE_SIZE);

        Page<FavoriteStock> favoriteStockPage = favoriteStockRepository.findPageByUserId(userId, pageable);
        Map<Long, String> industryNamesByStockId = stockIndustryCodeService.resolveIndustryNamesByStocks(
                favoriteStockPage.getContent().stream()
                        .map(FavoriteStock::getStock)
                        .toList());

        return new FavoriteStockPageInfo(favoriteStockPage, industryNamesByStockId);
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
