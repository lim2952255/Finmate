package com.finmate.controller.stock;

import com.finmate.domain.stock.StockPriceLine;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.stock.price.StockPriceLineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 사용자선을 등록하고 조회하는 REST API
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockId}/price-lines")
public class StockPriceLineController {
    private final StockPriceLineService priceLineService;

	// 특정 사용자의 특정 종목에 대한 가로선정보를 리턴한다.
    @GetMapping
    public List<PriceLineResponse> getLines(
            @PathVariable Long stockId,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        return priceLineService.getLines(principal.getId(), stockId).stream()
                .map(PriceLineResponse::from)
                .toList();
    }

	// 특정 사용자가 특정 종목에 대해 새로운 가로선을 추가한다.
    @PostMapping
    public PriceLineResponse createLine(
            @PathVariable Long stockId,
            @Valid @RequestBody CreatePriceLineRequest request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        return PriceLineResponse.from(priceLineService.createLine(principal.getId(), stockId, request.price()));
    }

	// 특정 사용자가 특정 종목에 대한 특정 가로선을 제거한다.
    @DeleteMapping("/{lineId}")
    public ResponseEntity<Void> deleteLine(
            @PathVariable Long stockId,
            @PathVariable Long lineId,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        priceLineService.deleteLine(principal.getId(), stockId, lineId);
        return ResponseEntity.noContent().build();
    }

	// 특정 사용자의 특정 종목에 대한 모든 가로선을 제거한다.
    @DeleteMapping
    public ResponseEntity<Void> deleteAllLines(
            @PathVariable Long stockId,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        priceLineService.deleteAllLines(principal.getId(), stockId);
        return ResponseEntity.noContent().build();
    }

    public record CreatePriceLineRequest(
            @NotNull
            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 13, fraction = 6)
            BigDecimal price) {
    }

    public record PriceLineResponse(Long id, String price, LocalDateTime createdAt) {
        static PriceLineResponse from(StockPriceLine line) {
            return new PriceLineResponse(line.getId(), line.getPrice().toPlainString(), line.getCreatedAt());
        }
    }
}
