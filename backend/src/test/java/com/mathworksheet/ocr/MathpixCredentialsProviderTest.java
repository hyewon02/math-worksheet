package com.mathworksheet.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mathworksheet.config.AppProperties;

class MathpixCredentialsProviderTest {

    @TempDir
    Path dir;

    final Map<String, String> env = Map.of("MATHPIX_APP_ID", "env-id", "MATHPIX_APP_KEY", "env-key-9999");

    @Test
    void 설정_파일이_가장_우선한다() throws Exception {
        Path settings = dir.resolve("settings.json");
        Files.writeString(settings, "{\"mathpix\":{\"appId\":\"file-id\",\"appKey\":\"file-key-1234\"}}");
        var provider = new MathpixCredentialsProvider(settings, new AppProperties.Mathpix("local-id", "local-key"), env::get);

        assertThat(provider.get()).hasValueSatisfying(c -> assertThat(c.appId()).isEqualTo("file-id"));
    }

    @Test
    void 설정_파일이_없으면_local_설정_다음_환경_변수() {
        Path missing = dir.resolve("none.json");

        var withLocal = new MathpixCredentialsProvider(missing, new AppProperties.Mathpix("local-id", "local-key"), env::get);
        assertThat(withLocal.get()).hasValueSatisfying(c -> assertThat(c.appId()).isEqualTo("local-id"));

        var envOnly = new MathpixCredentialsProvider(missing, new AppProperties.Mathpix("", null), env::get);
        assertThat(envOnly.get()).hasValueSatisfying(c -> assertThat(c.appId()).isEqualTo("env-id"));
    }

    @Test
    void 키가_없으면_비어_있다() {
        var provider = new MathpixCredentialsProvider(dir.resolve("none.json"), null, key -> null);
        assertThat(provider.get()).isEmpty();
    }

    @Test
    void 키는_가려서_보인다() {
        var credentials = new MathpixCredentials("id", "secret-key-1234");
        assertThat(credentials.maskedKey()).isEqualTo("••••1234");
        assertThat(credentials.toString()).doesNotContain("secret");
    }
}
