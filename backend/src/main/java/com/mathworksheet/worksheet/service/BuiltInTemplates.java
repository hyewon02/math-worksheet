package com.mathworksheet.worksheet.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathworksheet.config.AppPaths;
import com.mathworksheet.worksheet.FormatSettings;
import com.mathworksheet.worksheet.domain.Template;
import com.mathworksheet.worksheet.repository.TemplateRepository;

/**
 * 내장 형식(설계서 8장 "내장 형식")을 앱이 켜질 때마다 형식 폴더에 꺼내고 DB에 등록한다.
 * 앱 업데이트로 내장 형식이 바뀌면 파일과 설정이 새 것으로 바뀐다. 선생님이 등록한 형식은 건드리지 않는다.
 */
@Component
@Order(0)
public class BuiltInTemplates implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuiltInTemplates.class);

    /** classpath worksheet/templates/ 아래 파일 이름(확장자 제외) */
    static final List<String> BUILT_IN = List.of("naesin-2col");

    private final TemplateRepository templates;
    private final AppPaths paths;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BuiltInTemplates(TemplateRepository templates, AppPaths paths) {
        this.templates = templates;
        this.paths = paths;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : BUILT_IN) {
            register(name);
        }
    }

    private void register(String name) {
        String fileName = name + ".docx";
        try (InputStream docx = new ClassPathResource("worksheet/templates/" + fileName).getInputStream();
             InputStream json = new ClassPathResource("worksheet/templates/" + name + ".json").getInputStream()) {
            Path target = paths.templates().resolve(fileName);
            Files.copy(docx, target, StandardCopyOption.REPLACE_EXISTING);
            FormatSettings settings = objectMapper.readValue(json, FormatSettings.class);
            templates.findByFilePathAndBuiltInTrue(fileName).ifPresentOrElse(
                    t -> t.apply(settings),
                    () -> templates.save(Template.builtIn(fileName, settings)));
            log.info("내장 형식 준비: {}", settings.name());
        } catch (IOException e) {
            throw new UncheckedIOException("내장 형식을 준비할 수 없습니다: " + name, e);
        }
    }
}
