package com.mathworksheet.worksheet.docx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

/**
 * 형식 파일 등록 검사(설계서 8장 "형식 파일 제작·등록·검사"). 필수 스타일 13개와 머리말·꼬리말의 빈칸 4개를 확인한다.
 * <p>
 * 오류 문장은 선생님이 그대로 외부 AI 대화창에 붙여 넣어 고치게 할 수 있도록 완결된 문장으로 쓴다.
 */
public class TemplateInspector {

    public enum Kind {
        PARAGRAPH("단락", STStyleType.PARAGRAPH),
        CHARACTER("문자", STStyleType.CHARACTER),
        TABLE("표", STStyleType.TABLE);

        final String label;
        final STStyleType.Enum type;

        Kind(String label, STStyleType.Enum type) {
            this.label = label;
            this.type = type;
        }
    }

    public record RequiredStyle(String name, Kind kind) {
    }

    /** 설계서 8장 필수 스타일 표와 같은 순서 */
    public static final List<RequiredStyle> REQUIRED_STYLES = List.of(
            new RequiredStyle("Problem", Kind.PARAGRAPH),
            new RequiredStyle("ProblemNumber", Kind.CHARACTER),
            new RequiredStyle("GroupStem", Kind.PARAGRAPH),
            new RequiredStyle("Choices5", Kind.PARAGRAPH),
            new RequiredStyle("Choices3", Kind.PARAGRAPH),
            new RequiredStyle("Choices1", Kind.PARAGRAPH),
            new RequiredStyle("BogiTitle", Kind.PARAGRAPH),
            new RequiredStyle("BogiBox", Kind.PARAGRAPH),
            new RequiredStyle("Figure", Kind.PARAGRAPH),
            new RequiredStyle("Table", Kind.TABLE),
            new RequiredStyle("Answer", Kind.PARAGRAPH),
            new RequiredStyle("CorrectChoice", Kind.CHARACTER),
            new RequiredStyle("AnswerTable", Kind.TABLE));

    /**
     * @param errors   등록을 막는 문제
     * @param warnings 등록은 되지만 알려 줄 것(예: 빈칸 글자가 쪼개져 저장됨. 채우기는 정상 동작)
     */
    public record Result(List<String> errors, List<String> warnings) {

        public boolean passed() {
            return errors.isEmpty();
        }
    }

    public Result inspect(Path template) {
        try (InputStream in = Files.newInputStream(template); XWPFDocument doc = new XWPFDocument(in)) {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            checkStyles(doc.getStyles(), errors);
            checkPlaceholders(doc, errors, warnings);
            return new Result(errors, warnings);
        } catch (IOException | RuntimeException e) {
            return new Result(List.of("Word 파일(.docx)로 열 수 없습니다. 손상되지 않은 .docx 파일인지 확인해 주세요."), List.of());
        }
    }

    private static void checkStyles(XWPFStyles styles, List<String> errors) {
        for (RequiredStyle required : REQUIRED_STYLES) {
            XWPFStyle style = styles == null ? null : styles.getStyleWithName(required.name());
            if (style == null && styles != null) {
                style = styles.getStyle(required.name());
            }
            if (style == null) {
                errors.add("필수 스타일 '" + required.name() + "'(" + required.kind().label
                        + " 스타일)이 없습니다. 이 이름 그대로 " + required.kind().label + " 스타일을 추가해 주세요.");
            } else if (style.getType() != required.kind().type) {
                errors.add("스타일 '" + required.name() + "'은 " + required.kind().label
                        + " 스타일이어야 합니다. 같은 이름으로 종류만 " + required.kind().label + " 스타일로 바꿔 주세요.");
            }
        }
    }

    private static void checkPlaceholders(XWPFDocument doc, List<String> errors, List<String> warnings) {
        List<XWPFParagraph> paragraphs = new ArrayList<>();
        for (XWPFHeaderFooter hf : PlaceholderFiller.headersAndFooters(doc)) {
            PlaceholderFiller.forEachParagraphIn(hf, paragraphs::add);
        }
        for (String name : PlaceholderFiller.REQUIRED) {
            String token = "{{" + name + "}}";
            List<XWPFParagraph> found = paragraphs.stream().filter(p -> p.getText().contains(token)).toList();
            if (found.isEmpty()) {
                errors.add("머리말이나 꼬리말에 빈칸 " + token + "이 없습니다. "
                        + (PlaceholderFiller.NUMBER.equals(name) ? "꼬리말" : "머리말") + "에 " + token + "를 그대로 입력해 주세요.");
            } else if (found.stream().noneMatch(p -> inSingleRun(p, token))) {
                warnings.add("빈칸 " + token + "의 글자가 여러 조각으로 나뉘어 저장되어 있습니다. 채우기는 되지만, "
                        + "고칠 때는 " + token + "를 지우고 한 번에 다시 입력해 주세요.");
            }
        }
    }

    private static boolean inSingleRun(XWPFParagraph p, String token) {
        for (XWPFRun run : p.getRuns()) {
            String t = run.getText(0);
            if (t != null && t.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
