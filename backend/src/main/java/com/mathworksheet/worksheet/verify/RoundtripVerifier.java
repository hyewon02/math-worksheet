package com.mathworksheet.worksheet.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.mathworksheet.worksheet.pandoc.PandocRunner;
import com.mathworksheet.worksheet.verify.ProblemTextExtractor.Unit;
import com.mathworksheet.worksheet.verify.VerifyReport.Difference;
import com.mathworksheet.worksheet.verify.VerifyReport.UnitResult;

/**
 * 생성 후 검증(설계서 8장 "검증(역변환 비교)"). 만든 docx를 {@code docx+styles}로 다시 읽어 원고와 문제별로 비교한다.
 * <p>
 * LaTeX와 Word 수식(OMML)은 글자로 직접 비교할 수 없어서, Pandoc이 OMML을 다시 LaTeX로 바꾼 결과를 정규화해 비교한다.
 * 불일치가 있어도 내려받기를 막지 않고 문제 번호와 다른 부분을 보여준다(시험 직전 차단 방지, 설계서 13장).
 */
public class RoundtripVerifier {

    static final String MANUSCRIPT_FORMAT = "markdown+tex_math_single_backslash";

    private final PandocRunner pandoc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoundtripVerifier(PandocRunner pandoc) {
        this.pandoc = pandoc;
    }

    public VerifyReport verify(Path manuscript, Path docx, Path workDir) {
        JsonNode expectedAst = toAst(manuscript, MANUSCRIPT_FORMAT, workDir);
        JsonNode actualAst = toAst(docx, "docx+styles", workDir);
        return compare(manuscript.toString(), docx.toString(),
                ProblemTextExtractor.fromManuscript(expectedAst), ProblemTextExtractor.fromRoundtrip(actualAst));
    }

    static VerifyReport compare(String manuscript, String docx, Map<String, Unit> expected, Map<String, Unit> actual) {
        Set<String> keys = new LinkedHashSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        List<UnitResult> results = new ArrayList<>();
        for (String key : keys) {
            Unit e = expected.get(key);
            Unit a = actual.get(key);
            if (e == null || a == null) {
                results.add(new UnitResult(key, false, e == null ? 0 : e.mathCount(), a == null ? 0 : a.mathCount(),
                        e == null ? null : e.text(), a == null ? null : a.text(),
                        List.of(new Difference("", e == null ? "(원고에 없음)" : "(문제 전체)", a == null ? "(결과에 없음)" : "(문제 전체)"))));
                continue;
            }
            String ne = normalize(e.text());
            String na = normalize(a.text());
            List<Difference> differences = differences(ne, na);
            boolean matched = differences.isEmpty() && e.mathCount() == a.mathCount();
            results.add(new UnitResult(key, matched, e.mathCount(), a.mathCount(), e.text(), a.text(), differences));
        }
        return new VerifyReport(manuscript, docx, results);
    }

    /** ⟦…⟧ 안은 수식 규칙, 밖은 글자 규칙으로 정규화 */
    static String normalize(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('⟦', i);
            if (open < 0) {
                out.append(ComparisonNormalizer.text(text.substring(i)));
                break;
            }
            int close = text.indexOf('⟧', open);
            out.append(ComparisonNormalizer.text(text.substring(i, open)));
            out.append('⟦').append(ComparisonNormalizer.math(text.substring(open + 1, close))).append('⟧');
            i = close + 1;
        }
        return out.toString();
    }

    private static List<Difference> differences(String expected, String actual) {
        List<String> e = expected.codePoints().mapToObj(Character::toString).toList();
        List<String> a = actual.codePoints().mapToObj(Character::toString).toList();
        List<Difference> result = new ArrayList<>();
        for (AbstractDelta<String> delta : DiffUtils.diff(e, a).getDeltas()) {
            int at = delta.getSource().getPosition();
            String before = String.join("", e.subList(Math.max(0, at - 12), at));
            result.add(new Difference(before, String.join("", delta.getSource().getLines()),
                    String.join("", delta.getTarget().getLines())));
        }
        return result;
    }

    private JsonNode toAst(Path input, String from, Path workDir) {
        String json = pandoc.run(List.of("-f", from, "-t", "json", input.toAbsolutePath().toString()), workDir);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("pandoc JSON을 읽을 수 없습니다: " + input, e);
        }
    }
}
