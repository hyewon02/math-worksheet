package com.mathworksheet.worksheet.docx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaceholderFillerTest {

    @TempDir
    Path dir;

    final PlaceholderFiller filler = new PlaceholderFiller();
    final Map<String, String> values = Map.of(
            "학원명", "율현수학", "시험제목", "1-2 중간고사", "날짜", "2026-10-06", "문제지번호", "WS-0042");

    @Test
    void 머리말과_꼬리말의_빈칸을_채운다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.header(doc, List.of("{{학원명}} · {{시험제목}} · {{날짜}}"));
        DocxFixtures.footer(doc, List.of("{{문제지번호}}"));
        Path source = DocxFixtures.save(doc, dir.resolve("in.docx"));

        Set<String> leftovers = filler.fill(source, dir.resolve("out.docx"), values);

        assertThat(leftovers).isEmpty();
        try (XWPFDocument out = open(dir.resolve("out.docx"))) {
            assertThat(out.getHeaderList().get(0).getText()).contains("율현수학 · 1-2 중간고사 · 2026-10-06");
            assertThat(out.getFooterList().get(0).getText()).contains("WS-0042");
        }
    }

    @Test
    void 여러_run으로_쪼개진_빈칸도_채운다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.header(doc, List.of("시험: {{시험", "제목", "}} 끝"));
        Path source = DocxFixtures.save(doc, dir.resolve("in.docx"));

        filler.fill(source, dir.resolve("out.docx"), values);

        try (XWPFDocument out = open(dir.resolve("out.docx"))) {
            XWPFParagraph p = out.getHeaderList().get(0).getParagraphs().get(0);
            assertThat(p.getText()).isEqualTo("시험: 1-2 중간고사 끝");
        }
    }

    @Test
    void 모르는_빈칸은_남겨_두고_알려준다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.header(doc, List.of("{{시험제목}} {{반이름}}"));
        Path source = DocxFixtures.save(doc, dir.resolve("in.docx"));

        Set<String> leftovers = filler.fill(source, dir.resolve("out.docx"), values);

        assertThat(leftovers).containsExactly("반이름");
    }

    @Test
    void 입력값에_빈칸_모양이_있어도_다시_바꾸지_않는다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.header(doc, List.of("{{시험제목}}"));
        Path source = DocxFixtures.save(doc, dir.resolve("in.docx"));

        filler.fill(source, dir.resolve("out.docx"), Map.of("시험제목", "{{시험제목}} 복습"));

        try (XWPFDocument out = open(dir.resolve("out.docx"))) {
            assertThat(out.getHeaderList().get(0).getText()).contains("{{시험제목}} 복습");
        }
    }

    @Test
    void 빈칸이_없는_본문_글자는_그대로_둔다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        String body = "  함수 f(x)의  최솟값은?  {중괄호} 그대로 ";
        DocxFixtures.paragraph(doc.createParagraph(), List.of(body));
        Path source = DocxFixtures.save(doc, dir.resolve("in.docx"));

        filler.fill(source, dir.resolve("out.docx"), values);

        try (XWPFDocument out = open(dir.resolve("out.docx"))) {
            assertThat(out.getParagraphs().get(0).getText()).isEqualTo(body);
        }
    }

    private static XWPFDocument open(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return new XWPFDocument(in);
        }
    }
}
