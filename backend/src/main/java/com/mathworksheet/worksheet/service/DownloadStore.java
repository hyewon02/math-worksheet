package com.mathworksheet.worksheet.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mathworksheet.config.AppPaths;

/**
 * 만든 문제지·정답지를 잠깐 보관한다. 역변환 비교가 불일치면 선생님이 차이를 보고 "확인했음, 내려받기"를 누를 때까지
 * 기다려야 하므로(설계서 8장 "검증"), 만들기와 내려받기를 두 요청으로 나누고 그 사이 파일을 여기 둔다.
 * 1시간이 지나면 지운다. 서버가 다시 켜지면 남은 파일도 지운다.
 */
@Component
public class DownloadStore {

    private static final Logger log = LoggerFactory.getLogger(DownloadStore.class);
    static final Duration TTL = Duration.ofHours(1);

    /** @param files 내려받을 파일: "student"·"answers" → 파일(이름이 곧 내려받는 파일 이름) */
    public record Entry(Long worksheetId, Path dir, Instant createdAt, Map<String, Path> files) {
    }

    private final Path root;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public DownloadStore(AppPaths paths) {
        this(paths.work().resolve("downloads"), Clock.systemUTC());
    }

    DownloadStore(Path root, Clock clock) {
        this.root = root;
        this.clock = clock;
        deleteTree(root);
    }

    /** 새 보관 폴더를 만들고 토큰을 돌려준다 */
    public String open(Long worksheetId) {
        String token = UUID.randomUUID().toString();
        Path dir = root.resolve(token);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("작업 폴더를 만들 수 없습니다", e);
        }
        entries.put(token, new Entry(worksheetId, dir, clock.instant(), new ConcurrentHashMap<>()));
        return token;
    }

    public void attach(String token, String kind, Path file) {
        entries.get(token).files().put(kind, file);
    }

    public Path dir(String token) {
        return entries.get(token).dir();
    }

    public Optional<Entry> find(String token) {
        Entry entry = entries.get(token);
        if (entry == null || expired(entry)) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void discard(String token) {
        Entry entry = entries.remove(token);
        if (entry != null) {
            deleteTree(entry.dir());
        }
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cleanUp() {
        entries.entrySet().stream().filter(e -> expired(e.getValue())).map(Map.Entry::getKey).toList()
                .forEach(this::discard);
    }

    private boolean expired(Entry entry) {
        return entry.createdAt().plus(TTL).isBefore(clock.instant());
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("임시 파일을 지우지 못함: {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("임시 폴더를 지우지 못함: {}", dir);
        }
    }
}
