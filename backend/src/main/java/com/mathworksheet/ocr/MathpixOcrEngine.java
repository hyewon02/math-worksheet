package com.mathworksheet.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mathpix v3/text 호출(설계서 5장 2단계). 사진 1장 = 호출 1번이며, 응답은 손대지 않고 원문 그대로 돌려준다.
 * <p>
 * 주의: Mathpix는 대부분의 오류를 <b>HTTP 200</b>에 {@code error} 칸으로 돌려준다. 상태 코드만 보면 실패를 놓친다.
 * 키는 {@link MathpixCredentialsProvider}로만 얻고, 예외 메시지·로그에 넣지 않는다(CLAUDE.md 절대 원칙 3).
 */
public class MathpixOcrEngine implements OcrEngine {

    public static final URI ENDPOINT = URI.create("https://api.mathpix.com/v3/text");

    /**
     * 요청 옵션. 수식 구분자는 원고 형식({@code \(...\)}, {@code \[...\]})과 같게 명시한다.
     * improve_mathpix=false: 문제집 사진을 Mathpix가 보관·학습에 쓰지 않게 한다(키 설정에서 꺼 두었어도 요청마다 다시 끈다).
     */
    static final Map<String, Object> OPTIONS = Map.of(
            "formats", List.of("text"),
            "include_line_data", true,
            "math_inline_delimiters", List.of("\\(", "\\)"),
            "math_display_delimiters", List.of("\\[", "\\]"),
            "metadata", Map.of("improve_mathpix", false));

    /** 잠깐 뒤 다시 하면 될 수 있는 오류(Mathpix 문서 "Error Handling") */
    private static final Set<String> RETRYABLE_ERRORS = Set.of("sys_exception", "connection_closed");

    private final Supplier<Optional<MathpixCredentials>> credentials;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    public MathpixOcrEngine(Supplier<Optional<MathpixCredentials>> credentials, HttpClient http, URI endpoint,
                            Duration timeout) {
        this.credentials = credentials;
        this.http = http;
        this.endpoint = endpoint;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "mathpix";
    }

    @Override
    public OcrResponse recognize(Path image) {
        MathpixCredentials key = credentials.get().orElseThrow(() ->
                new OcrException("Mathpix 키가 설정되지 않았습니다. 설정 화면에서 키를 입력해 주세요.", false, null));

        String boundary = "----mathworksheet-" + UUID.randomUUID();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("app_id", key.appId())
                    .header("app_key", key.appKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, image)))
                    .build();
        } catch (IOException e) {
            throw new OcrException("사진을 읽을 수 없습니다: " + image.getFileName(), false, e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OcrException("Mathpix에 연결할 수 없습니다. 인터넷 연결을 확인해 주세요.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("OCR 요청이 중단되었습니다.", true, e);
        }
        return parse(response.statusCode(), response.body());
    }

    OcrResponse parse(int status, String body) {
        if (status == 401 || status == 403) {
            throw new OcrException("Mathpix 키가 맞지 않거나 사용이 중지되었습니다. 설정 화면에서 키를 확인해 주세요.", false, null);
        }
        if (status == 429) {
            throw new OcrException("Mathpix 사용 한도를 넘었습니다. 잠시 뒤 다시 시도하거나 콘솔에서 한도를 확인해 주세요.", true, null);
        }
        if (status >= 500) {
            throw new OcrException("Mathpix 서버 오류(" + status + ")입니다. 잠시 뒤 다시 시도합니다.", true, null);
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JsonProcessingException e) {
            throw new OcrException("Mathpix 응답을 읽을 수 없습니다(HTTP " + status + ").", status != 200, e);
        }
        if (root.hasNonNull("error")) {
            String id = root.path("error_info").path("id").asText("");
            throw new OcrException("Mathpix 오류: " + root.get("error").asText() + (id.isEmpty() ? "" : " (" + id + ")"),
                    RETRYABLE_ERRORS.contains(id), null);
        }
        if (status != 200) {
            throw new OcrException("Mathpix 응답 오류(HTTP " + status + ")", false, null);
        }
        JsonNode lines = root.get("line_data");
        return new OcrResponse(body, root.path("text").asText(""), lines == null ? null : lines.toString());
    }

    private byte[] multipart(String boundary, Path image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String fileName = image.getFileName().toString();
        write(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + asciiName(fileName) + "\"\r\n"
                + "Content-Type: " + contentType(fileName) + "\r\n\r\n");
        out.write(Files.readAllBytes(image));
        write(out, "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"options_json\"\r\n\r\n"
                + json.writeValueAsString(OPTIONS)
                + "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    /** 파일 이름은 Mathpix가 쓰지 않는다. 한글 이름이 헤더를 깨지 않게 확장자만 남긴다 */
    private static String asciiName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return "image" + (dot < 0 ? "" : fileName.substring(dot).toLowerCase());
    }

    private static String contentType(String fileName) {
        return fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
