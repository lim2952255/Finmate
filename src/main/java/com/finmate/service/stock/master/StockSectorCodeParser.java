package com.finmate.service.stock.master;

import com.finmate.domain.stock.dto.master.DomesticStockSectorCodeDto;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.finmate.infra.kis.parser.KisValueParser.requiredCode;
import static com.finmate.infra.kis.parser.KisValueParser.requiredText;

// 귝내 업종코드파일에서 데이터를 파싱하는 컴포넌트
@Component
public class StockSectorCodeParser {
    private static final Charset STOCK_MASTER_CHARSET = Charset.forName("CP949");
    private static final int DOMESTIC_SECTOR_CODE_START = 1;
    private static final int DOMESTIC_SECTOR_CODE_END = 5;

    public List<DomesticStockSectorCodeDto> parseDomestic(Path sectorCodeFile) {
        List<DomesticStockSectorCodeDto> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(sectorCodeFile, STOCK_MASTER_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (isBlankDomesticSectorName(line)) {
                    continue;
                }

                rows.add(parseDomesticLine(line));
            }
        } catch (IOException e) {
            throw new RuntimeException("국내 업종코드 파일 파싱에 실패했습니다: " + sectorCodeFile, e);
        }

        return rows;
    }

    private boolean isBlankDomesticSectorName(String line) {
        return line.length() > DOMESTIC_SECTOR_CODE_END
                && line.substring(DOMESTIC_SECTOR_CODE_END).isBlank();
    }

    DomesticStockSectorCodeDto parseDomesticLine(String line) {
        if (line.length() <= DOMESTIC_SECTOR_CODE_END) {
            throw new RuntimeException("국내 업종코드 파일 라인 길이가 올바르지 않습니다. length=" + line.length());
        }

        String code = requiredCode(
                line.substring(DOMESTIC_SECTOR_CODE_START, DOMESTIC_SECTOR_CODE_END),
                "국내 업종코드는 필수입니다.");
        String nameKo = requiredText(
                line.substring(DOMESTIC_SECTOR_CODE_END),
                "국내 업종명은 필수입니다.");

        return new DomesticStockSectorCodeDto(code, nameKo);
    }
}
