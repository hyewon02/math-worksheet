package com.mathworksheet.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mathworksheet.problem.domain.SearchText;

class SearchTextTest {

    @Test
    void LaTeX_명령어와_기호와_공백을_걷어_낸다() {
        assertThat(SearchText.of("등비수열 \\(\\{a_{n}\\}\\)의 \\(\\frac{1}{2}\\)배"))
                .isEqualTo("등비수열(an)의(12)배");
    }

    @Test
    void 그림_표시와_구역_표시는_검색_대상이_아니다() {
        assertThat(SearchText.of("본문\n\n[그림:p1_1]\n\n::: bogi\nㄱ. 참\n:::")).isEqualTo("본문ㄱ.참");
    }

    @Test
    void 영문은_소문자로_맞춘다() {
        assertThat(SearchText.normalize("ABC 함수")).isEqualTo(SearchText.normalize("abc함수"));
    }
}
