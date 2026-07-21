package com.finmate.service.stock.master;

import com.finmate.domain.stock.dto.master.DomesticStockSectorCodeDto;
import com.finmate.domain.stock.industry.DomesticStockSectorCode;
import com.finmate.infra.kis.stock.master.KisStockMasterFileClient;
import com.finmate.repository.stock.industry.DomesticStockSectorCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 국내 업종코드 정보를 업종코드 파일에서 읽어서 DB에 동기화하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSectorCodeSyncService {
    private static final String DOMESTIC_SECTOR_CODE_SOURCE_NAME = "IDXCODE";
    private static final String DOMESTIC_SECTOR_CODE_ZIP_URL =
            "https://new.real.download.dws.co.kr/common/master/idxcode.mst.zip";
    private static final String DOMESTIC_SECTOR_CODE_FILE_NAME = "idxcode.mst";

    private final KisStockMasterFileClient kisStockMasterFileClient; // Zip파일을 저장 및 압축해제를 담당하는 클라이언트
    private final StockSectorCodeParser stockSectorCodeParser;
    private final DomesticStockSectorCodeRepository domesticStockSectorCodeRepository;

    public void syncDomesticSectorCodes() {
        Path workingDirectory = createWorkingDirectory();
        LocalDateTime syncedAt = LocalDateTime.now();

        try {
            Path sectorCodeFile = kisStockMasterFileClient.downloadAndExtract(
                    DOMESTIC_SECTOR_CODE_SOURCE_NAME,
                    DOMESTIC_SECTOR_CODE_ZIP_URL,
                    DOMESTIC_SECTOR_CODE_FILE_NAME,
                    workingDirectory);
            List<DomesticStockSectorCodeDto> rows = stockSectorCodeParser.parseDomestic(sectorCodeFile);
            applyDomestic(rows, syncedAt);
            log.info("국내 업종코드 동기화 완료. count={}", rows.size());
        } finally {
            deleteWorkingDirectory(workingDirectory);
        }
    }

    // 만약 파일에서 읽은 업종코드가 DB에 없었다면 새로 생성, DB에 있었다면 update를 수행한다.
    @Transactional
    public void applyDomestic(List<DomesticStockSectorCodeDto> rows, LocalDateTime syncedAt) {
        Map<String, DomesticStockSectorCode> existingCodes = domesticStockSectorCodeRepository.findAll().stream()
                .collect(Collectors.toMap(DomesticStockSectorCode::getCode, Function.identity()));

        for (DomesticStockSectorCodeDto row : rows) {
            DomesticStockSectorCode sectorCode = existingCodes.get(row.code());
            if (sectorCode == null) {
                domesticStockSectorCodeRepository.save(
                        DomesticStockSectorCode.create(row.code(), row.nameKo(), syncedAt));
            } else {
                sectorCode.updateName(row.nameKo(), syncedAt);
                domesticStockSectorCodeRepository.save(sectorCode);
            }
        }
    }

    private Path createWorkingDirectory() {
        try {
            return Files.createTempDirectory("finmate-stock-sector-code-");
        } catch (IOException e) {
            throw new RuntimeException("국내 업종코드 임시 디렉터리 생성에 실패했습니다.", e);
        }
    }

    private void deleteWorkingDirectory(Path workingDirectory) {
        if (workingDirectory == null || !Files.exists(workingDirectory)) {
            return;
        }

        try {
            Files.walk(workingDirectory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("국내 업종코드 임시 디렉터리 삭제에 실패했습니다. path={}", workingDirectory, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("국내 업종코드 임시 파일 삭제에 실패했습니다. path={}", path, e);
        }
    }
}
