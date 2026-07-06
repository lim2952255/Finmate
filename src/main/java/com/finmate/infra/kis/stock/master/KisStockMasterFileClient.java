package com.finmate.infra.kis.stock.master;

import com.finmate.service.stock.master.StockMasterFileSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// 한국투자증권 url을 통해 마스터 zip파일을 임시 디렉터리에 저장하고, 압축해제하는 역할
@Component
public class KisStockMasterFileClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 한국투자증권에서 마스터파일을 다운로드명
    // source에는 코스피 / 코스닥/ 나스닥의 마스터파일 경로와 마스터파일명이 저장되어 있다.
    public Path downloadAndExtract(StockMasterFileSource source, Path workingDirectory) {
        try {
            // 임시 디렉터리 생성
            Files.createDirectories(workingDirectory);
            // zip file 저장 경로 생성
            Path zipPath = workingDirectory.resolve(source.name().toLowerCase() + ".zip");
            download(source.getZipUrl(), zipPath); // zip file을 다운로드한 다음, zip file 저장경로에 저장한다.
            extract(zipPath, workingDirectory); // zip file을 압축 해제

            // 압축해제한 파일명을 찾고, 없으면 오류 출력. 있으면 압축해제한 파일명 리턴 (kosdaq_code.mst)
            Path extractedFile = workingDirectory.resolve(source.getExtractedFileName());
            if (!Files.exists(extractedFile)) {
                throw new RuntimeException("종목 마스터 파일을 찾을 수 없습니다: " + extractedFile);
            }

            return extractedFile;
        } catch (IOException | InterruptedException e) {
            // IOException: 파일 생성 / 다운로드 저장 / 압축 해제중에 발생 가능
            // InterruptedException: 다운로드 과정에서 스레드가 중단되는 경우
            if (e instanceof InterruptedException) {
                // 스레드가 중단되었는데 catch를 하면, 스레드의 인터럽트 상태정보가 지워지기 때문에, 다시 스레드에 interrupt정보를 추가한다.
                Thread.currentThread().interrupt();
            }

            throw new RuntimeException("종목 마스터 파일 다운로드에 실패했습니다: " + source.name(), e);
        }
    }

    // 한국투자증권 URL에서 ZIP 파일을 다운로드해서 targetPath에 저장
    private void download(String url, Path targetPath) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        // http 요청을 보낸 뒤, 응답 본문을 메모리에 담지 않고, targetPath에 저장
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetPath));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("종목 마스터 파일 다운로드 응답이 실패했습니다. status=" + response.statusCode());
        }

        if (Files.size(targetPath) == 0) { // targetPath에 저장된 정보가 없으면 오류 출력
            throw new RuntimeException("다운로드된 종목 마스터 파일이 비어 있습니다: " + targetPath);
        }
    }

    // 저장한 마스터 zip file을 압축 해제
    private void extract(Path zipPath, Path targetDirectory) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            // zip file을 읽을 수 있는 스트림 생성
            ZipEntry entry;
            // zip 내부 파일들을 하나씩 순회
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }
                // 압축 해제될 파일경로 만들기
                Path outputPath = targetDirectory.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDirectory)) {
                    throw new RuntimeException("안전하지 않은 ZIP 엔트리입니다: " + entry.getName());
                }
                // zip 파일 데이터를 outputPath에 저장
                Files.copy(zipInputStream, outputPath);
                zipInputStream.closeEntry();
            }
        }
    }
}
