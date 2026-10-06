package com.mathworksheet.worksheet.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mathworksheet.common.ApiException;
import com.mathworksheet.worksheet.service.DownloadStore;
import com.mathworksheet.worksheet.service.WorksheetDtos.RefreshRequest;
import com.mathworksheet.worksheet.service.WorksheetDtos.TemplateView;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetRequest;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetSummary;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetView;
import com.mathworksheet.worksheet.service.WorksheetGenerator;
import com.mathworksheet.worksheet.service.WorksheetGenerator.GenerationResult;
import com.mathworksheet.worksheet.service.WorksheetService;

import jakarta.validation.Valid;

/**
 * 문제지 API. 내려받기는 두 단계다: POST /generate로 만들고 검사 결과를 보여준 뒤, GET /downloads/{token}/…로 받는다.
 */
@RestController
@RequestMapping("/api")
public class WorksheetController {

    static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WorksheetService service;
    private final WorksheetGenerator generator;
    private final DownloadStore downloads;

    public WorksheetController(WorksheetService service, WorksheetGenerator generator, DownloadStore downloads) {
        this.service = service;
        this.generator = generator;
        this.downloads = downloads;
    }

    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return service.templates();
    }

    @GetMapping("/worksheets")
    public List<WorksheetSummary> list() {
        return service.list();
    }

    @PostMapping("/worksheets")
    @ResponseStatus(HttpStatus.CREATED)
    public WorksheetView create(@Valid @RequestBody WorksheetRequest request) {
        return service.create(request);
    }

    @GetMapping("/worksheets/{id}")
    public WorksheetView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/worksheets/{id}")
    public WorksheetView update(@PathVariable Long id, @Valid @RequestBody WorksheetRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version이 필요합니다.");
        }
        return service.update(id, request);
    }

    @PostMapping("/worksheets/{id}/refresh")
    public WorksheetView refresh(@PathVariable Long id, @Valid @RequestBody RefreshRequest request) {
        return service.refresh(id, request);
    }

    @PostMapping("/worksheets/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public WorksheetView duplicate(@PathVariable Long id) {
        return service.duplicate(id);
    }

    @PostMapping("/worksheets/{id}/generate")
    public GenerationResult generate(@PathVariable Long id) {
        return generator.generate(id);
    }

    /** kind: student(문제지) / answers(정답지) / both(둘 다, zip) */
    @GetMapping("/downloads/{token}/{kind}")
    public ResponseEntity<?> download(@PathVariable String token, @PathVariable String kind) {
        DownloadStore.Entry entry = downloads.find(token)
                .orElseThrow(() -> ApiException.notFound("내려받을 파일이 없습니다. 만든 지 1시간이 지났다면 다시 만들어 주세요."));
        return switch (kind) {
            case "student", "answers" -> file(entry.files().get(kind));
            case "both" -> zip(entry.files().get("student"), entry.files().get("answers"));
            default -> throw ApiException.badRequest("student, answers, both 중 하나를 골라 주세요.");
        };
    }

    private static ResponseEntity<Resource> file(Path file) {
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.getFileName().toString()))
                .body(new FileSystemResource(file));
    }

    /** 문제지·정답지 두 파일이라 수십 KB다. 메모리에서 묶어 한 번에 보낸다 */
    private static ResponseEntity<byte[]> zip(Path student, Path answers) {
        String name = student.getFileName().toString().replaceFirst("\\.docx$", ".zip");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            writeZip(body, student, answers);
        } catch (IOException e) {
            throw new UncheckedIOException("zip 파일을 만들 수 없습니다", e);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(name))
                .body(body.toByteArray());
    }

    private static void writeZip(OutputStream out, Path... files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Path f : files) {
                zip.putNextEntry(new ZipEntry(f.getFileName().toString()));
                Files.copy(f, zip);
                zip.closeEntry();
            }
        }
    }

    /** 한글 파일 이름이 깨지지 않게 RFC 5987(filename*=UTF-8'')로 보낸다 */
    private static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
    }
}
