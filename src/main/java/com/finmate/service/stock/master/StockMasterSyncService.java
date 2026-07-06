package com.finmate.service.stock.master;

import com.finmate.domain.stock.dto.master.DomesticStockMasterDto;
import com.finmate.domain.stock.dto.master.OverseasStockMasterDto;
import com.finmate.infra.kis.stock.master.KisStockMasterFileClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMasterSyncService {
    private final KisStockMasterFileClient kisStockMasterFileClient;
    private final StockMasterParser stockMasterParser;
    private final StockMasterApplyService stockMasterApplyService;

    // 국내 장 시작 전에 KOSPI/KOSDAQ 마스터파일을 동기화한다.
    @Scheduled(
            cron = "${finmate.stock-master.domestic-sync-cron:0 0 8 * * MON-FRI}",
            zone = "${finmate.stock-master.domestic-sync-zone:Asia/Seoul}"
    )
    public void syncDailyDomesticStockMasters() {
        syncSources(
                "국내",
                List.of(
                        StockMasterFileSource.KOSPI,
                        StockMasterFileSource.KOSDAQ));
    }

    // 나스닥 장 시작 전에 NASDAQ 마스터파일을 동기화한다. 미국 서머타임은 America/New_York zone에서 처리한다.
    @Scheduled(
            cron = "${finmate.stock-master.nasdaq-sync-cron:0 0 8 * * MON-FRI}",
            zone = "${finmate.stock-master.nasdaq-sync-zone:America/New_York}"
    )
    public void syncDailyNasdaqStockMaster() {
        syncSources("나스닥", List.of(StockMasterFileSource.NASDAQ));
    }

    private void syncSources(String groupName, List<StockMasterFileSource> sources) {
        log.info("{} 종목 마스터 동기화를 시작합니다.", groupName);
        for (StockMasterFileSource source : sources) {
            try {
                sync(source);
            } catch (Exception e) {
                log.error("종목 마스터 동기화에 실패했습니다. market={}", source.getMarketType(), e);
            }
        }
        log.info("{} 종목 마스터 동기화를 종료합니다.", groupName);
    }

    public void sync(StockMasterFileSource source) {
        // 마스터 임시 디렉터리 생성 + 종목 업데이트 시간 설정
        Path workingDirectory = createWorkingDirectory(source);
        LocalDateTime syncedAt = LocalDateTime.now();

        try {
            // 마스터 파일 다운로드 및 압축 해제 (kosdaq_code.mst)
            Path masterFile = kisStockMasterFileClient.downloadAndExtract(source, workingDirectory);
            // 국내주식인지 or 해외주식인지에 따라 다른 parsing 적용(파일을 파싱하여 row단위로 dto에 데이터를 담고, 서비스레이어에서 실제 엔티티 업데이트)
            if (source.isDomestic()) {
                List<DomesticStockMasterDto> rows = stockMasterParser.parseDomestic(masterFile, source.getMarketType());
                stockMasterApplyService.applyDomestic(source.getMarketType(), rows, syncedAt);
                log.info("국내 종목 마스터 동기화 완료. market={}, count={}", source.getMarketType(), rows.size());
            } else {
                List<OverseasStockMasterDto> rows = stockMasterParser.parseOverseas(masterFile, source.getMarketType());
                stockMasterApplyService.applyOverseas(source.getMarketType(), rows, syncedAt);
                log.info("해외 종목 마스터 동기화 완료. market={}, count={}", source.getMarketType(), rows.size());
            }
        } finally {
            // 국내 + 해외 종목 마스터 동기화가 완료되면 임시 파일 / 디렉터리 모두 삭제
            deleteWorkingDirectory(workingDirectory);
        }
    }
    // 마스터 임시 디렉터리 생성
    private Path createWorkingDirectory(StockMasterFileSource source) {
        try {
            return Files.createTempDirectory("finmate-stock-master-" + source.name().toLowerCase() + "-");
        } catch (IOException e) {
            throw new RuntimeException("종목 마스터 임시 디렉터리 생성에 실패했습니다.", e);
        }
    }

    // 마스터 임시 디렉터리 제거
    private void deleteWorkingDirectory(Path workingDirectory) {
        if (workingDirectory == null || !Files.exists(workingDirectory)) {
            return;
        }

        try {
            Files.walk(workingDirectory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("종목 마스터 임시 디렉터리 삭제에 실패했습니다. path={}", workingDirectory, e);
        }
    }

    // 마스터 임시파일들 제거
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("종목 마스터 임시 파일 삭제에 실패했습니다. path={}", path, e);
        }
    }
}
