package com.mathworksheet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import com.mathworksheet.config.AppPaths;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MathWorksheetApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AppPaths paths;

    @Test
    void 헬스체크가_응답한다() {
        String body = rest.getForObject("/api/health", String.class);
        assertThat(body).contains("\"status\":\"ok\"");
    }

    @Test
    void 데이터_폴더가_만들어진다() {
        assertThat(paths.figures()).isDirectory();
        assertThat(paths.work()).isDirectory();
        assertThat(paths.settingsFile().getParent()).isDirectory();
    }
}
