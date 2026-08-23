package com.finmate.domain.stock;

import com.finmate.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 사용자가 특정 종목 차트에 직접 표시한 지지선·저항선 가격을 저장한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_price_line",
		// 이때 한 사용자가 동일한 가격에 대해서 사용자선을 중복으로 생성하는 것을 방지하기 위해 UniqueConstraint를 설정한다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_price_line_user_stock_price",
                columnNames = {"user_id", "stock_id", "price"}
        )
)
@Entity
public class StockPriceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    // 사용자가 선택한 캔들의 종가다. 주문 가격이 아니라 차트 표시용 기준 가격이다.
    @Column(nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 사용자선 생성시점

    public static StockPriceLine create(User user, Stock stock, BigDecimal price) {
        validateRequired(user, "사용자 정보는 필수입니다.");
        validateRequired(stock, "종목 정보는 필수입니다.");
        validatePositive(price, "가로선 가격은 0보다 커야 합니다.");

        StockPriceLine line = new StockPriceLine();
        line.user = user;
        line.stock = stock;
        line.price = price;
        return line;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
