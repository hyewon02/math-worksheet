package com.mathworksheet.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 내부 저장소 경로(10장 "데이터 위치"). 선생님이 손대지 않는 폴더이며 id·날짜 기준으로 나눈다.
 */
@Component
public class AppPaths {

    private final Path root;

    public AppPaths(AppProperties properties) {
        this.root = properties.dataDir().toAbsolutePath().normalize();
        createDirectories();
    }

    public Path root() {
        return root;
    }

    public Path db() {
        return root.resolve("db");
    }

    public Path originals() {
        return root.resolve("images").resolve("originals");
    }

    public Path figures() {
        return root.resolve("images").resolve("figures");
    }

    public Path templates() {
        return root.resolve("templates");
    }

    /** Pandoc 실행용 임시 폴더. 생성 후 삭제한다. */
    public Path work() {
        return root.resolve("work");
    }

    public Path logs() {
        return root.resolve("logs");
    }

    public Path settingsFile() {
        return root.resolve("config").resolve("settings.json");
    }

    private void createDirectories() {
        List<Path> dirs = List.of(db(), originals(), figures(), templates(), work(), logs(), settingsFile().getParent());
        try {
            for (Path dir : dirs) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("데이터 폴더를 만들 수 없습니다: " + root, e);
        }
    }
}
