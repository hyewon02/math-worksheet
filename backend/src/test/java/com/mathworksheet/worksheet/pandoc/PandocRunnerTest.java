package com.mathworksheet.worksheet.pandoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mathworksheet.worksheet.PandocTestSupport;

class PandocRunnerTest {

    @TempDir
    Path dir;

    @Test
    void 한글_원고를_변환하고_stdout을_돌려준다() throws Exception {
        PandocRunner pandoc = PandocTestSupport.pandocOrSkip(dir);
        Path md = Files.writeString(dir.resolve("원고.md"), "함수 \\(f(x)=x^{2}\\)의 최솟값은?");

        String json = pandoc.run(List.of("-f", "markdown+tex_math_single_backslash", "-t", "json", md.toString()), dir);

        assertThat(json).contains("함수").contains("f(x)=x^{2}");
    }

    @Test
    void 실패하면_종료_코드와_오류_내용을_담아_던진다() {
        PandocRunner pandoc = PandocTestSupport.pandocOrSkip(dir);

        assertThatThrownBy(() -> pandoc.run(List.of("없는파일.md"), dir))
                .isInstanceOfSatisfying(PandocException.class, e -> {
                    assertThat(e.exitCode()).isNotZero();
                    assertThat(e.stderr()).isNotBlank();
                });
    }

    @Test
    void 실행_파일이_없으면_알아볼_수_있는_오류를_던진다() {
        PandocRunner missing = new PandocRunner("pandoc-없음", Duration.ofSeconds(5));

        assertThatThrownBy(() -> missing.version(dir))
                .isInstanceOf(PandocException.class)
                .hasMessageContaining("pandoc을 실행할 수 없습니다");
    }

    @Test
    void 실행_후_임시_출력_파일을_남기지_않는다() throws Exception {
        PandocRunner pandoc = PandocTestSupport.pandocOrSkip(dir);
        pandoc.version(dir);

        try (var files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }
}
