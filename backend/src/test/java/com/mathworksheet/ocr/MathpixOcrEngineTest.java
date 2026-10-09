package com.mathworksheet.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * 진짜 Mathpix 대신 이 테스트 안에서 띄운 가짜 서버를 부른다. 테스트에는 실제 키도 실제 호출도 없다.
 */
class MathpixOcrEngineTest {

    @TempDir
    Path dir;

    HttpServer server;
    final List<String> bodies = new ArrayList<>();
    final List<String> appKeys = new ArrayList<>();
    volatile int status = 200;
    volatile String response = "{\"text\":\"함수 \\\\(f(x)\\\\)\",\"line_data\":[{\"id\":\"1\",\"type\":\"text\",\"text\":\"10. 함수\",\"is_handwritten\":false}]}";

    final MathpixCredentials key = new MathpixCredentials("test-id", "test-key-0000");
    Path image;

    @BeforeEach
    void setUp() throws IOException {
        image = Files.write(dir.resolve("쪽사진.jpg"), new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/text", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            appKeys.add(exchange.getRequestHeaders().getFirst("app_key"));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    MathpixOcrEngine engine(Optional<MathpixCredentials> credentials) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/text");
        return new MathpixOcrEngine(() -> credentials, HttpClient.newHttpClient(), endpoint, Duration.ofSeconds(5));
    }

    @Test
    void 사진과_옵션을_보내고_응답을_원문_그대로_돌려준다() {
        OcrResponse r = engine(Optional.of(key)).recognize(image);

        assertThat(r.rawJson()).isEqualTo(response);
        assertThat(r.text()).isEqualTo("함수 \\(f(x)\\)");
        assertThat(r.lineData()).contains("10. 함수");
        assertThat(appKeys).containsExactly("test-key-0000");
    }

    @Test
    void 사진을_Mathpix가_보관하지_않도록_요청마다_끈다() {
        engine(Optional.of(key)).recognize(image);

        assertThat(bodies.get(0))
                .contains("name=\"options_json\"")
                .contains("\"improve_mathpix\":false")
                .contains("\"include_line_data\":true")
                .contains("name=\"file\"; filename=\"image.jpg\"");
    }

    @Test
    void 키가_없으면_호출하지_않는다() {
        assertThatThrownBy(() -> engine(Optional.empty()).recognize(image))
                .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.isRetryable()).isFalse())
                .hasMessageContaining("키가 설정되지 않았습니다");
        assertThat(bodies).isEmpty();
    }

    @Test
    void 키가_틀리면_재시도하지_않는_오류다() {
        status = 401;
        response = "{\"error\":\"Invalid credentials\",\"error_info\":{\"id\":\"http_unauthorized\"}}";

        assertThatThrownBy(() -> engine(Optional.of(key)).recognize(image))
                .isInstanceOfSatisfying(OcrException.class, e -> {
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).doesNotContain("test-key");
                });
    }

    @Test
    void HTTP_200이어도_error_칸이_있으면_실패다() {
        response = "{\"error\":\"No content found\",\"error_info\":{\"id\":\"image_no_content\"}}";

        assertThatThrownBy(() -> engine(Optional.of(key)).recognize(image))
                .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.isRetryable()).isFalse())
                .hasMessageContaining("image_no_content");
    }

    @Test
    void 일시적인_서버_오류는_재시도할_수_있다() {
        response = "{\"error\":\"Internal error\",\"error_info\":{\"id\":\"sys_exception\"}}";

        assertThatThrownBy(() -> engine(Optional.of(key)).recognize(image))
                .isInstanceOfSatisfying(OcrException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void 재시도는_세_번까지만_한다() {
        response = "{\"error\":\"Internal error\",\"error_info\":{\"id\":\"sys_exception\"}}";
        AtomicInteger sleeps = new AtomicInteger();

        assertThatThrownBy(() -> OcrRetry.recognize(engine(Optional.of(key)), image, d -> sleeps.incrementAndGet()))
                .isInstanceOf(OcrException.class);
        assertThat(bodies).hasSize(3);
        assertThat(sleeps).hasValue(2);
    }

    @Test
    void 재시도할_수_없는_오류는_한_번만_부른다() {
        status = 401;
        response = "{\"error\":\"Invalid credentials\"}";

        assertThatThrownBy(() -> OcrRetry.recognize(engine(Optional.of(key)), image, d -> { }))
                .isInstanceOf(OcrException.class);
        assertThat(bodies).hasSize(1);
    }
}
