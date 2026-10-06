package com.mathworksheet.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 앱 설정. 런타임 데이터는 설치 폴더가 아닌 %APPDATA%\MathWorksheet 아래에 둔다(10장).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Path dataDir, Mathpix mathpix, Pandoc pandoc) {

    /** 개발용 키. application-local.yml로만 채운다. 학원 키는 설정 화면에 넣는다. */
    public record Mathpix(String appId, String appKey) {
    }

    /**
     * @param path    개발 PC는 PATH의 "pandoc", 배포본은 설치 폴더의 pandoc.exe 경로
     * @param timeout 문제지 한 장 변환 제한 시간
     */
    public record Pandoc(String path, Duration timeout) {
    }
}
