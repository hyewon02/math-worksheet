package com.mathworksheet.worksheet.verify;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Pandoc JSON AST에서 문제별 "비교할 글자"를 뽑는다.
 * <ul>
 *   <li>원고: {@code ::: {.problem num=…}} 구역 단위. {@code answer} 구역은 뺀다.</li>
 *   <li>역변환 결과({@code docx+styles}): 스타일 이름이 {@code custom-style}로 남는다. ProblemNumber 글자가 나오면
 *       새 문제가 시작된다. 프로그램이 붙인 번호·{@code <보 기>} 제목·정답 줄·정답표는 뺀다.</li>
 * </ul>
 * 묶음 문제의 공통 지문은 "묶음@첫 하위 문제 번호"를 키로 따로 비교한다.
 * 수식은 {@code ⟦…⟧}로 감싸 차이를 보여줄 때 수식 구간을 알아볼 수 있게 한다.
 */
public final class ProblemTextExtractor {

    public static final String GROUP_PREFIX = "묶음@";

    /** 프로그램이 붙이는 단락 스타일. 비교에서 뺀다 */
    static final Set<String> GENERATED_PARAGRAPH_STYLES = Set.of("Answer", "BogiTitle", "AnswerTable");

    private static final Set<String> BLOCK_TYPES = Set.of("Plain", "Para", "LineBlock", "CodeBlock", "RawBlock",
            "BlockQuote", "OrderedList", "BulletList", "DefinitionList", "Header", "HorizontalRule", "Table",
            "Figure", "Div");

    public record Unit(String text, int mathCount) {
    }

    private ProblemTextExtractor() {
    }

    // ---------- 원고 ----------

    public static Map<String, Unit> fromManuscript(JsonNode ast) {
        Map<String, Unit> units = new LinkedHashMap<>();
        for (JsonNode block : ast.path("blocks")) {
            manuscriptBlock(block, units);
        }
        return units;
    }

    private static void manuscriptBlock(JsonNode block, Map<String, Unit> units) {
        if (!"Div".equals(type(block))) {
            return;
        }
        JsonNode attr = block.path("c").get(0);
        JsonNode children = block.path("c").get(1);
        if (hasClass(attr, "problem")) {
            Collector c = new Collector();
            for (JsonNode child : children) {
                if (!("Div".equals(type(child)) && hasClass(child.path("c").get(0), "answer"))) {
                    c.block(child);
                }
            }
            units.put(attribute(attr, "num"), c.unit());
        } else if (hasClass(attr, "group")) {
            Collector stem = new Collector();
            String firstNum = null;
            Map<String, Unit> problems = new LinkedHashMap<>();
            for (JsonNode child : children) {
                if ("Div".equals(type(child)) && hasClass(child.path("c").get(0), "problem")) {
                    if (firstNum == null) {
                        firstNum = attribute(child.path("c").get(0), "num");
                    }
                    manuscriptBlock(child, problems);
                } else {
                    stem.block(child);
                }
            }
            units.put(GROUP_PREFIX + firstNum, stem.unit());
            units.putAll(problems);
        }
    }

    // ---------- 역변환 결과 ----------

    public static Map<String, Unit> fromRoundtrip(JsonNode ast) {
        RoundtripWalker walker = new RoundtripWalker();
        for (JsonNode block : ast.path("blocks")) {
            walker.block(block, null);
        }
        walker.finish();
        return walker.units;
    }

    private static final class RoundtripWalker {
        final Map<String, Unit> units = new LinkedHashMap<>();
        String currentKey;
        Collector current;
        Collector pendingGroup;
        boolean previousWasGroupStem;

        void block(JsonNode block, String style) {
            String t = type(block);
            if ("Div".equals(t)) {
                JsonNode attr = block.path("c").get(0);
                String divStyle = attribute(attr, "custom-style");
                for (JsonNode child : block.path("c").get(1)) {
                    block(child, divStyle != null ? divStyle : style);
                }
                return;
            }
            if (style != null && GENERATED_PARAGRAPH_STYLES.contains(style)) {
                return;
            }
            // 묶음 공통 지문 앞의 "[9~10]"도 ProblemNumber 글자라서, 문제 시작으로 보기 전에 묶음부터 가려낸다
            if (!"GroupStem".equals(style) && ("Para".equals(t) || "Plain".equals(t))) {
                String number = problemNumber(block.path("c"));
                if (number != null) {
                    startProblem(number);
                }
            }
            if ("GroupStem".equals(style)) {
                if (!previousWasGroupStem) {
                    pendingGroup = new Collector();
                }
                pendingGroup.block(block);
                previousWasGroupStem = true;
                return;
            }
            previousWasGroupStem = false;
            if (current != null) {
                current.block(block);
            }
            // 첫 문제 앞(정답지의 빠른 정답표 등)은 비교 대상이 아니다
        }

        void startProblem(String number) {
            finish();
            if (pendingGroup != null) {
                units.put(GROUP_PREFIX + number, pendingGroup.unit());
                pendingGroup = null;
            }
            currentKey = number;
            current = new Collector();
            previousWasGroupStem = false;
        }

        void finish() {
            if (current != null) {
                units.put(currentKey, current.unit());
                current = null;
            }
        }
    }

    /** 단락 안 ProblemNumber 글자에서 숫자만 꺼낸다("12." → "12") */
    private static String problemNumber(JsonNode inlines) {
        for (JsonNode inline : inlines) {
            if ("Span".equals(type(inline)) && "ProblemNumber".equals(attribute(inline.path("c").get(0), "custom-style"))) {
                Collector c = new Collector();
                c.inlines(inline.path("c").get(1));
                String digits = c.text.toString().replaceAll("[^0-9]", "");
                return digits.isEmpty() ? null : digits;
            }
        }
        return null;
    }

    // ---------- 글자 모으기 ----------

    private static final class Collector {
        final StringBuilder text = new StringBuilder();
        int mathCount;

        Unit unit() {
            return new Unit(text.toString().strip(), mathCount);
        }

        void block(JsonNode block) {
            JsonNode c = block.path("c");
            switch (type(block)) {
                case "Para", "Plain" -> {
                    inlines(c);
                    text.append('\n');
                }
                case "Header" -> {
                    inlines(c.get(2));
                    text.append('\n');
                }
                case "LineBlock" -> {
                    for (JsonNode line : c) {
                        inlines(line);
                        text.append('\n');
                    }
                }
                case "CodeBlock" -> text.append(c.get(1).asText()).append('\n');
                case "Div" -> {
                    for (JsonNode child : c.get(1)) {
                        block(child);
                    }
                }
                case "RawBlock", "HorizontalRule" -> {
                }
                // 표, 목록, 인용, 그림 캡션: 안에 든 블록을 문서 순서대로 찾아 모은다
                default -> scanBlocks(c);
            }
        }

        void scanBlocks(JsonNode node) {
            if (node.isObject() && BLOCK_TYPES.contains(type(node))) {
                block(node);
            } else if (node.isContainerNode()) {
                for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                    scanBlocks(it.next());
                }
            }
        }

        void inlines(JsonNode inlines) {
            for (JsonNode inline : inlines) {
                inline(inline);
            }
        }

        void inline(JsonNode inline) {
            JsonNode c = inline.path("c");
            switch (type(inline)) {
                case "Str" -> text.append(c.asText());
                case "Space", "SoftBreak", "LineBreak" -> text.append(' ');
                case "Math" -> {
                    mathCount++;
                    text.append('⟦').append(c.get(1).asText()).append('⟧');
                }
                case "Code" -> text.append(c.get(1).asText());
                case "Span" -> {
                    if (!"ProblemNumber".equals(attribute(c.get(0), "custom-style"))) {
                        inlines(c.get(1));
                    }
                }
                case "Quoted" -> {
                    String q = "SingleQuote".equals(type(c.get(0))) ? "'" : "\"";
                    text.append(q);
                    inlines(c.get(1));
                    text.append(q);
                }
                case "Emph", "Strong", "Underline", "Strikeout", "Superscript", "Subscript", "SmallCaps" -> inlines(c);
                case "Link", "Cite" -> inlines(c.get(1));
                // 그림은 글자 비교 대상이 아니다. RawInline은 필터가 넣은 탭 등이다.
                default -> {
                }
            }
        }
    }

    // ---------- AST 도우미 ----------

    private static String type(JsonNode node) {
        return node.path("t").asText();
    }

    private static boolean hasClass(JsonNode attr, String cls) {
        for (JsonNode c : attr.get(1)) {
            if (cls.equals(c.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String attribute(JsonNode attr, String key) {
        for (JsonNode kv : attr.get(2)) {
            if (key.equals(kv.get(0).asText())) {
                return kv.get(1).asText();
            }
        }
        return null;
    }
}
