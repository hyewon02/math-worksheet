package com.mathworksheet.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathworksheet.problem.repository.FigureRepository;
import com.mathworksheet.problem.service.SampleProblemImporter;
import com.mathworksheet.storage.ImageStorage;
import com.mathworksheet.worksheet.PandocTestSupport;

/**
 * 끝에서 끝까지: 문제은행(DB) → 문제지 저장 → 생성(원고 조립·Pandoc·빈칸·역변환 비교) → 내려받기.
 * pandoc을 실제로 실행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorksheetGenerationTest {

    @TempDir
    Path tmp;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SampleProblemImporter importer;

    @Autowired
    FigureRepository figures;

    @Autowired
    ImageStorage images;

    long worksheetId;
    String number;

    @BeforeEach
    void setUp() throws Exception {
        PandocTestSupport.pandocOrSkip(tmp);
        List<Long> ids = importer.importClasspath(SampleProblemImporter.SAMPLE_01);
        long templateId = read(mvc.perform(get("/api/templates")).andReturn().getResponse()).get(0).get("id").asLong();
        List<Map<String, Object>> items = new ArrayList<>();
        ids.forEach(id -> items.add(Map.of("problemId", id)));
        JsonNode ws = read(mvc.perform(post("/api/worksheets").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "율현중 1-2 중간고사", "templateId", templateId, "items", items))))
                .andExpect(status().isCreated()).andReturn().getResponse());
        worksheetId = ws.get("id").asLong();
        number = ws.get("number").asText();
    }

    @Test
    void 문제은행의_문제로_만든_문제지와_정답지가_역변환_비교를_통과한다() throws Exception {
        JsonNode result = generate();

        assertThat(result.get("ready").asBoolean()).isTrue();
        assertThat(result.at("/student/matched").asBoolean()).isTrue();
        assertThat(result.at("/student/total").asInt()).isEqualTo(13);
        assertThat(result.at("/answers/matched").asBoolean()).isTrue();
        assertThat(result.get("missingFigures")).isEmpty();
        assertThat(result.get("leftovers")).isEmpty();
    }

    @Test
    void 문제지를_내려받으면_머리말에_제목과_번호가_채워져_있다() throws Exception {
        String token = generate().get("token").asText();

        MockHttpServletResponse student = mvc.perform(get("/api/downloads/{token}/student", token))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(student.getContentType()).startsWith("application/vnd.openxmlformats-officedocument");
        assertThat(student.getHeader("Content-Disposition"))
                .contains("filename*=UTF-8''").contains(java.net.URLEncoder.encode(number, StandardCharsets.UTF_8));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(student.getContentAsByteArray()))) {
            String headers = String.join(" ", doc.getHeaderList().stream().map(h -> h.getText()).toList());
            String footers = String.join(" ", doc.getFooterList().stream().map(f -> f.getText()).toList());
            assertThat(headers).contains("율현중 1-2 중간고사").contains("테스트학원").doesNotContain("{{");
            assertThat(footers).contains(number);
        }

        MockHttpServletResponse answers = mvc.perform(get("/api/downloads/{token}/answers", token)).andReturn().getResponse();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(answers.getContentAsByteArray()))) {
            assertThat(String.join(" ", doc.getHeaderList().stream().map(h -> h.getText()).toList()))
                    .contains("율현중 1-2 중간고사 (정답)");
        }
    }

    @Test
    void 둘_다_받으면_zip에_문제지와_정답지가_들어_있다() throws Exception {
        String token = generate().get("token").asText();

        byte[] zip = mvc.perform(get("/api/downloads/{token}/both", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        assertThat(names).containsExactly(number + " 율현중 1-2 중간고사.docx", number + " 율현중 1-2 중간고사 (정답).docx");
    }

    @Test
    void 그림_파일이_없으면_누락으로_알리되_파일은_만든다() throws Exception {
        Path png = images.resolve(figures.findByMarker("sample-01-fig1").orElseThrow().getFilePath());
        Files.delete(png);

        JsonNode result = generate();

        assertThat(result.get("ready").asBoolean()).isFalse();
        assertThat(result.get("missingFigures").get(0).asText()).isEqualTo("sample-01-fig1");
        mvc.perform(get("/api/downloads/{token}/student", result.get("token").asText())).andExpect(status().isOk());
    }

    @Test
    void 없는_토큰은_다시_만들라고_알린다() throws Exception {
        mvc.perform(get("/api/downloads/{token}/student", "없는-토큰"))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("다시 만들어 주세요")));
    }

    private JsonNode generate() throws Exception {
        return read(mvc.perform(post("/api/worksheets/{id}/generate", worksheetId))
                .andExpect(status().isOk()).andReturn().getResponse());
    }

    private JsonNode read(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }
}
