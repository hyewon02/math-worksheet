package com.mathworksheet.problem;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.problem.service.SampleProblemImporter;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProblemApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SampleProblemImporter importer;

    @Autowired
    ProblemRepository problems;

    List<Long> ids;

    @BeforeEach
    void setUp() {
        ids = importer.importClasspath(SampleProblemImporter.SAMPLE_01);
        // 검수 중인 문제는 문제은행에 보이면 안 된다
        problems.save(new Problem(ProblemType.SHORT, "검수 중인 최솟값 문제", "샘플 교재", "고1", 1, "1"));
    }

    @Test
    void 검수_완료된_문제만_최근_등록_순으로_보인다() throws Exception {
        mvc.perform(get("/api/problems").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content[0].id").value(ids.get(11)));
    }

    @Test
    void 유형_태그_정답_미입력으로_거른다() throws Exception {
        mvc.perform(get("/api/problems").param("type", "SHORT")).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/problems").param("tag", "합성함수")).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/problems").param("answerMissing", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].originalNumber").value("1203"));
    }

    @Test
    void 본문_검색은_띄어쓰기와_LaTeX_명령어를_무시한다() throws Exception {
        mvc.perform(get("/api/problems").param("q", "최솟 값")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/problems").param("q", "무리수")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/problems").param("q", "sqrt")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 쪽_단위로_나눠_보여준다() throws Exception {
        mvc.perform(get("/api/problems").param("size", "5").param("page", "2"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void 문제_하나를_보기_그림_묶음과_함께_돌려준다() throws Exception {
        mvc.perform(get("/api/problems/{id}", ids.get(7)))
                .andExpect(jsonPath("$.choices", hasSize(5)))
                .andExpect(jsonPath("$.figures[0].url").value("/api/figures/sample-01-fig1"));
        mvc.perform(get("/api/problems/{id}", ids.get(8)))
                .andExpect(jsonPath("$.group.stemMarkdown", containsString("두 함수")));
    }

    @Test
    void 그림을_내려준다() throws Exception {
        mvc.perform(get("/api/figures/sample-01-fig1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/figures/없는그림")).andExpect(status().isNotFound());
    }

    @Test
    void 정답을_고치면_version이_오른다() throws Exception {
        Long id = ids.get(10);
        long version = problems.findById(id).orElseThrow().getVersion();

        mvc.perform(patch("/api/problems/{id}/answer", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"③\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("③"))
                .andExpect(jsonPath("$.answerStatus").value("ENTERED"))
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void 다른_분이_먼저_고쳤으면_409로_알린다() throws Exception {
        Long id = ids.get(0);
        long stale = problems.findById(id).orElseThrow().getVersion() - 1;

        mvc.perform(patch("/api/problems/{id}/answer", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"③\",\"version\":" + stale + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("다른 분이 먼저 수정했습니다")));
    }

    @Test
    void 객관식_정답은_동그라미_번호만_받는다() throws Exception {
        Long id = ids.get(0);
        long version = problems.findById(id).orElseThrow().getVersion();

        mvc.perform(patch("/api/problems/{id}/answer", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"3\",\"version\":" + version + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("객관식 정답은 ①~⑤ 중 하나를 골라 주세요."));
    }

    @Test
    void 나중에_입력으로_바꿀_수_있다() throws Exception {
        Long id = ids.get(4);
        long version = problems.findById(id).orElseThrow().getVersion();

        mvc.perform(patch("/api/problems/{id}/answer", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"later\":true,\"version\":" + version + "}"))
                .andExpect(jsonPath("$.answerStatus").value("LATER"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }
}
