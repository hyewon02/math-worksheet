package com.mathworksheet.worksheet;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.Duration;

import com.mathworksheet.worksheet.pandoc.PandocException;
import com.mathworksheet.worksheet.pandoc.PandocRunner;

/** pandoc이 필요한 테스트용. 개발 PC·CI에는 설치되어 있어야 하며, 없으면 건너뛴다. */
public final class PandocTestSupport {

    private PandocTestSupport() {
    }

    public static PandocRunner pandocOrSkip(Path workDir) {
        PandocRunner runner = new PandocRunner("pandoc", Duration.ofSeconds(60));
        boolean available;
        try {
            available = runner.version(workDir).startsWith("pandoc");
        } catch (PandocException e) {
            available = false;
        }
        assumeTrue(available, "pandoc이 설치되어 있지 않아 건너뜀");
        return runner;
    }

    /** 저장소 루트의 worksheet 폴더(테스트는 backend 폴더에서 실행된다) */
    public static Path worksheetDir() {
        return Path.of("..", "worksheet").toAbsolutePath().normalize();
    }
}
