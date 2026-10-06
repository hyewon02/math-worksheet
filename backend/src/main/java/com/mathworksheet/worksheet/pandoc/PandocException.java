package com.mathworksheet.worksheet.pandoc;

/**
 * Pandoc 실행 실패. stderr를 그대로 담아 로그로 원인을 볼 수 있게 한다.
 */
public class PandocException extends RuntimeException {

    private final int exitCode;
    private final String stderr;

    public PandocException(String message, int exitCode, String stderr, Throwable cause) {
        super(message + (stderr == null || stderr.isBlank() ? "" : "\n" + stderr.strip()), cause);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /** 시간 초과·실행 파일 없음처럼 종료 코드가 없으면 -1 */
    public int exitCode() {
        return exitCode;
    }

    public String stderr() {
        return stderr;
    }
}
