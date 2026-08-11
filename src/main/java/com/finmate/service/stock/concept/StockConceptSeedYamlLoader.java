package com.finmate.service.stock.concept;

import com.finmate.domain.stock.concept.StockConceptCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StockConceptSeedYamlLoader {
    private final Resource seedResource; // YAML 파일을 가리키는 Spring에서 제공하는 Resource 객체

    public StockConceptSeedYamlLoader(
            @Value("classpath:stock-concepts/stock-concepts.yml") Resource seedResource
    ) {
        // YAML파일을 읽어서 seedResource에 주입
        this.seedResource = seedResource;
    }

    public List<StockConceptSeedDefinition> load() {
        // YamlMapFactoryBean은 YAML 파일 내용을 읽고, 자바의 Map 구조로 바꿔주는 Spring 클래스이다.
        YamlMapFactoryBean yamlFactory = new YamlMapFactoryBean();
        // yamlFactory가 읽을 Yaml 파일을 등록한다.
        yamlFactory.setResources(seedResource);
        // yamlFactory가 Yaml 파일 내용을 읽고, 자바의 Map 구조로 변환한다.
        Map<String, Object> yaml = yamlFactory.getObject();

        // Yaml 파일 안에 concepts라는 루트 목록이 있는지를 검사한다.
        if (yaml == null || !(yaml.get("concepts") instanceof List<?> concepts)) {
            throw new IllegalStateException("주식 개념 YAML의 concepts 목록이 필요합니다.");
        }

        // Yaml 파일에 저장된 각 개념을 읽어서 최종적으로 리스트에 담는다.
        List<StockConceptSeedDefinition> definitions = new ArrayList<>();
        // 개념카드 enum 목록을 담은 set -> 동일한 개념카드를 중복으로 읽는 것을 방지한다.
        Set<StockConceptCode> loadedCodes = EnumSet.noneOf(StockConceptCode.class);
        for (Object concept : concepts) { // contexts 목록에서 각 concept를 하나씩 꺼낸다.
            if (!(concept instanceof Map<?, ?> values)) {
                throw new IllegalStateException("주식 개념 YAML의 각 항목은 객체 형식이어야 합니다.");
            }

            // 개념 카드 code를 꺼낸다.
            StockConceptCode conceptCode = parseConceptCode(requiredText(values, "conceptCode"));
            if (!loadedCodes.add(conceptCode)) {
                throw new IllegalStateException("중복된 주식 개념 코드입니다: " + conceptCode);
            }

            // StockConceptSeedDefinition 레코드에 개념정보를 담는다.
            definitions.add(new StockConceptSeedDefinition(
                    conceptCode,
                    requiredText(values, "title"),
                    requiredText(values, "summary"),
                    requiredText(values, "detailedExplanation"),
                    requiredText(values, "bakeryExample"),
                    optionalText(values, "marketImpact"),
                    requiredText(values, "caution")
            ));
        }

        Set<StockConceptCode> missingCodes = EnumSet.allOf(StockConceptCode.class);
        missingCodes.removeAll(loadedCodes);
        if (!missingCodes.isEmpty()) {
            throw new IllegalStateException("YAML에 누락된 주식 개념 코드가 있습니다: " + missingCodes);
        }
        return List.copyOf(definitions);
    }

    private StockConceptCode parseConceptCode(String value) {
        try {
            return StockConceptCode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("지원하지 않는 주식 개념 코드입니다: " + value, e);
        }
    }

    // Map에서 특정 키를 꺼낸다. 즉 Ymal에 title: "PER: 이익에 비해 주가가 몇 배인가" 와 같이 Map 형태로 저장된 데이터를 꺼낸다.
    private String requiredText(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("주식 개념 YAML의 " + key + " 값은 필수입니다.");
        }
        return text.strip();
    }

    private String optionalText(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("주식 개념 YAML의 " + key + " 값은 문자열이어야 합니다.");
        }
        return text.strip();
    }
}
