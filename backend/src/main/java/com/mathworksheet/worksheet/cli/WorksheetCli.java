package com.mathworksheet.worksheet.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mathworksheet.worksheet.WorksheetRenderer;
import com.mathworksheet.worksheet.docx.PlaceholderFiller;
import com.mathworksheet.worksheet.docx.TemplateInspector;
import com.mathworksheet.worksheet.pandoc.PandocRunner;
import com.mathworksheet.worksheet.verify.RoundtripVerifier;
import com.mathworksheet.worksheet.verify.VerifyReport;

/**
 * 개발용 명령줄 도구. 스킬 스크립트(docx-build, roundtrip-verify, template-lint)가 이 클래스를 불러
 * 앱과 같은 Java 로직을 쓴다. 로직이 스크립트와 Java 두 벌로 갈라지지 않게 하기 위해서다(설계서 15장).
 * <pre>
 * ./gradlew -q docxTool --args="build  --manuscript m.md --template t.docx --filter f.lua --out out.docx [--answers] [--title …]"
 * ./gradlew -q docxTool --args="verify --manuscript m.md --docx out.docx --report verify.json"
 * ./gradlew -q docxTool --args="lint   --template t.docx"
 * </pre>
 * 종료 코드: 0 정상, 1 검사 불일치(역변환 차이·형식 오류·남은 빈칸), 2 사용법·실행 오류.
 */
public final class WorksheetCli {

    private WorksheetCli() {
    }

    /** 마지막 줄에 "exit=N"을 찍는다. Gradle(JavaExec)을 거치면 종료 코드가 전달되지 않아 스크립트가 이 줄을 읽는다. */
    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (RuntimeException e) {
            System.out.println("오류: " + e.getMessage());
            code = 2;
        }
        System.out.println("exit=" + code);
        System.exit(code);
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.err.println("명령: build | verify | lint");
            return 2;
        }
        Map<String, String> options = parse(args);
        PandocRunner pandoc = new PandocRunner(options.getOrDefault("pandoc", "pandoc"), Duration.ofMinutes(2));
        Path workDir = Path.of(options.getOrDefault("work", System.getProperty("java.io.tmpdir"))).resolve("mathworksheet-cli");
        return switch (args[0]) {
            case "build" -> build(options, pandoc, workDir);
            case "verify" -> verify(options, pandoc, workDir);
            case "lint" -> lint(options);
            default -> {
                System.err.println("알 수 없는 명령: " + args[0]);
                yield 2;
            }
        };
    }

    private static int build(Map<String, String> o, PandocRunner pandoc, Path workDir) {
        Path manuscript = Path.of(require(o, "manuscript"));
        Map<String, String> values = new HashMap<>();
        values.put(PlaceholderFiller.ACADEMY, o.getOrDefault("academy", "샘플 수학학원"));
        values.put(PlaceholderFiller.TITLE, o.getOrDefault("title", "샘플 문제지") + (o.containsKey("answers") ? " (정답)" : ""));
        values.put(PlaceholderFiller.DATE, o.getOrDefault("date", LocalDate.now().toString()));
        values.put(PlaceholderFiller.NUMBER, o.getOrDefault("number", "WS-0000"));

        var renderer = new WorksheetRenderer(pandoc, new PlaceholderFiller(), Path.of(require(o, "filter")));
        var result = renderer.render(new WorksheetRenderer.Request(
                manuscript,
                Path.of(o.getOrDefault("resources", manuscript.toAbsolutePath().getParent().toString())),
                Path.of(require(o, "template")),
                o.containsKey("answers"),
                values,
                Path.of(require(o, "out"))), workDir);

        System.out.println("생성: " + result.output());
        if (!result.leftovers().isEmpty()) {
            System.out.println("남은 빈칸: " + result.leftovers());
            return 1;
        }
        return 0;
    }

    private static int verify(Map<String, String> o, PandocRunner pandoc, Path workDir) {
        VerifyReport report = new RoundtripVerifier(pandoc)
                .verify(Path.of(require(o, "manuscript")), Path.of(require(o, "docx")), workDir);
        if (o.containsKey("report")) {
            write(Path.of(o.get("report")), report);
        }
        long matched = report.units().stream().filter(VerifyReport.UnitResult::matched).count();
        System.out.println("역변환 비교: " + matched + "/" + report.units().size() + " 일치");
        for (VerifyReport.UnitResult u : report.mismatches()) {
            System.out.println("  불일치 " + u.key() + " (수식 " + u.expectedMath() + "→" + u.actualMath() + ")");
            u.differences().stream().limit(5).forEach(d ->
                    System.out.println("    …" + visible(d.before()) + " [" + visible(d.expected()) + "] → [" + visible(d.actual()) + "]"));
        }
        return report.allMatched() ? 0 : 1;
    }

    private static int lint(Map<String, String> o) {
        TemplateInspector.Result result = new TemplateInspector().inspect(Path.of(require(o, "template")));
        result.errors().forEach(e -> System.out.println("오류: " + e));
        result.warnings().forEach(w -> System.out.println("주의: " + w));
        System.out.println(result.passed() ? "형식 파일 검사 통과" : "형식 파일 검사 실패");
        return result.passed() ? 0 : 1;
    }

    /** 폭 없는 공백 같은 보이지 않는 글자를 U+200B처럼 드러낸다 */
    static String visible(String s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.FORMAT || type == Character.CONTROL && cp != '\n') {
                out.append(String.format("<U+%04X>", cp));
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    /** "--key value" 쌍. 값 없는 "--answers"는 플래그 */
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

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null) {
            throw new IllegalArgumentException("--" + key + " 옵션이 필요합니다");
        }
        return value;
    }

    private static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), value);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 쓸 수 없습니다: " + file, e);
        }
    }
}
