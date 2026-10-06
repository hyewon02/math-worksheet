package com.mathworksheet.worksheet.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mathworksheet.worksheet.PandocTestSupport;
import com.mathworksheet.worksheet.pandoc.PandocRunner;

/**
 * Lua 필터가 만들 결과를 custom-style 마크다운으로 흉내 내 docx를 만들고, 원고와 비교한다.
 * 필터 구현과 상관없이 "무엇을 비교하고 무엇을 빼는가"를 고정하는 테스트다.
 */
class RoundtripVerifierTest {

    @TempDir
    Path dir;

    PandocRunner pandoc;
    RoundtripVerifier verifier;

    static final String MANUSCRIPT = """
            ::: {.problem num="1" type="choice" space="1cm"}
            함수 \\(f(x)=\\dfrac{1}{2}x^{2}\\)의 최솟값은? \\<보기\\>

            ::: choices
            ① \\(-4\\)
            ② \\(-3\\)
            :::

            ::: answer
            ②
            :::
            :::

            ::: {.group range="2~3"}
            두 함수 \\(f(x)=2x+1\\)에 대하여 답하시오.

            ::: {.problem num="2" type="short" space="3cm"}
            \\(f(1)\\)의 값을 구하시오.
            :::

            ::: {.problem num="3" type="short" space="3cm"}
            \\(\\left(f(2)\\right)\\)의 값을 구하시오.
            :::
            :::
            """;

    /** 필터 결과 흉내: 번호·탭·<보 기> 제목·정답 줄 같은 생성물이 붙어 있다 */
    static final String RENDERED = """
            ::: {custom-style="Answer"}
            빠른 정답 1-②
            :::

            ::: {custom-style="Problem"}
            [1.]{custom-style="ProblemNumber"} 함수 \\(f(x)=\\dfrac{1}{2}x^{2}\\)의 최솟값은? \\<보기\\>
            :::

            ::: {custom-style="Choices5"}
            ① \\(-4\\) [② \\(-3\\)]{custom-style="CorrectChoice"}
            :::

            ::: {custom-style="Answer"}
            정답 ②
            :::

            ::: {custom-style="GroupStem"}
            [[2~3]]{custom-style="ProblemNumber"} 두 함수 \\(f(x)=2x+1\\)에 대하여 답하시오.
            :::

            ::: {custom-style="Problem"}
            [2.]{custom-style="ProblemNumber"} \\(f(1)\\)의 값을 구하시오.
            :::

            ::: {custom-style="Problem"}
            [3.]{custom-style="ProblemNumber"} \\(\\left(f(2)\\right)\\)의 값을 구하시오.
            :::
            """;

    @BeforeEach
    void setUp() {
        pandoc = PandocTestSupport.pandocOrSkip(dir);
        verifier = new RoundtripVerifier(pandoc);
    }

    @Test
    void 생성물을_빼면_원고와_문제별로_일치한다() throws Exception {
        VerifyReport report = verify(MANUSCRIPT, RENDERED);

        assertThat(report.mismatches()).isEmpty();
        assertThat(report.units()).extracting(VerifyReport.UnitResult::key)
                .containsExactly("1", "묶음@2", "2", "3");
        assertThat(report.units().get(0).expectedMath()).isEqualTo(3);
    }

    @Test
    void 수식이_바뀐_문제만_불일치로_알려준다() throws Exception {
        VerifyReport report = verify(MANUSCRIPT, RENDERED.replace("x^{2}\\)의 최솟값", "x^{3}\\)의 최솟값"));

        assertThat(report.mismatches()).singleElement().satisfies(u -> {
            assertThat(u.key()).isEqualTo("1");
            assertThat(u.differences()).singleElement().satisfies(d -> {
                assertThat(d.expected()).isEqualTo("2");
                assertThat(d.actual()).isEqualTo("3");
            });
        });
    }

    @Test
    void 글자가_빠진_문제를_알려준다() throws Exception {
        VerifyReport report = verify(MANUSCRIPT, RENDERED.replace("두 함수", "함수"));

        assertThat(report.mismatches()).extracting(VerifyReport.UnitResult::key).containsExactly("묶음@2");
    }

    @Test
    void 결과에_없는_문제를_알려준다() throws Exception {
        String withoutThird = RENDERED.substring(0, RENDERED.indexOf("::: {custom-style=\"Problem\"}\n[3.]"));

        VerifyReport report = verify(MANUSCRIPT, withoutThird);

        assertThat(report.mismatches()).singleElement().satisfies(u -> {
            assertThat(u.key()).isEqualTo("3");
            assertThat(u.actual()).isNull();
        });
    }

    @Test
    void 수식_개수가_다르면_불일치다() {
        var expected = java.util.Map.of("1", new ProblemTextExtractor.Unit("⟦x⟧⟦y⟧", 2));
        var actual = java.util.Map.of("1", new ProblemTextExtractor.Unit("⟦x⟧⟦y⟧", 1));

        assertThat(RoundtripVerifier.compare("m", "d", expected, actual).allMatched()).isFalse();
    }

    private VerifyReport verify(String manuscript, String rendered) throws Exception {
        Path md = Files.writeString(dir.resolve("worksheet.md"), manuscript);
        Path renderedMd = Files.writeString(dir.resolve("rendered.md"), rendered);
        Path docx = dir.resolve("out.docx");
        pandoc.run(List.of("-f", "markdown+tex_math_single_backslash", "-o", docx.toString(), renderedMd.toString()), dir);
        return verifier.verify(md, docx, dir);
    }
}
