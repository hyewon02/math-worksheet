package com.mathworksheet.problem.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathworksheet.common.ApiException;
import com.mathworksheet.common.PageResponse;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.problem.repository.FigureRepository;
import com.mathworksheet.problem.service.ProblemDtos.AnswerUpdate;
import com.mathworksheet.problem.service.ProblemDtos.ProblemView;
import com.mathworksheet.problem.service.ProblemDtos.SearchCondition;
import com.mathworksheet.problem.service.ProblemService;
import com.mathworksheet.storage.ImageStorage;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService service;
    private final FigureRepository figures;
    private final ImageStorage images;

    public ProblemController(ProblemService service, FigureRepository figures, ImageStorage images) {
        this.service = service;
        this.figures = figures;
        this.images = images;
    }

    /** 문제은행 목록. 예: /api/problems?textbook=쎈&type=CHOICE&q=등비수열&page=0&size=20 */
    @GetMapping("/problems")
    public PageResponse<ProblemView> search(@RequestParam(required = false) String textbook,
                                            @RequestParam(required = false) String grade,
                                            @RequestParam(required = false) ProblemType type,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(required = false) Boolean answerMissing,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.search(new SearchCondition(textbook, grade, type, tag, answerMissing, q), page, size);
    }

    @GetMapping("/problems/{id}")
    public ProblemView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/problems/{id}/answer")
    public ProblemView updateAnswer(@PathVariable Long id, @Valid @RequestBody AnswerUpdate request) {
        return service.updateAnswer(id, request);
    }

    /** 문제 카드·미리보기에 쓰는 그림 */
    @GetMapping("/figures/{marker}")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> figure(@PathVariable String marker) throws IOException {
        Path file = figures.findByMarker(marker)
                .map(f -> images.resolve(f.getFilePath()))
                .filter(Files::exists)
                .orElseThrow(() -> ApiException.notFound("그림 파일을 찾을 수 없습니다."));
        String type = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(type == null ? MediaType.IMAGE_PNG : MediaType.parseMediaType(type))
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(file));
    }
}
