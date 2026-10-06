package com.mathworksheet.worksheet.pandoc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * pandoc 실행기(설계서 8장 "Pandoc 실행"). 개발 PC는 PATH의 pandoc, 배포본은 설치 폴더의 pandoc.exe를 쓴다.
 * <p>
 * stdout·stderr를 작업 폴더의 파일로 돌려받는다. 파이프로 읽으면 출력이 큰 경우 버퍼가 차서 프로세스가 멈출 수 있다.
 * Spring에 의존하지 않아 CLI와 테스트에서도 그대로 쓴다.
 */
public class PandocRunner {

    private final String executable;
    private final Duration timeout;

    public PandocRunner(String executable, Duration timeout) {
        this.executable = executable;
        this.timeout = timeout;
    }

    /**
     * pandoc을 실행하고 stdout을 돌려준다(pandoc의 입출력 인코딩은 항상 UTF-8).
     *
     * @param workDir 실행 위치. 상대 경로 인자와 그림 경로의 기준이며 stdout·stderr 임시 파일도 여기 생긴다
     */
    public String run(List<String> args, Path workDir) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);

        Path out = null;
        Path err = null;
        try {
            Files.createDirectories(workDir);
            out = Files.createTempFile(workDir, "pandoc-", ".out");
            err = Files.createTempFile(workDir, "pandoc-", ".err");
            Process process = new ProcessBuilder(command)
                    .directory(workDir.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile())
                    .start();

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new PandocException("pandoc이 " + timeout.toSeconds() + "초 안에 끝나지 않았습니다", -1, readStderr(err), null);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new PandocException("pandoc 실행 실패(종료 코드 " + exitCode + ")", exitCode, readStderr(err), null);
            }
            return read(out);
        } catch (IOException e) {
            throw new PandocException("pandoc을 실행할 수 없습니다: " + executable, -1, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PandocException("pandoc 실행이 중단되었습니다", -1, null, e);
        } finally {
            deleteQuietly(out);
            deleteQuietly(err);
        }
    }

    /** 첫 줄(예: "pandoc 3.12"). 설치 확인용 */
    public String version(Path workDir) {
        return run(List.of("--version"), workDir).lines().findFirst().orElse("").strip();
    }

    private static java.io.File nullDevice() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new java.io.File(windows ? "NUL" : "/dev/null");
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * stderr는 UTF-8이 아닐 수 있다. Windows에서 pandoc 오류 메시지 속 파일 이름이 콘솔 코드 페이지(CP949)로 나온다.
     * UTF-8로 읽다 깨지면 시스템 기본 인코딩으로 다시 읽는다.
     */
    private static String readStderr(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                return new String(bytes, java.nio.charset.Charset.forName(
                        System.getProperty("native.encoding", "UTF-8"), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return "(오류 내용을 읽을 수 없음: " + e.getMessage() + ")";
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 임시 파일이라 남아도 작업 폴더 정리 때 지워진다
        }
    }
}
