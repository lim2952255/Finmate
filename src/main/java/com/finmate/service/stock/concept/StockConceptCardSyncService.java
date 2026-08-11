package com.finmate.service.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCard;
import com.finmate.domain.stock.concept.StockConceptCode;
import com.finmate.repository.stock.concept.StockConceptCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

// 실제 개념 카드 정보를 동기화하는 과정. 만약 개념정보에 수정이 필요한경우, 이때 수정된 개념이 갱신된다.
@Service
@RequiredArgsConstructor
public class StockConceptCardSyncService {
    private final StockConceptCardRepository conceptCardRepository;
    private final StockConceptSeedYamlLoader seedYamlLoader;

    @Transactional
    public SyncResult syncOfficialConceptCards() {
        Map<StockConceptCode, StockConceptCard> savedCards = new EnumMap<>(StockConceptCode.class);
        conceptCardRepository.findAll().forEach(card -> savedCards.put(card.getConceptCode(), card));

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        // Yaml에서 각 개념카드 정보를 읽고, DB에 동기화한다.
        for (StockConceptSeedDefinition definition : seedYamlLoader.load()) {
            StockConceptCard savedCard = savedCards.get(definition.conceptCode());
            if (savedCard == null) {
                conceptCardRepository.save(createCard(definition));
                createdCount++;
                continue;
            }

            boolean updated = savedCard.update(
                    definition.title(),
                    definition.summary(),
                    definition.detailedExplanation(),
                    definition.bakeryExample(),
                    definition.marketImpact(),
                    definition.caution(),
                    true
            );
            if (updated) {
                updatedCount++;
            } else {
                unchangedCount++;
            }
        }

        return new SyncResult(createdCount, updatedCount, unchangedCount);
    }

    // Yaml 파일을 읽어 개념정보를 StockConceptSeedDefinition 레코드에 담고, 이를 활용해서 StockConceptCard 엔티티를 생성한다.
    private StockConceptCard createCard(StockConceptSeedDefinition definition) {
        return StockConceptCard.create(
                definition.conceptCode(),
                definition.title(),
                definition.summary(),
                definition.detailedExplanation(),
                definition.bakeryExample(),
                definition.marketImpact(),
                definition.caution(),
                true
        );
    }

    public record SyncResult(int createdCount, int updatedCount, int unchangedCount) {
    }
}
