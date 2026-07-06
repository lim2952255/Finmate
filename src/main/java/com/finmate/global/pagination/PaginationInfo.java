package com.finmate.global.pagination;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.IntStream;

// 화면 페이지네이션에 필요한 공통 정보
@Getter
public class PaginationInfo {
    private static final int PAGE_NUMBER_COUNT = 5;

    private final int currentPage;
    private final int totalPages;
    private final List<Integer> pageNumbers;

    private PaginationInfo(int currentPage, int totalPages, List<Integer> pageNumbers) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.pageNumbers = pageNumbers;
    }

    public static PaginationInfo from(Page<?> page) {
        if (page == null || page.getTotalPages() == 0) {
            return new PaginationInfo(0, 0, List.of());
        }

        int currentPage = page.getNumber();
        int totalPages = page.getTotalPages();
        int startPage = Math.max(0, currentPage - PAGE_NUMBER_COUNT / 2);
        int endPage = Math.min(totalPages - 1, startPage + PAGE_NUMBER_COUNT - 1);
        startPage = Math.max(0, endPage - PAGE_NUMBER_COUNT + 1);

        List<Integer> pageNumbers = IntStream.rangeClosed(startPage, endPage)
                .boxed()
                .toList();

        return new PaginationInfo(currentPage, totalPages, pageNumbers);
    }

    public static int safePage(int page) {
        return Math.max(page, 0);
    }
}
