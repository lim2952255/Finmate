package com.finmate.domain.stock;

import com.finmate.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 관심 종목 저장
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "favorite_stock",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_favorite_stock_user_stock",
                        columnNames = {"user_id", "stock_id"}
                )
        }
)
@Entity
public class FavoriteStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static FavoriteStock create(User user, Stock stock) {
        validateRequired(user, "사용자 정보는 필수입니다.");
        validateRequired(stock, "종목 정보는 필수입니다.");

        FavoriteStock favoriteStock = new FavoriteStock();
        favoriteStock.user = user;
        favoriteStock.stock = stock;
        return favoriteStock;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
