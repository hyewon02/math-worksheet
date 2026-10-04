package com.mathworksheet.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 앱 설정. 런타임 데이터는 설치 폴더가 아닌 %APPDATA%\MathWorksheet 아래에 둔다(10장).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Path dataDir, Mathpix mathpix) {

    /** 개발용 키. application-local.yml로만 채운다. 학원 키는 설정 화면에 넣는다. */
    public record Mathpix(String appId, String appKey) {
    }
}
