package com.mathworksheet.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 문제 나누기 추천(설계서 5장 3단계). Mathpix 줄별 데이터(line_data)에서 줄 첫머리가 문제 번호 모양인 곳을 경계로 삼는다.
 * 추천일 뿐이며, 검수 화면에서 선생님이 경계선을 옮기거나 더하고 지워 확정한다.
 * <p>
 * 글자는 읽기만 한다. 번호를 찾으려고 앞뒤 공백·기호를 무시하지만 원문은 바꾸지 않는다.
 */
public class ProblemSplitter {

    /** 경계 후보에서 빼는 줄 종류: 머리말·쪽 번호(소단원 표시 포함), 표 칸, 그림, 수식 블록, 답 쓰는 칸 */
    static final Set<String> IGNORED_TYPES = Set.of("page_info", "table_cell", "table", "diagram", "chart",
            "figure_label", "equation_number", "math", "form_field", "column");

    /** 묶음: [3~4], [3∼4], [3-4] */
    static final Pattern GROUP = Pattern.compile("^\\[\\s*(\\d{1,4})\\s*[~∼～\\-]\\s*(\\d{1,4})\\s*]");
    /** "문제 4", "문 제 4" */
    static final Pattern LABELED = Pattern.compile("^문\\s*제\\s*(\\d{1,3})(?!\\d)");
    /** 예제는 문제가 아니지만 앞 문제와 나누는 경계다 */
    static final Pattern EXAMPLE = Pattern.compile("^예\\s*제\\s*(\\d{1,3})(?!\\d)");
    /** "10." "8." */
    static final Pattern DOTTED = Pattern.compile("^(\\d{1,4})\\s*\\.(?!\\d)");
    /** "04 삼각형", "1203 다음", "3 다음" — 번호 뒤에 글자가 이어져야 한다(혼자 있는 숫자는 쪽·소단원 번호일 수 있음) */
    static final Pattern SPACED = Pattern.compile("^(\\d{1,4})\\s+(?=[가-힣A-Za-z\\\\(])");
    /** 줄 앞의 글머리 기호(▸ 등이 '-'로 읽힘) */
    static final Pattern BULLET = Pattern.compile("^[\\-•▸►▶·]\\s*");

    public enum Kind { PROBLEM, GROUP, EXAMPLE }

    /**
     * @param label      사진에 적힌 번호 그대로(예: "04", "문제 4", "[3~4]")
     * @param firstLine  line_data 안 시작 줄 위치
     * @param lastLine   끝 줄 위치(다음 경계 앞까지)
     * @param top        시작 줄 위쪽 y(픽셀)
     */
    public record Segment(Kind kind, String label, int firstLine, int lastLine, int top) {
    }

    /** 그림 영역 추천(설계서 9장): Mathpix가 그림으로 본 줄의 사각형 */
    public record FigureBox(String type, int x, int y, int width, int height) {
    }

    /**
     * @param leading 첫 경계 앞 줄 수(앞 사진에서 이어진 문제이거나 머리말)
     */
    public record Split(List<Segment> segments, int leading, List<FigureBox> figures) {

        /** 문제 번호만(예제 제외), 사진에 적힌 그대로 */
        public List<String> numbers() {
            return segments.stream().filter(s -> s.kind() != Kind.EXAMPLE).map(Segment::label).toList();
        }
    }

    public Split split(JsonNode mathpixResponse) {
        JsonNode lines = mathpixResponse.path("line_data");
        List<Segment> segments = new ArrayList<>();
        List<FigureBox> figures = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            JsonNode line = lines.get(i);
            String type = line.path("type").asText("");
            if ("diagram".equals(type) || "chart".equals(type)) {
                figures.add(box(type, line.path("cnt")));
            }
            Boundary b = boundary(line);
            if (b != null) {
                segments.add(new Segment(b.kind(), b.label(), i, -1, top(line.path("cnt"))));
            }
        }
        List<Segment> closed = new ArrayList<>();
        for (int k = 0; k < segments.size(); k++) {
            Segment s = segments.get(k);
            int last = k + 1 < segments.size() ? segments.get(k + 1).firstLine() - 1 : lines.size() - 1;
            closed.add(new Segment(s.kind(), s.label(), s.firstLine(), last, s.top()));
        }
        int leading = closed.isEmpty() ? lines.size() : closed.get(0).firstLine();
        return new Split(closed, leading, figures);
    }

    private record Boundary(Kind kind, String label) {
    }

    /** 이 줄이 문제 시작이면 번호를, 아니면 null */
    static Boundary boundary(JsonNode line) {
        if (IGNORED_TYPES.contains(line.path("type").asText(""))) {
            return null;
        }
        // 손글씨(풀이 흔적)는 경계가 될 수 없다
        if (line.path("is_handwritten").asBoolean(false)) {
            return null;
        }
        String text = BULLET.matcher(line.path("text").asText("").strip()).replaceFirst("");
        Matcher m;
        if ((m = GROUP.matcher(text)).find()) {
            return new Boundary(Kind.GROUP, "[" + m.group(1) + "~" + m.group(2) + "]");
        }
        if ((m = LABELED.matcher(text)).find()) {
            return new Boundary(Kind.PROBLEM, "문제 " + m.group(1));
        }
        if ((m = EXAMPLE.matcher(text)).find()) {
            return new Boundary(Kind.EXAMPLE, "예제 " + m.group(1));
        }
        if ((m = DOTTED.matcher(text)).find() || (m = SPACED.matcher(text)).find()) {
            return new Boundary(Kind.PROBLEM, m.group(1));
        }
        return null;
    }

    private static int top(JsonNode cnt) {
        int min = Integer.MAX_VALUE;
        for (JsonNode p : cnt) {
            min = Math.min(min, p.get(1).asInt());
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static FigureBox box(String type, JsonNode cnt) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (JsonNode p : cnt) {
            minX = Math.min(minX, p.get(0).asInt());
            maxX = Math.max(maxX, p.get(0).asInt());
            minY = Math.min(minY, p.get(1).asInt());
            maxY = Math.max(maxY, p.get(1).asInt());
        }
        return new FigureBox(type, minX, minY, maxX - minX, maxY - minY);
    }
}
