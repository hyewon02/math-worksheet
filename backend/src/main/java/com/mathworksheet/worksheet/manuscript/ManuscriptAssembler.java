package com.mathworksheet.worksheet.manuscript;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.worksheet.domain.ProblemSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.ChoiceSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.FigureSnapshot;
import com.mathworksheet.worksheet.domain.SpaceSize;

/**
 * 원고 조립(설계서 5장 7단계, docs/manuscript-format.md). 문제지 스냅샷을 Pandoc 원고(마크다운)로 바꾼다.
 * <p>
 * 본문 글자는 그대로 넣고, 붙이는 것은 구역 표시·번호·풀이 공간·이스케이프·그림 경로뿐이다(CLAUDE.md 절대 원칙 1).
 * 상태가 없는 순수 함수라 DB 없이 테스트한다.
 */
public class ManuscriptAssembler {

    private static final Pattern FIGURE_MARKER = Pattern.compile("\\[그림:([^\\]]+)]");
    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤"};

    /** 문제지에 담긴 순서대로의 문항 */
    public record Item(ProblemSnapshot snapshot, SpaceSize space) {
    }

    /**
     * @param baseSpace   유형별 기본 풀이 공간(cm). 형식 설정에서 온다
     * @param columnWidth 단 너비(cm). 그림 크기(단 너비 대비 %)를 cm로 바꾼다(설계서 9장: Pandoc의 %는 페이지 기준이라 쓰지 않는다)
     */
    public record Layout(Function<ProblemType, BigDecimal> baseSpace, BigDecimal columnWidth) {
    }

    /**
     * @param markdown       원고
     * @param figureFiles    원고가 참조하는 그림: 원고 속 경로(images/…) → 이미지 저장소 상대 경로
     * @param unknownMarkers 본문에 표시는 있는데 그림 정보가 없는 것(설계서 9장 "누락 검사")
     */
    public record Manuscript(String markdown, Map<String, String> figureFiles, Set<String> unknownMarkers) {
    }

    public Manuscript assemble(List<Item> items, Layout layout) {
        Context ctx = new Context(layout);
        List<String> blocks = new ArrayList<>();
        int number = 1;
        int i = 0;
        while (i < items.size()) {
            ProblemSnapshot.GroupSnapshot group = items.get(i).snapshot().group();
            if (group == null) {
                blocks.add(problem(items.get(i), number++, ctx));
                i++;
                continue;
            }
            // 같은 묶음이 이어진 구간을 하나의 group 구역으로 만든다
            int end = i;
            while (end < items.size() && items.get(end).snapshot().group() != null
                    && Objects.equals(items.get(end).snapshot().group().groupId(), group.groupId())) {
                end++;
            }
            int first = number;
            List<String> problems = new ArrayList<>();
            for (int k = i; k < end; k++) {
                problems.add(problem(items.get(k), number++, ctx));
            }
            int last = number - 1;
            String range = first == last ? String.valueOf(first) : first + "~" + last;
            blocks.add("::: {.group range=\"" + range + "\"}\n"
                    + text(group.stemMarkdown(), group.figures(), ctx) + "\n\n"
                    + String.join("\n\n", problems) + "\n:::");
            i = end;
        }
        return new Manuscript(String.join("\n\n", blocks) + "\n", ctx.figureFiles, ctx.unknownMarkers);
    }

    private String problem(Item item, int number, Context ctx) {
        ProblemSnapshot p = item.snapshot();
        StringBuilder header = new StringBuilder("::: {.problem num=\"").append(number)
                .append("\" type=\"").append(p.type().manuscriptName())
                .append("\" space=\"").append(cm(ctx.layout.baseSpace().apply(p.type()).multiply(item.space().factor())))
                .append('"');
        if (!p.answerEntered() && p.source() != null && !p.source().isBlank()) {
            header.append(" source=\"").append(attribute(p.source())).append('"');
        }
        header.append('}');

        List<String> parts = new ArrayList<>();
        parts.add(text(p.bodyMarkdown(), p.figures(), ctx));
        if (p.choices() != null && !p.choices().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (ChoiceSnapshot c : p.choices()) {
                lines.add(CIRCLED[c.number() - 1] + " " + text(c.contentMarkdown(), p.figures(), ctx));
            }
            parts.add("::: choices\n" + String.join("\n", lines) + "\n:::");
        }
        if (p.answerEntered()) {
            parts.add("::: answer\n" + ManuscriptEscaper.escape(p.answer()) + "\n:::");
        }
        return header + "\n" + String.join("\n\n", parts) + "\n:::";
    }

    /** 이스케이프 → 그림 표시 치환. 앞뒤 빈 줄만 걷고 줄 안의 글자는 그대로 둔다 */
    private String text(String markdown, List<FigureSnapshot> figures, Context ctx) {
        String trimmed = markdown.replaceAll("^[\\r\\n]+|[\\r\\n]+$", "");
        String escaped = ManuscriptEscaper.escape(trimmed);
        Matcher m = FIGURE_MARKER.matcher(escaped);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String marker = m.group(1);
            FigureSnapshot figure = figures == null ? null
                    : figures.stream().filter(f -> f.marker().equals(marker)).findFirst().orElse(null);
            if (figure == null) {
                ctx.unknownMarkers.add(marker);
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String path = "images/" + marker + extension(figure.filePath());
            ctx.figureFiles.put(path, figure.filePath());
            BigDecimal width = ctx.layout.columnWidth().multiply(BigDecimal.valueOf(figure.displayWidth()))
                    .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
            m.appendReplacement(out, Matcher.quoteReplacement("![](" + path + "){width=" + cm(width) + "}"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String cm(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "cm";
    }

    private static String attribute(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extension(String filePath) {
        int dot = filePath.lastIndexOf('.');
        return dot < 0 ? ".png" : filePath.substring(dot);
    }

    private static final class Context {
        final Layout layout;
        final Map<String, String> figureFiles = new LinkedHashMap<>();
        final Set<String> unknownMarkers = new LinkedHashSet<>();

        Context(Layout layout) {
            this.layout = layout;
        }
    }
}
