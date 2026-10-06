package com.mathworksheet.worksheet.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.problem.service.SampleProblemImporter;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorksheetApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SampleProblemImporter importer;

    @Autowired
    ProblemRepository problems;

    List<Long> ids;
    long templateId;

    @BeforeEach
    void setUp() throws Exception {
        ids = importer.importClasspath(SampleProblemImporter.SAMPLE_01);
        JsonNode templates = read(mvc.perform(get("/api/templates")));
        templateId = templates.get(0).get("id").asLong();
    }

    @Test
    void 내장_형식이_등록되어_있다() throws Exception {
        mvc.perform(get("/api/templates"))
                .andExpect(jsonPath("$[0].name").value("내신형 2단"))
                .andExpect(jsonPath("$[0].builtIn").value(true));
    }

    @Test
    void 문제지를_만들면_번호를_매기고_스냅샷을_뜬다() throws Exception {
        mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(request("1-2 중간고사 대비", ids.subList(0, 3), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(matchesPattern("WS-\\d{4}")))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[2].number").value(3))
                .andExpect(jsonPath("$.items[0].snapshot.bodyMarkdown").value(containsString("최솟값")))
                .andExpect(jsonPath("$.items[0].outdated").value(false))
                .andExpect(jsonPath("$.date").isNotEmpty());
    }

    @Test
    void 문제은행에서_고친_문제는_수정됨으로_보이고_갱신해야_바뀐다() throws Exception {
        JsonNode ws = create(ids.subList(0, 2));
        Long problemId = ids.get(0);
        long problemVersion = problems.findById(problemId).orElseThrow().getVersion();
        mvc.perform(patch("/api/problems/{id}/answer", problemId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"⑤\",\"version\":" + problemVersion + "}")).andExpect(status().isOk());

        JsonNode reloaded = read(mvc.perform(get("/api/worksheets/{id}", ws.get("id").asLong())));
        org.assertj.core.api.Assertions.assertThat(reloaded.at("/items/0/outdated").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(reloaded.at("/items/0/snapshot/answer").asText()).isEqualTo("②");

        mvc.perform(post("/api/worksheets/{id}/refresh", ws.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + reloaded.get("version").asLong() + "}"))
                .andExpect(jsonPath("$.items[0].outdated").value(false))
                .andExpect(jsonPath("$.items[0].snapshot.answer").value("⑤"));
    }

    @Test
    void 순서를_바꾸고_빼면_번호를_다시_매긴다() throws Exception {
        JsonNode ws = create(ids.subList(0, 3));
        long first = ws.at("/items/0/itemId").asLong();
        long third = ws.at("/items/2/itemId").asLong();
        String body = json.writeValueAsString(Map.of("title", "바꾼 제목", "templateId", templateId,
                "version", ws.get("version").asLong(),
                "items", List.of(Map.of("itemId", third), Map.of("itemId", first, "space", "LARGE"),
                        Map.of("problemId", ids.get(4)))));

        mvc.perform(put("/api/worksheets/{id}", ws.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바꾼 제목"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].itemId").value(third))
                .andExpect(jsonPath("$.items[1].space").value("LARGE"))
                .andExpect(jsonPath("$.items[2].problemId").value(ids.get(4)))
                .andExpect(jsonPath("$.version").value(ws.get("version").asLong() + 1));
    }

    @Test
    void 다른_분이_먼저_고친_문제지는_409로_알린다() throws Exception {
        JsonNode ws = create(ids.subList(0, 2));
        String body = request("제목", ids.subList(0, 1), ws.get("version").asLong() + 5);

        mvc.perform(put("/api/worksheets/{id}", ws.get("id").asLong()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void 같은_묶음의_문제가_떨어져_있으면_거절한다() throws Exception {
        // ids 8, 9가 묶음 9~10
        mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(request("묶음", List.of(ids.get(8), ids.get(0), ids.get(9)), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("같은 묶음의 문제는 붙어 있어야 합니다")));
    }

    @Test
    void 문제은행에_없는_문제나_같은_문제를_두_번_담을_수_없다() throws Exception {
        Problem reviewing = problems.save(new Problem(ProblemType.SHORT, "검수 중", "교재", "고1", 1, "1"));

        mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(request("x", List.of(reviewing.getId()), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("문제은행에 없는 문제")));
        mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(request("x", List.of(ids.get(0), ids.get(0)), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("같은 문제를 두 번 담을 수 없습니다."));
    }

    @Test
    void 제목이_없으면_알려준다() throws Exception {
        mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(request(" ", ids.subList(0, 1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제목을 입력해 주세요."));
    }

    @Test
    void 복제하면_새_번호와_같은_문항을_가진다() throws Exception {
        JsonNode ws = create(ids.subList(0, 3));

        mvc.perform(post("/api/worksheets/{id}/duplicate", ws.get("id").asLong()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.not(ws.get("number").asText())))
                .andExpect(jsonPath("$.title").value("시험 (복사본)"))
                .andExpect(jsonPath("$.duplicatedFromId").value(ws.get("id").asLong()))
                .andExpect(jsonPath("$.items", hasSize(3)));
    }

    @Test
    void 저장된_문제지_목록에_수정된_문항_수가_보인다() throws Exception {
        create(ids.subList(0, 2));

        mvc.perform(get("/api/worksheets"))
                .andExpect(jsonPath("$[0].itemCount").value(2))
                .andExpect(jsonPath("$[0].outdatedCount").value(0))
                .andExpect(jsonPath("$[0].templateName").value("내신형 2단"));
    }

    private JsonNode create(List<Long> problemIds) throws Exception {
        return read(mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                .content(request("시험", problemIds, null))).andExpect(status().isCreated()));
    }

    private String request(String title, List<Long> problemIds, Long version) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        problemIds.forEach(id -> items.add(Map.of("problemId", id)));
        var body = new java.util.HashMap<String, Object>(Map.of("title", title, "templateId", templateId, "items", items));
        if (version != null) {
            body.put("version", version);
        }
        return json.writeValueAsString(body);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
