package com.mathworksheet.worksheet.verify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComparisonNormalizerTest {

    @Test
    void 글자는_공백만_지운다() {
        assertThat(ComparisonNormalizer.text(" 함수 의\n최솟값은? ")).isEqualTo("함수의최솟값은?");
    }

    @Test
    void dfrac은_frac으로_본다() {
        assertThat(ComparisonNormalizer.math("\\dfrac{1}{3}")).isEqualTo(ComparisonNormalizer.math("\\frac{1}{3}"));
    }

    @Test
    void left와_right는_지운다() {
        assertThat(ComparisonNormalizer.math("\\left(3x^{2}-2x\\right)")).isEqualTo(ComparisonNormalizer.math("(3x^2-2x)"));
    }

    @Test
    void 글자_하나를_감싼_중괄호는_지운다() {
        assertThat(ComparisonNormalizer.math("a_{n}=x^{2}")).isEqualTo("a_n=x^2");
        assertThat(ComparisonNormalizer.math("\\sqrt{{5}}")).isEqualTo("\\sqrt5");
    }

    @Test
    void 이스케이프된_중괄호는_남긴다() {
        assertThat(ComparisonNormalizer.math("\\{1\\}")).isEqualTo("\\{1\\}");
        assertThat(ComparisonNormalizer.math("\\{a_{n}\\}")).isEqualTo("\\{a_n\\}");
    }

    @Test
    void 폭_없는_공백도_공백으로_본다() {
        assertThat(ComparisonNormalizer.text("미만\u200B")).isEqualTo("미만");
    }

    @Test
    void Word를_거친_cases는_원래_cases와_같게_본다() {
        String original = "f(x)=\\begin{cases} x+a & (x<1) \\\\ x^{2}-2 & (x \\geq 1) \\end{cases}";
        String roundtrip = "f(x) = \\left\\{ \\begin{matrix}\nx + a & (x < 1) \\\\\nx^{2} - 2 & (x \\geq 1)\n\\end{matrix} \\right.\\ ";

        assertThat(ComparisonNormalizer.math(roundtrip)).isEqualTo(ComparisonNormalizer.math(original));
    }

    @Test
    void cases가_아닌_matrix는_그대로_둔다() {
        assertThat(ComparisonNormalizer.math("\\begin{matrix}a\\end{matrix}")).isEqualTo("\\begin{matrix}a\\end{matrix}");
    }

    @Test
    void 정규화_규칙에_없는_차이는_그대로_남는다() {
        assertThat(ComparisonNormalizer.math("x^{2}")).isNotEqualTo(ComparisonNormalizer.math("x^{3}"));
        assertThat(ComparisonNormalizer.math("\\geq")).isNotEqualTo(ComparisonNormalizer.math("\\ge"));
        assertThat(ComparisonNormalizer.math("\\leftarrow")).isEqualTo("\\leftarrow");
    }
}
