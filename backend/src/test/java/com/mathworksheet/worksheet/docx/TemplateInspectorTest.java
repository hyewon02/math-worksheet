package com.mathworksheet.worksheet.docx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import com.mathworksheet.worksheet.PandocTestSupport;

class TemplateInspectorTest {

    @TempDir
    Path dir;

    final TemplateInspector inspector = new TemplateInspector();

    @Test
    void 필수_스타일과_빈칸이_모두_있으면_통과한다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.addRequiredStyles(doc);
        addPlaceholders(doc);
        Path template = DocxFixtures.save(doc, dir.resolve("ok.docx"));

        TemplateInspector.Result result = inspector.inspect(template);

        assertThat(result.passed()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void 빠진_스타일과_빈칸을_문장으로_알려준다() throws Exception {
        Path template = DocxFixtures.save(new XWPFDocument(), dir.resolve("empty.docx"));

        TemplateInspector.Result result = inspector.inspect(template);

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).hasSize(13 + 4);
        assertThat(result.errors()).contains(
                "필수 스타일 'ProblemNumber'(문자 스타일)이 없습니다. 이 이름 그대로 문자 스타일을 추가해 주세요.");
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("{{문제지번호}}").contains("꼬리말"));
    }

    @Test
    void 스타일_종류가_다르면_실패한다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        for (TemplateInspector.RequiredStyle s : TemplateInspector.REQUIRED_STYLES) {
            DocxFixtures.addStyle(doc, s.name(), s.name().equals("Answer") ? STStyleType.CHARACTER : s.kind().type);
        }
        addPlaceholders(doc);
        Path template = DocxFixtures.save(doc, dir.resolve("wrong.docx"));

        assertThat(inspector.inspect(template).errors()).containsExactly(
                "스타일 'Answer'은 단락 스타일이어야 합니다. 같은 이름으로 종류만 단락 스타일로 바꿔 주세요.");
    }

    @Test
    void 쪼개진_빈칸은_통과시키되_주의를_준다() throws Exception {
        XWPFDocument doc = new XWPFDocument();
        DocxFixtures.addRequiredStyles(doc);
        DocxFixtures.header(doc, List.of("{{학원명}} {{시험", "제목}} {{날짜}}"));
        DocxFixtures.footer(doc, List.of("{{문제지번호}}"));
        Path template = DocxFixtures.save(doc, dir.resolve("split.docx"));

        TemplateInspector.Result result = inspector.inspect(template);

        assertThat(result.passed()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("{{시험제목}}");
    }

    @Test
    void docx가_아니면_실패한다() throws Exception {
        Path notDocx = Files.writeString(dir.resolve("a.docx"), "text");

        assertThat(inspector.inspect(notDocx).passed()).isFalse();
    }

    @Test
    void 내장_형식_파일이_검사를_통과한다() {
        Path builtIn = PandocTestSupport.worksheetDir().resolve("templates/naesin-2col.docx");
        assumeTrue(Files.exists(builtIn), "내장 형식 파일이 아직 없음");

        TemplateInspector.Result result = inspector.inspect(builtIn);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    private static void addPlaceholders(XWPFDocument doc) {
        DocxFixtures.header(doc, List.of("{{학원명}}", " ", "{{시험제목}}", " ", "{{날짜}}"));
        DocxFixtures.footer(doc, List.of("{{문제지번호}}"));
    }
}
