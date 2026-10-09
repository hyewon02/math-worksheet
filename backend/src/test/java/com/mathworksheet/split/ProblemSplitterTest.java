package com.mathworksheet.split;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ProblemSplitterTest {

    final ObjectMapper json = new ObjectMapper();
    final ProblemSplitter splitter = new ProblemSplitter();

    @Test
    void 줄_첫머리의_번호_모양에서_나눈다() {
        JsonNode r = response(
                line("text", "10. 그림의 정육면체에서", 330, false),
                line("text", "(1) 5", 860, false),
                line("text", "04 삼각형 ㄱㄴㄷ을", 900, false),
                line("text", "- 문제 4 다음 표는", 1000, false),
                line("text", "1203 다음 식을", 1100, false));

        assertThat(splitter.split(r).numbers()).containsExactly("10", "04", "문제 4", "1203");
    }

    @Test
    void 쪽_번호_소단원_표시_손글씨_표_칸은_경계가_아니다() {
        JsonNode r = response(
                line("page_info", "03", 190, false),
                line("text", "  04", 891, false),
                line("table_cell", "3 다음", 300, false),
                line("text", "2.11 정답", 1529, true),
                line("text", "(2) \\(y=-0.2 x\\)", 1491, false),
                line("text", "8. 삼각형의 세 변", 365, false));

        assertThat(splitter.split(r).numbers()).containsExactly("8");
    }

    @Test
    void 예제는_경계로_나누되_문제_번호에는_넣지_않는다() {
        var split = splitter.split(response(
                line("text", "1. 첫 문제", 100, false),
                line("text", "예제 1 다음은 포도", 300, false),
                line("text", "문제 2 다음 표는", 600, false)));

        assertThat(split.segments()).hasSize(3);
        assertThat(split.numbers()).containsExactly("1", "문제 2");
    }

    @Test
    void 묶음_문제를_알아본다() {
        assertThat(splitter.split(response(line("text", "[3~4] 다음 글을 읽고", 100, false))).segments().get(0))
                .satisfies(s -> {
                    assertThat(s.kind()).isEqualTo(ProblemSplitter.Kind.GROUP);
                    assertThat(s.label()).isEqualTo("[3~4]");
                });
    }

    @Test
    void 경계_사이의_줄과_앞에서_이어진_줄을_센다() {
        var split = splitter.split(response(
                line("text", "가 52 cm입니다.", 40, false),
                line("text", "08 직선", 535, false),
                line("text", "일부분입니다.", 595, false),
                line("text", "09 삼각형", 1276, false)));

        assertThat(split.leading()).isEqualTo(1);
        assertThat(split.segments().get(0)).satisfies(s -> {
            assertThat(s.firstLine()).isEqualTo(1);
            assertThat(s.lastLine()).isEqualTo(2);
            assertThat(s.top()).isEqualTo(535);
        });
    }

    @Test
    void 그림_영역을_사각형으로_돌려준다() {
        ObjectNode diagram = line("diagram", "", 0, false);
        diagram.set("cnt", json.valueToTree(List.of(List.of(390, 500), List.of(700, 500), List.of(700, 854), List.of(390, 854))));

        var figures = splitter.split(response(diagram)).figures();

        assertThat(figures).containsExactly(new ProblemSplitter.FigureBox("diagram", 390, 500, 310, 354));
    }

    @Test
    void 정확도는_순서까지_맞아야_맞은_것으로_센다() {
        var page = SplitEvaluation.compare("p", List.of("1", "2", "3", "4", "문제 1"), List.of("3", "4", "문제1", "7"));

        assertThat(page.found()).containsExactly("3", "4", "문제 1");
        assertThat(page.missed()).containsExactly("1", "2");
        assertThat(page.extra()).containsExactly("7");
        assertThat(page.exact()).isFalse();
    }

    private JsonNode response(ObjectNode... lines) {
        ObjectNode root = json.createObjectNode();
        ArrayNode data = root.putArray("line_data");
        for (ObjectNode l : lines) {
            data.add(l);
        }
        return root;
    }

    private ObjectNode line(String type, String text, int top, boolean handwritten) {
        ObjectNode l = json.createObjectNode();
        l.put("type", type);
        l.put("text", text);
        l.put("is_handwritten", handwritten);
        l.set("cnt", json.valueToTree(List.of(List.of(0, top), List.of(100, top), List.of(100, top + 30), List.of(0, top + 30))));
        return l;
    }
}
