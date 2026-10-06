package com.mathworksheet.worksheet.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mathworksheet.config.AppPaths;
import com.mathworksheet.config.AppProperties;
import com.mathworksheet.worksheet.FormatSettings;
import com.mathworksheet.worksheet.WorksheetRenderer;
import com.mathworksheet.worksheet.docx.PlaceholderFiller;
import com.mathworksheet.worksheet.domain.Template;
import com.mathworksheet.worksheet.domain.Worksheet;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Item;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Layout;
import com.mathworksheet.worksheet.manuscript.ManuscriptAssembler.Manuscript;
import com.mathworksheet.worksheet.pandoc.PandocException;
import com.mathworksheet.worksheet.verify.RoundtripVerifier;
import com.mathworksheet.worksheet.verify.VerifyReport;
import com.mathworksheet.storage.ImageStorage;

/**
 * 문제지 생성(설계서 5장 7~8단계): 스냅샷 → 원고 조립 → Pandoc → 빈칸 채우기 → 역변환 비교.
 * <p>
 * DB는 짧은 트랜잭션에서 읽고 끝내며, 몇 초 걸리는 Pandoc 실행은 트랜잭션 밖에서 한다(커넥션을 오래 잡지 않기 위해).
 * 결과는 {@link DownloadStore}에 잠깐 두고 토큰을 돌려준다. 불일치·그림 누락이 있어도 내려받기를 막지 않는다(설계서 13장).
 */
@Service
public class WorksheetGenerator {

    private static final Logger log = LoggerFactory.getLogger(WorksheetGenerator.class);
    static final int DIFFERENCES_PER_PROBLEM = 5;

    private final WorksheetService worksheets;
    private final ManuscriptAssembler assembler = new ManuscriptAssembler();
    private final WorksheetRenderer renderer;
    private final RoundtripVerifier verifier;
    private final DownloadStore downloads;
    private final ImageStorage images;
    private final AppPaths paths;
    private final AppProperties properties;
    private final TransactionTemplate readOnly;

    public WorksheetGenerator(WorksheetService worksheets, WorksheetRenderer renderer, RoundtripVerifier verifier,
                              DownloadStore downloads, ImageStorage images, AppPaths paths, AppProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.worksheets = worksheets;
        this.renderer = renderer;
        this.verifier = verifier;
        this.downloads = downloads;
        this.images = images;
        this.paths = paths;
        this.properties = properties;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    // ---------- 결과 ----------

    public record Mismatch(String key, int expectedMath, int actualMath, List<VerifyReport.Difference> differences) {
    }

    /** @param total 비교한 단위 수(문제 + 묶음 공통 지문) */
    public record VerifySummary(boolean matched, int total, List<Mismatch> mismatches) {

        static VerifySummary of(VerifyReport report) {
            return new VerifySummary(report.allMatched(), report.units().size(), report.mismatches().stream()
                    .map(u -> new Mismatch(u.key(), u.expectedMath(), u.actualMath(),
                            u.differences().stream().limit(DIFFERENCES_PER_PROBLEM).toList()))
                    .toList());
        }
    }

    /**
     * @param token          내려받기 토큰(1시간 유효)
     * @param missingFigures 그림 파일이 없거나 본문 표시와 짝이 맞지 않는 그림
     * @param leftovers      채우지 못한 빈칸
     * @param ready          모두 정상. false면 화면에 차이를 보여주고 "확인했음, 내려받기"를 받는다
     */
    public record GenerationResult(String token, String number, VerifySummary student, VerifySummary answers,
                                   Set<String> missingFigures, Set<String> leftovers, boolean ready) {
    }

    /** 트랜잭션 안에서 꺼내 둔 생성 재료 */
    record Prepared(Long id, String number, String title, LocalDate date, Path templateFile, FormatSettings settings,
                    Layout layout, List<Item> items) {
    }

    // ---------- 생성 ----------

    public GenerationResult generate(Long worksheetId) {
        Prepared p = readOnly.execute(status -> prepare(worksheetId));
        Manuscript manuscript = assembler.assemble(p.items(), p.layout());

        String token = downloads.open(p.id());
        Path dir = downloads.dir(token);
        Path work = dir.resolve("work");
        try {
            Set<String> missing = new LinkedHashSet<>(manuscript.unknownMarkers());
            copyFigures(manuscript.figureFiles(), work, missing);
            Path md = work.resolve("worksheet.md");
            Files.writeString(md, manuscript.markdown());

            String baseName = fileName(p.number() + " " + p.title());
            Path student = dir.resolve(baseName + ".docx");
            Path answers = dir.resolve(baseName + " (정답).docx");
            Set<String> leftovers = new LinkedHashSet<>();
            leftovers.addAll(render(p, md, work, false, student));
            leftovers.addAll(render(p, md, work, true, answers));

            VerifySummary studentCheck = VerifySummary.of(verifier.verify(md, student, work));
            VerifySummary answersCheck = VerifySummary.of(verifier.verify(md, answers, work));
            deleteTree(work);

            downloads.attach(token, "student", student);
            downloads.attach(token, "answers", answers);
            boolean ready = studentCheck.matched() && answersCheck.matched() && missing.isEmpty() && leftovers.isEmpty();
            if (!ready) {
                log.warn("문제지 {} 생성 검사 불일치: 학생용 {}건, 정답지 {}건, 그림 누락 {}, 남은 빈칸 {}", p.number(),
                        studentCheck.mismatches().size(), answersCheck.mismatches().size(), missing, leftovers);
            }
            return new GenerationResult(token, p.number(), studentCheck, answersCheck, missing, leftovers, ready);
        } catch (IOException e) {
            downloads.discard(token);
            throw new UncheckedIOException("문제지를 만들 수 없습니다", e);
        } catch (RuntimeException e) {
            downloads.discard(token);
            throw e;
        }
    }

    private Prepared prepare(Long id) {
        Worksheet w = worksheets.find(id);
        Template t = w.getTemplate();
        List<Item> items = w.getItems().stream().map(i -> new Item(i.getSnapshot(), i.getSpaceSize())).toList();
        return new Prepared(w.getId(), w.getNumber(), w.getTitle(), w.getDate(),
                paths.templates().resolve(t.getFilePath()), t.toSettings(),
                new Layout(t::baseSpace, t.getColumnWidth()), items);
    }

    /** 정답지는 같은 원고에 정답 표시를 켜고, 머리말 제목 뒤에 "(정답)"을 붙인다(설계서 8장 "정답지") */
    private Set<String> render(Prepared p, Path md, Path work, boolean answers, Path output) {
        Map<String, String> values = Map.of(
                PlaceholderFiller.ACADEMY, properties.academyName(),
                PlaceholderFiller.TITLE, answers ? p.title() + " (정답)" : p.title(),
                PlaceholderFiller.DATE, p.date().toString(),
                PlaceholderFiller.NUMBER, p.number());
        var request = new WorksheetRenderer.Request(md, work, p.templateFile(), answers, values, output, p.settings());
        try {
            return renderer.render(request, work).leftovers();
        } catch (PandocException first) {
            // 설계서 5장 7단계: Pandoc 실패는 자동 재시도 1회 후 오류 표시
            log.warn("Pandoc 실패, 한 번 더 시도: {}", first.getMessage());
            return renderer.render(request, work).leftovers();
        }
    }

    /** 원고가 참조하는 그림을 작업 폴더 images/로 복사한다. 파일이 없으면 누락으로 기록한다(설계서 9장 "누락 검사") */
    private void copyFigures(Map<String, String> figureFiles, Path work, Set<String> missing) throws IOException {
        Files.createDirectories(work.resolve("images"));
        for (Map.Entry<String, String> f : figureFiles.entrySet()) {
            Path source = images.resolve(f.getValue());
            if (Files.exists(source)) {
                Files.copy(source, work.resolve(f.getKey()));
            } else {
                missing.add(f.getKey().replaceFirst("^images/", "").replaceFirst("\\.[^.]+$", ""));
            }
        }
    }

    /** 제목에 파일 이름으로 못 쓰는 글자가 있으면 바꾼다(파일 이름만, 문제지 머리말은 그대로) */
    static String fileName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return safe.isEmpty() ? "문제지" : safe;
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
