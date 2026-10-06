package com.mathworksheet.worksheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.mathworksheet.config.AppPaths;
import com.mathworksheet.config.AppProperties;
import com.mathworksheet.worksheet.docx.PlaceholderFiller;
import com.mathworksheet.worksheet.docx.TemplateInspector;
import com.mathworksheet.worksheet.pandoc.PandocRunner;
import com.mathworksheet.worksheet.verify.RoundtripVerifier;

/**
 * Word 생성 부품을 Spring 빈으로 묶는다. 부품 자체는 Spring에 의존하지 않아 CLI·단위 테스트에서도 그대로 쓴다.
 */
@Configuration
public class WorksheetConfig {

    static final String LUA_FILTER_RESOURCE = "worksheet/filters/worksheet.lua";

    @Bean
    PandocRunner pandocRunner(AppProperties properties) {
        AppProperties.Pandoc pandoc = properties.pandoc();
        return new PandocRunner(pandoc.path(), pandoc.timeout());
    }

    @Bean
    PlaceholderFiller placeholderFiller() {
        return new PlaceholderFiller();
    }

    @Bean
    TemplateInspector templateInspector() {
        return new TemplateInspector();
    }

    @Bean
    RoundtripVerifier roundtripVerifier(PandocRunner pandocRunner) {
        return new RoundtripVerifier(pandocRunner);
    }

    /** Lua 필터는 jar 안에 들어 있어 pandoc이 읽을 수 있도록 작업 폴더에 꺼내 둔다 */
    @Bean
    WorksheetRenderer worksheetRenderer(PandocRunner pandocRunner, PlaceholderFiller filler, AppPaths paths) {
        Path filter = paths.work().resolve("filters").resolve("worksheet.lua");
        try (InputStream in = new ClassPathResource(LUA_FILTER_RESOURCE).getInputStream()) {
            Files.createDirectories(filter.getParent());
            Files.copy(in, filter, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 필터를 준비할 수 없습니다: " + LUA_FILTER_RESOURCE, e);
        }
        return new WorksheetRenderer(pandocRunner, filler, filter);
    }
}
