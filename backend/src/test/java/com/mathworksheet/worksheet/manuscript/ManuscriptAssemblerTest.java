package com.mathworksheet.worksheet.manuscript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mathworksheet.problem.domain.AnswerStatus;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.worksheet.domain.ProblemSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.ChoiceSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.FigureSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.GroupSnapshot;
import com.mathworksheet.worksheet.domain.SpaceSize;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Item;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Layout;

class ManuscriptAssemblerTest {

    final ManuscriptAssembler assembler = new ManuscriptAssembler();
    final Layout layout = new Layout(type -> switch (type) {
        case CHOICE -> new BigDecimal("1.0");
        case SHORT -> new BigDecimal("3.0");
        case ESSAY -> new BigDecimal("8.0");
    }, new BigDecimal("8.2"));

    @Test
    void 담은_순서대로_1번부터_번호를_매긴다() {
        String md = assembler.assemble(List.of(
                item(shortProblem(10L, "첫 문제", "1"), SpaceSize.NORMAL),
                item(shortProblem(20L, "둘째 문제", "2"), SpaceSize.NORMAL)), layout).markdown();

        assertThat(md).contains("::: {.problem num=\"1\" type=\"short\" space=\"3cm\"}\n첫 문제")
                .contains("::: {.problem num=\"2\" type=\"short\" space=\"3cm\"}\n둘째 문제");
    }

    @Test
    void 풀이_공간은_형식_기본값에_크기를_곱한다() {
        String md = assembler.assemble(List.of(
                item(shortProblem(1L, "a", "1"), SpaceSize.SMALL),
                item(shortProblem(2L, "b", "1"), SpaceSize.LARGE)), layout).markdown();

        assertThat(md).contains("num=\"1\" type=\"short\" space=\"1.5cm\"").contains("num=\"2\" type=\"short\" space=\"6cm\"");
    }

    @Test
    void 보기와_정답을_구역으로_붙인다() {
        ProblemSnapshot p = new ProblemSnapshot(1L, 0, ProblemType.CHOICE, "최솟값은?",
                List.of(new ChoiceSnapshot(1, "\\(-4\\)", false), new ChoiceSnapshot(2, "\\(-3\\)", false)),
                "②", AnswerStatus.ENTERED, "교재 1쪽", List.of(), null);

        String md = assembler.assemble(List.of(item(p, SpaceSize.NORMAL)), layout).markdown();

        assertThat(md).isEqualTo("""
                ::: {.problem num="1" type="choice" space="1cm"}
                최솟값은?

                ::: choices
                ① \\(-4\\)
                ② \\(-3\\)
                :::

                ::: answer
                ②
                :::
                :::
                """);
    }

    @Test
    void 정답이_없을_때만_출처를_넣는다() {
        ProblemSnapshot noAnswer = new ProblemSnapshot(1L, 0, ProblemType.SHORT, "본문", List.of(), null,
                AnswerStatus.LATER, "쎈 \"중2\" 52쪽 1203번", List.of(), null);

        String md = assembler.assemble(List.of(item(noAnswer, SpaceSize.NORMAL)), layout).markdown();

        assertThat(md).contains("source=\"쎈 \\\"중2\\\" 52쪽 1203번\"").doesNotContain("::: answer");
        assertThat(assembler.assemble(List.of(item(shortProblem(2L, "본문", "4"), SpaceSize.NORMAL)), layout).markdown())
                .doesNotContain("source=");
    }

    @Test
    void 이어진_묶음_문제는_공통_지문을_한_번만_넣고_범위를_매긴다() {
        GroupSnapshot g = new GroupSnapshot(7L, 0, "공통 지문", List.of());
        String md = assembler.assemble(List.of(
                item(shortProblem(1L, "앞 문제", "1"), SpaceSize.NORMAL),
                item(grouped(2L, "하위 1", g), SpaceSize.NORMAL),
                item(grouped(3L, "하위 2", g), SpaceSize.NORMAL)), layout).markdown();

        assertThat(md).contains("::: {.group range=\"2~3\"}\n공통 지문\n\n::: {.problem num=\"2\"");
        assertThat(md.indexOf("공통 지문")).isEqualTo(md.lastIndexOf("공통 지문"));
        assertThat(md).endsWith("하위 2\n\n::: answer\n1\n:::\n:::\n:::\n");
    }

    @Test
    void 그림_표시를_단_너비_기준_cm_크기의_그림으로_바꾼다() {
        ProblemSnapshot p = new ProblemSnapshot(1L, 0, ProblemType.SHORT, "그림을 보고\n\n[그림:p12_1]", List.of(), "1",
                AnswerStatus.ENTERED, "", List.of(new FigureSnapshot("p12_1", "figures/12/12_1.png", 30)), null);

        var manuscript = assembler.assemble(List.of(item(p, SpaceSize.NORMAL)), layout);

        assertThat(manuscript.markdown()).contains("![](images/p12_1.png){width=2.5cm}");
        assertThat(manuscript.figureFiles()).containsEntry("images/p12_1.png", "figures/12/12_1.png");
        assertThat(manuscript.unknownMarkers()).isEmpty();
    }

    @Test
    void 그림_정보가_없는_표시는_그대로_두고_누락으로_알린다() {
        var manuscript = assembler.assemble(List.of(item(shortProblem(1L, "[그림:없는그림]", "1"), SpaceSize.NORMAL)), layout);

        assertThat(manuscript.markdown()).contains("[그림:없는그림]");
        assertThat(manuscript.unknownMarkers()).containsExactly("없는그림");
    }

    private static Item item(ProblemSnapshot p, SpaceSize space) {
        return new Item(p, space);
    }

    private static ProblemSnapshot shortProblem(Long id, String body, String answer) {
        return new ProblemSnapshot(id, 0, ProblemType.SHORT, body, List.of(), answer,
                answer == null ? AnswerStatus.LATER : AnswerStatus.ENTERED, "", List.of(), null);
    }

    private static ProblemSnapshot grouped(Long id, String body, GroupSnapshot g) {
        return new ProblemSnapshot(id, 0, ProblemType.SHORT, body, List.of(), "1", AnswerStatus.ENTERED, "", List.of(), g);
    }
}
