package com.mathworksheet.worksheet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mathworksheet.worksheet.docx.PlaceholderFiller;
import com.mathworksheet.worksheet.pandoc.PandocRunner;

/**
 * 원고(마크다운) → Pandoc(형식 파일 + Lua 필터) → 빈칸 채우기 → 문제지·정답지 docx(설계서 8장 "문제지 생성 순서" 2~4).
 * 정답지는 같은 원고·형식으로 {@code -M answers=true}만 켜서 한 번 더 만든다.
 */
public class WorksheetRenderer {

    static final String MANUSCRIPT_FORMAT = "markdown+tex_math_single_backslash";

    private final PandocRunner pandoc;
    private final PlaceholderFiller filler;
    private final Path luaFilter;

    public WorksheetRenderer(PandocRunner pandoc, PlaceholderFiller filler, Path luaFilter) {
        this.pandoc = pandoc;
        this.filler = filler;
        this.luaFilter = luaFilter;
    }

    /**
     * @param manuscript   원고 파일
     * @param resourceDir  원고 속 그림 경로(images/…)의 기준 폴더
     * @param template     형식 파일(reference.docx)
     * @param answers      true면 정답지
     * @param placeholders 학원명·시험제목·날짜·문제지번호
     * @param settings     형식 설정. null이면 형식 파일 옆 .json(앱에서는 DB의 Template 값을 넘긴다)
     */
    public record Request(Path manuscript, Path resourceDir, Path template, boolean answers,
                          Map<String, String> placeholders, Path output, FormatSettings settings) {

        public Request(Path manuscript, Path resourceDir, Path template, boolean answers,
                       Map<String, String> placeholders, Path output) {
            this(manuscript, resourceDir, template, answers, placeholders, output, null);
        }
    }

    /** @param leftovers 채우지 못한 빈칸 이름. 비어 있어야 정상 */
    public record Result(Path output, Set<String> leftovers) {
    }

    public Result render(Request request, Path workDir) {
        try {
            Files.createDirectories(workDir);
            Path raw = Files.createTempFile(workDir, "pandoc-", ".docx");
            try {
                pandoc.run(arguments(request, raw), workDir);
                Files.createDirectories(request.output().toAbsolutePath().getParent());
                Set<String> leftovers = filler.fill(raw, request.output(), request.placeholders());
                return new Result(request.output(), leftovers);
            } finally {
                Files.deleteIfExists(raw);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("문제지를 만들 수 없습니다: " + request.output(), e);
        }
    }

    List<String> arguments(Request request, Path raw) {
        List<String> args = new ArrayList<>(List.of(
                "-f", MANUSCRIPT_FORMAT,
                "--reference-doc=" + request.template().toAbsolutePath(),
                "--lua-filter=" + luaFilter.toAbsolutePath(),
                "--resource-path=" + request.resourceDir().toAbsolutePath()));
        FormatSettings settings = request.settings() != null ? request.settings() : FormatSettings.forTemplate(request.template());
        args.addAll(settings.pandocArguments());
        if (request.answers()) {
            args.addAll(List.of("-M", "answers=true"));
        }
        args.addAll(List.of("-o", raw.toAbsolutePath().toString(), request.manuscript().toAbsolutePath().toString()));
        return args;
    }
}
