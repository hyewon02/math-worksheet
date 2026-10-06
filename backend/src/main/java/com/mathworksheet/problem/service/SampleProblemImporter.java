package com.mathworksheet.problem.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ProblemGroup;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.problem.domain.Tag;
import com.mathworksheet.problem.repository.ProblemGroupRepository;
import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.problem.repository.TagRepository;
import com.mathworksheet.storage.ImageStorage;

/**
 * 개발용 샘플 문제를 문제은행에 넣는다(worksheet/samples/*.problems.json). OCR·검수 화면이 생기기 전까지
 * 문제지 생성 API를 실제 데이터로 시험하기 위한 것이며, 넣은 문제는 검수 완료 상태다.
 */
@Service
public class SampleProblemImporter {

    public static final String SAMPLE_01 = "worksheet/samples/sample-01.problems.json";

    private final ProblemRepository problems;
    private final ProblemGroupRepository groups;
    private final TagRepository tags;
    private final ImageStorage images;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SampleProblemImporter(ProblemRepository problems, ProblemGroupRepository groups, TagRepository tags,
                                 ImageStorage images) {
        this.problems = problems;
        this.groups = groups;
        this.tags = tags;
        this.images = images;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SampleFile(String textbook, String grade, List<SampleItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SampleItem(Integer page, String number, ProblemType type, String body, List<String> choices,
                      String answer, List<String> tags, List<SampleFigure> figures, SampleGroup group) {
    }

    record SampleGroup(String stem, List<SampleItem> problems) {
    }

    record SampleFigure(String marker, String file, int displayWidth) {
    }

    /** @return 넣은 문제 id(파일 순서) */
    @Transactional
    public List<Long> importClasspath(String resource) {
        String baseDir = resource.substring(0, resource.lastIndexOf('/') + 1);
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            SampleFile file = objectMapper.readValue(in, SampleFile.class);
            List<Long> ids = new java.util.ArrayList<>();
            for (SampleItem item : file.items()) {
                if (item.group() == null) {
                    ids.add(save(item, file, null, 0, baseDir).getId());
                    continue;
                }
                ProblemGroup group = groups.save(new ProblemGroup(item.group().stem()));
                int order = 1;
                for (SampleItem child : item.group().problems()) {
                    ids.add(save(child, file, group, order++, baseDir).getId());
                }
            }
            return ids;
        } catch (IOException e) {
            throw new UncheckedIOException("샘플 문제를 읽을 수 없습니다: " + resource, e);
        }
    }

    private Problem save(SampleItem item, SampleFile file, ProblemGroup group, int order, String baseDir) throws IOException {
        Problem problem = new Problem(item.type(), item.body(), file.textbook(), file.grade(), item.page(), item.number());
        if (group != null) {
            problem.joinGroup(group, order);
        }
        if (item.choices() != null) {
            item.choices().forEach(problem::addChoice);
        }
        if (item.answer() != null) {
            problem.enterAnswer(item.answer());
        }
        if (item.tags() != null) {
            for (String name : item.tags()) {
                problem.addTag(tags.findByName(name).orElseGet(() -> tags.save(new Tag(name))));
            }
        }
        problem.completeReview();
        problems.save(problem);

        if (item.figures() != null) {
            int figureOrder = 1;
            for (SampleFigure figure : item.figures()) {
                try (InputStream png = new ClassPathResource(baseDir + figure.file()).getInputStream()) {
                    String path = images.saveFigure(problem.getId(), figureOrder++, png);
                    problem.addFigure(figure.marker(), path, figure.displayWidth());
                }
            }
        }
        return problem;
    }
}
