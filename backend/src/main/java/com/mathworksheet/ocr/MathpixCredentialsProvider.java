package com.mathworksheet.ocr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathworksheet.config.AppPaths;
import com.mathworksheet.config.AppProperties;

/**
 * 키 제공자(10장 "키 우선순위"). 코드는 키를 직접 읽지 않고 이 클래스를 거친다.
 * <ol>
 *   <li>설정 화면에 저장된 값(%APPDATA%\MathWorksheet\config\settings.json)</li>
 *   <li>application-local.yml의 app.mathpix.*</li>
 *   <li>환경 변수 MATHPIX_APP_ID / MATHPIX_APP_KEY</li>
 * </ol>
 */
@Component
public class MathpixCredentialsProvider {

    private final Path settingsFile;
    private final AppProperties.Mathpix localConfig;
    private final Function<String, String> env;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MathpixCredentialsProvider(AppPaths paths, AppProperties properties) {
        this(paths.settingsFile(), properties.mathpix(), System::getenv);
    }

    MathpixCredentialsProvider(Path settingsFile, AppProperties.Mathpix localConfig, Function<String, String> env) {
        this.settingsFile = settingsFile;
        this.localConfig = localConfig;
        this.env = env;
    }

    /**
     * Spring 없이 쓰는 개발용 CLI용. 앱과 같은 우선순위로 읽는다.
     *
     * @param localYml 개발자 키 파일(backend/application-local.yml). 없어도 된다
     */
    public static MathpixCredentialsProvider forCli(Path settingsFile, Path localYml) {
        AppProperties.Mathpix local = null;
        if (Files.exists(localYml)) {
            try (var in = Files.newInputStream(localYml)) {
                Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(in);
                Map<?, ?> mathpix = (Map<?, ?>) ((Map<?, ?>) root.getOrDefault("app", Map.of())).get("mathpix");
                if (mathpix != null) {
                    local = new AppProperties.Mathpix(string(mathpix.get("app-id")), string(mathpix.get("app-key")));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("키 파일을 읽을 수 없습니다: " + localYml.getFileName(), e);
            }
        }
        return new MathpixCredentialsProvider(settingsFile, local, System::getenv);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    public Optional<MathpixCredentials> get() {
        return fromSettingsFile()
                .or(this::fromLocalConfig)
                .or(this::fromEnvironment);
    }

    private Optional<MathpixCredentials> fromSettingsFile() {
        if (!Files.exists(settingsFile)) {
            return Optional.empty();
        }
        try {
            JsonNode mathpix = objectMapper.readTree(settingsFile.toFile()).path("mathpix");
            return complete(new MathpixCredentials(text(mathpix, "appId"), text(mathpix, "appKey")));
        } catch (IOException e) {
            throw new UncheckedIOException("설정 파일을 읽을 수 없습니다: " + settingsFile, e);
        }
    }

    private Optional<MathpixCredentials> fromLocalConfig() {
        if (localConfig == null) {
            return Optional.empty();
        }
        return complete(new MathpixCredentials(localConfig.appId(), localConfig.appKey()));
    }

    private Optional<MathpixCredentials> fromEnvironment() {
        return complete(new MathpixCredentials(env.apply("MATHPIX_APP_ID"), env.apply("MATHPIX_APP_KEY")));
    }

    private static Optional<MathpixCredentials> complete(MathpixCredentials credentials) {
        return credentials.isComplete() ? Optional.of(credentials) : Optional.empty();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
