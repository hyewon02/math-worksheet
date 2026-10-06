package com.mathworksheet.worksheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mathworksheet.worksheet.docx.DocxFixtures;
import com.mathworksheet.worksheet.docx.PlaceholderFiller;
import com.mathworksheet.worksheet.pandoc.PandocRunner;

class WorksheetRendererTest {

    @TempDir
    Path dir;

    final Map<String, String> values = Map.of(
            "학원명", "율현수학", "시험제목", "1-2 중간고사", "날짜", "2026-10-06", "문제지번호", "WS-0042");

    @Test
    void 정답지는_answers_옵션을_켜서_pandoc을_부른다() {
        var renderer = new WorksheetRenderer(new PandocRunner("pandoc", java.time.Duration.ofSeconds(1)),
                new PlaceholderFiller(), dir.resolve("f.lua"));

        var student = renderer.arguments(request(false), dir.resolve("raw.docx"));
        var answers = renderer.arguments(request(true), dir.resolve("raw.docx"));

        assertThat(student).doesNotContain("answers=true");
        assertThat(answers).containsSubsequence("-M", "answers=true");
        assertThat(answers).contains("-f", "markdown+tex_math_single_backslash");
    }

    @Test
    void 형식_설정의_보기_배치_기준을_필터에_넘긴다() throws Exception {
        Files.writeString(dir.resolve("template.json"), """
                {"name": "촘촘한 2단", "space": {"choice": "1cm"}, "choices5Max": 4, "choices3Max": 8}
                """);
        var renderer = new WorksheetRenderer(new PandocRunner("pandoc", java.time.Duration.ofSeconds(1)),
                new PlaceholderFiller(), dir.resolve("f.lua"));

        var args = renderer.arguments(request(false), dir.resolve("raw.docx"));

        assertThat(args).containsSubsequence("-M", "choices5-max=4", "-M", "choices3-max=8");
    }

    @Test
    void 형식_설정이_없으면_설계서_기본값을_쓴다() {
        var renderer = new WorksheetRenderer(new PandocRunner("pandoc", java.time.Duration.ofSeconds(1)),
                new PlaceholderFiller(), dir.resolve("f.lua"));

        var args = renderer.arguments(request(false), dir.resolve("raw.docx"));

        assertThat(args).contains("choices5-max=9", "choices3-max=15");
    }

    @Test
    void 형식_파일의_머리말을_가져오고_빈칸을_채운다() throws Exception {
        PandocRunner pandoc = PandocTestSupport.pandocOrSkip(dir);
        XWPFDocument template = new XWPFDocument();
        DocxFixtures.header(template, List.of("{{학원명}} {{시험제목}} {{날짜}}"));
        DocxFixtures.footer(template, List.of("{{문제지번호}}"));
        DocxFixtures.save(template, dir.resolve("template.docx"));
        Files.writeString(dir.resolve("worksheet.md"), "함수 \\(f(x)=x^{2}\\)의 최솟값은?");
        Path filter = Files.writeString(dir.resolve("noop.lua"), "return {}");

        var result = new WorksheetRenderer(pandoc, new PlaceholderFiller(), filter)
                .render(request(false), dir.resolve("work"));

        assertThat(result.leftovers()).isEmpty();
        try (InputStream in = Files.newInputStream(result.output()); XWPFDocument out = new XWPFDocument(in)) {
            assertThat(out.getHeaderList()).anySatisfy(h -> assertThat(h.getText()).contains("율현수학 1-2 중간고사 2026-10-06"));
            assertThat(out.getFooterList()).anySatisfy(f -> assertThat(f.getText()).contains("WS-0042"));
            assertThat(out.getParagraphs()).anySatisfy(p -> assertThat(p.getText()).contains("최솟값은?"));
        }
        try (var leftovers = Files.list(dir.resolve("work"))) {
            assertThat(leftovers).as("작업 폴더에 중간 파일을 남기지 않는다").isEmpty();
        }
    }

    private WorksheetRenderer.Request request(boolean answers) {
        return new WorksheetRenderer.Request(dir.resolve("worksheet.md"), dir, dir.resolve("template.docx"),
                answers, values, dir.resolve("out").resolve(answers ? "answers.docx" : "student.docx"));
    }
}
