package com.mathworksheet.worksheet.manuscript;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ManuscriptEscaperTest {

    @Test
    void 수식_밖의_꺾쇠에만_역슬래시를_붙인다() {
        assertThat(ManuscriptEscaper.escape("<보기>에서 \\(a>0\\)이고 \\(b<1\\)"))
                .isEqualTo("\\<보기\\>에서 \\(a>0\\)이고 \\(b<1\\)");
    }

    @Test
    void 줄_수식_안도_건드리지_않는다() {
        String display = "\\[f(x)=\\begin{cases} x & (x<1) \\\\ 1 & (x \\geq 1) \\end{cases}\\] 이고 a<b";

        assertThat(ManuscriptEscaper.escape(display))
                .isEqualTo("\\[f(x)=\\begin{cases} x & (x<1) \\\\ 1 & (x \\geq 1) \\end{cases}\\] 이고 a\\<b");
    }

    @Test
    void 이미_이스케이프된_꺾쇠는_두_번_붙이지_않는다() {
        assertThat(ManuscriptEscaper.escape("\\<보기\\>")).isEqualTo("\\<보기\\>");
    }

    @Test
    void 꺾쇠가_없으면_한_글자도_바꾸지_않는다() {
        String body = "  함수 \\(f(x)\\)의  최솟값은?\n| 표 | \\(1\\) |\n::: bogi\nㄱ. 참\n:::";

        assertThat(ManuscriptEscaper.escape(body)).isEqualTo(body);
    }
}
