package com.mathworksheet.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.mathworksheet.config.AppPaths;

/**
 * 서버 PC 디스크 저장소(%APPDATA%\MathWorksheet\images). id·날짜 기준으로 나눠 선생님이 붙인 폴더 이름과 무관하다(설계서 10장).
 */
@Component
public class LocalImageStorage implements ImageStorage {

    private final Path imagesRoot;

    public LocalImageStorage(AppPaths paths) {
        this.imagesRoot = paths.originals().getParent();
    }

    @Override
    public String saveOriginal(long sourceImageId, String extension, InputStream content) {
        YearMonth now = YearMonth.now();
        String relative = "originals/%d/%02d/%d.%s".formatted(now.getYear(), now.getMonthValue(), sourceImageId, extension);
        return save(relative, content);
    }

    @Override
    public String saveFigure(long problemId, int order, InputStream content) {
        return save("figures/%d/%d_%d.png".formatted(problemId, problemId, order), content);
    }

    /** 상대 경로가 저장소 밖을 가리키면(../ 등) 거부한다 */
    @Override
    public Path resolve(String relativePath) {
        Path resolved = imagesRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(imagesRoot)) {
            throw new IllegalArgumentException("저장소 밖 경로입니다: " + relativePath);
        }
        return resolved;
    }

    private String save(String relative, InputStream content) {
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (IOException e) {
            throw new UncheckedIOException("그림을 저장할 수 없습니다: " + relative, e);
        }
    }
}
