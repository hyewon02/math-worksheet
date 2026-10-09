package com.mathworksheet.ocr.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mathworksheet.ocr.MathpixCredentials;
import com.mathworksheet.ocr.MathpixCredentialsProvider;
import com.mathworksheet.ocr.MathpixOcrEngine;
import com.mathworksheet.ocr.OcrException;
import com.mathworksheet.ocr.OcrResponse;
import com.mathworksheet.ocr.OcrRetry;
import com.mathworksheet.split.ProblemSplitter;
import com.mathworksheet.split.SplitEvaluation;

/**
 * 1단계(OCR·분할 검증)용 개발 도구. 스킬 스크립트(mathpix-ocr, split-eval)가 부른다.
 * <pre>
 * check  --image 사진 --out 폴더                 키 확인 + 사진 1장 호출
 * batch  --input 폴더 --out 폴더 --limit N       폴더의 사진을 최대 N장 호출(기본 1)
 * split  --raw 폴더 --out split.json [--labels 폴더]   원문으로 문제 나누기, 라벨이 있으면 정확도
 * </pre>
 * 같은 사진은 다시 호출하지 않는다: 사진 지문(SHA-256)별 응답을 {@code output/ocr-eval/cache/}에 두고 재사용한다.
 * 키는 화면에 ••••1234로만 보인다. 마지막 줄에 exit=N(0 정상, 1 검사 불일치, 2 오류).
 */
public final class OcrCli {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private OcrCli() {
    }

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (OcrException e) {
            System.out.println("OCR 실패: " + e.getMessage() + (e.isRetryable() ? " (다시 시도 가능)" : ""));
            code = 2;
        } catch (RuntimeException e) {
            System.out.println("오류: " + e.getMessage());
            code = 2;
        }
        System.out.println("exit=" + code);
        System.exit(code);
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.out.println("명령: check | batch | split");
            return 2;
        }
        Map<String, String> o = parse(args);
        return switch (args[0]) {
            case "check" -> check(o);
            case "batch" -> batch(o);
            case "split" -> split(o);
            default -> {
                System.out.println("알 수 없는 명령: " + args[0]);
                yield 2;
            }
        };
    }

    // ---------- OCR ----------

    private static int check(Map<String, String> o) {
        MathpixCredentialsProvider provider = provider(o);
        MathpixCredentials key = provider.get().orElseThrow(() ->
                new IllegalStateException("Mathpix 키를 찾을 수 없습니다. backend/application-local.yml을 확인해 주세요."));
        System.out.println("키: app_id=" + key.appId() + ", app_key=" + key.maskedKey());
        Path image = Path.of(require(o, "image"));
        Path out = Path.of(require(o, "out"));
        Result r = ocr(engine(provider), image, out.resolve("raw"), cacheDir(o));
        System.out.println(r.summary());
        return 0;
    }

    private static int batch(Map<String, String> o) {
        MathpixCredentialsProvider provider = provider(o);
        int limit = Integer.parseInt(o.getOrDefault("limit", "1"));
        Path out = Path.of(require(o, "out"));
        List<Path> images = images(Path.of(require(o, "input")));
        MathpixOcrEngine engine = engine(provider);
        int calls = 0;
        int done = 0;
        for (Path image : images) {
            boolean cached = Files.exists(cacheDir(o).resolve(sha256(image) + ".json"));
            if (!cached && calls >= limit) {
                System.out.println("한도 " + limit + "장에 도달해 멈춤. 남은 사진: " + (images.size() - done));
                break;
            }
            Result r = ocr(engine, image, out.resolve("raw"), cacheDir(o));
            System.out.println(r.summary());
            if (!r.cached()) {
                calls++;
            }
            done++;
        }
        System.out.println("Mathpix 호출 " + calls + "회, 처리 " + done + "/" + images.size() + "장");
        return 0;
    }

    record Result(String image, boolean cached, int lines, int handwrittenLines, int textLength) {
        String summary() {
            return (cached ? "[저장본] " : "[호출] ") + image + ": 줄 " + lines + "개(손글씨 " + handwrittenLines
                    + "), 글자 " + textLength;
        }
    }

    /** 응답 원문을 그대로 raw/{사진 이름}.json에 저장한다. 같은 사진은 저장본을 쓴다 */
    private static Result ocr(MathpixOcrEngine engine, Path image, Path rawDir, Path cache) {
        try {
            Files.createDirectories(rawDir);
            Files.createDirectories(cache);
            Path cached = cache.resolve(sha256(image) + ".json");
            boolean fromCache = Files.exists(cached);
            String raw;
            if (fromCache) {
                raw = Files.readString(cached, StandardCharsets.UTF_8);
            } else {
                OcrResponse response = OcrRetry.recognize(engine, image, d -> Thread.sleep(d.toMillis()));
                raw = response.rawJson();
                Files.writeString(cached, raw, StandardCharsets.UTF_8);
            }
            Files.writeString(rawDir.resolve(stem(image) + ".json"), raw, StandardCharsets.UTF_8);
            JsonNode root = JSON.readTree(raw);
            int handwritten = 0;
            for (JsonNode line : root.path("line_data")) {
                if (line.path("is_handwritten").asBoolean(false)) {
                    handwritten++;
                }
            }
            return new Result(image.getFileName().toString(), fromCache, root.path("line_data").size(), handwritten,
                    root.path("text").asText("").length());
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓸 수 없습니다", e);
        }
    }

    // ---------- 분할 ----------

    private static int split(Map<String, String> o) {
        Path rawDir = Path.of(require(o, "raw"));
        ProblemSplitter splitter = new ProblemSplitter();
        Map<String, ProblemSplitter.Split> results = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(rawDir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                results.put(stem(f), splitter.split(JSON.readTree(f.toFile())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("원문을 읽을 수 없습니다: " + rawDir, e);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("splits", results);
        int code = 0;
        if (o.containsKey("labels")) {
            SplitEvaluation.Report eval = SplitEvaluation.evaluate(results, Path.of(o.get("labels")));
            report.put("evaluation", eval);
            System.out.println(eval.summary());
            code = eval.allPagesCorrect() ? 0 : 1;
        }
        results.forEach((name, s) -> System.out.println(name + ": " + s.numbers()));
        write(Path.of(require(o, "out")), report);
        return code;
    }

    // ---------- 도우미 ----------

    private static MathpixCredentialsProvider provider(Map<String, String> o) {
        String appData = System.getenv().getOrDefault("APPDATA", System.getProperty("user.home"));
        return MathpixCredentialsProvider.forCli(
                Path.of(appData, "MathWorksheet", "config", "settings.json"),
                Path.of(o.getOrDefault("local-config", "application-local.yml")));
    }

    private static MathpixOcrEngine engine(MathpixCredentialsProvider provider) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new MathpixOcrEngine(provider::get, http, MathpixOcrEngine.ENDPOINT, Duration.ofSeconds(60));
    }

    private static Path cacheDir(Map<String, String> o) {
        return Path.of(o.getOrDefault("cache", "../output/ocr-eval/cache"));
    }

    private static List<Path> images(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().toLowerCase().matches(".*\\.(jpe?g|png)$")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("사진 폴더를 읽을 수 없습니다: " + dir, e);
        }
    }

    static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stem(Path file) {
        return file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
    }

    private static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            JSON.writeValue(file.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 쓸 수 없습니다: " + file, e);
        }
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("옵션은 --이름 값 형식입니다: " + args[i]);
            }
            String key = args[i].substring(2);
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            options.put(key, hasValue ? args[++i] : "true");
        }
        return options;
    }

    private static String require(Map<String, String> o, String key) {
        String value = o.get(key);
        if (value == null) {
            throw new IllegalArgumentException("--" + key + " 옵션이 필요합니다");
        }
        return value;
    }
}
