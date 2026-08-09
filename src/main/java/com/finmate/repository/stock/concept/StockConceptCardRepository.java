package com.finmate.repository.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCard;
import com.finmate.domain.stock.concept.StockConceptCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockConceptCardRepository extends JpaRepository<StockConceptCard, Long> {
    Optional<StockConceptCard> findByConceptCodeAndActiveTrue(StockConceptCode conceptCode);
}
