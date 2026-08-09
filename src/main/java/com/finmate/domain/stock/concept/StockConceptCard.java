package com.finmate.domain.stock.concept;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 각 개념카드에 들어갈 정적인 개념정보를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_concept_card",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_concept_card_code",
                columnNames = "concept_code"
        )
)
@Entity
public class StockConceptCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 각 개념카드 종류
    @Enumerated(EnumType.STRING)
    @Column(name = "concept_code", nullable = false, length = 64, updatable = false)
    private StockConceptCode conceptCode;

    @Column(nullable = false, length = 120)
    private String title;

    // 개념 요약 정보
    @Column(nullable = false, length = 1000)
    private String summary;

    // 개념 상세 설명 본문
    // 이때 Lob는 JPA에게 해당 String은 대용량 텍스트이기 때문에 적절한 Column Type으로 매핑해달라고 설명을 붙이는 것
    // @Lob와 TEXT를 꼭 둘다 붙일 필요는 없음
    @Lob
    // DB에 varchar로 저장하는 것이 아니라, 긴 텍스트로 저장하기위해 column type을 TEXT로 설정
    @Column(name = "detailed_explanation", nullable = false, columnDefinition = "TEXT")
    private String detailedExplanation;

    // 빵집을 예로 들었을때의 설명
    @Lob
    @Column(name = "bakery_example", nullable = false, columnDefinition = "TEXT")
    private String bakeryExample;

    // 개념을 이해할때의 주의사항
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String caution;

    // 개념카드를 사용자에게 노출할지 여부
    @Column(nullable = false)
    private boolean active;

    // 생성일자
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정일자
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockConceptCard create(StockConceptCode conceptCode,
                                          String title,
                                          String summary,
                                          String detailedExplanation,
                                          String bakeryExample,
                                          String caution,
                                          boolean active) {
        validate(conceptCode, title, summary, detailedExplanation, bakeryExample, caution);
        StockConceptCard card = new StockConceptCard();
        card.conceptCode = conceptCode;
        card.apply(title, summary, detailedExplanation, bakeryExample, caution, active);
        return card;
    }

    public boolean update(String title,
                          String summary,
                          String detailedExplanation,
                          String bakeryExample,
                          String caution,
                          boolean active) {
        validate(conceptCode, title, summary, detailedExplanation, bakeryExample, caution);
        if (hasSameContent(title, summary, detailedExplanation, bakeryExample, caution, active)) {
            return false;
        }
        apply(title, summary, detailedExplanation, bakeryExample, caution, active);
        this.updatedAt = LocalDateTime.now();
        return true;
    }

    // 엔티티에 내용 삽입
    private void apply(String title,
                       String summary,
                       String detailedExplanation,
                       String bakeryExample,
                       String caution,
                       boolean active) {
        this.title = title;
        this.summary = summary;
        this.detailedExplanation = detailedExplanation;
        this.bakeryExample = bakeryExample;
        this.caution = caution;
        this.active = active;
    }

    // update하려는 카드 내용이 기존에 저장된 내용이랑 동일한지를 검사한다.
    private boolean hasSameContent(String title,
                                   String summary,
                                   String detailedExplanation,
                                   String bakeryExample,
                                   String caution,
                                   boolean active) {
        return Objects.equals(this.title, title)
                && Objects.equals(this.summary, summary)
                && Objects.equals(this.detailedExplanation, detailedExplanation)
                && Objects.equals(this.bakeryExample, bakeryExample)
                && Objects.equals(this.caution, caution)
                && this.active == active;
    }

    private static void validate(StockConceptCode conceptCode,
                                 String title,
                                 String summary,
                                 String detailedExplanation,
                                 String bakeryExample,
                                 String caution) {
        validateRequired(conceptCode, "개념 코드는 필수입니다.");
        validateRequired(title, "개념 카드 제목은 필수입니다.");
        validateRequired(summary, "개념 카드 요약은 필수입니다.");
        validateRequired(detailedExplanation, "개념 카드 상세 설명은 필수입니다.");
        validateRequired(bakeryExample, "개념 카드 빵집 예시는 필수입니다.");
        validateRequired(caution, "개념 카드 주의사항은 필수입니다.");
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
