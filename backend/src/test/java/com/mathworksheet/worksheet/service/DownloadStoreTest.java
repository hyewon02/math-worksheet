package com.mathworksheet.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadStoreTest {

    @TempDir
    Path root;

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-06T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void 한_시간이_지나면_찾을_수_없고_정리하면_폴더도_지운다() throws Exception {
        MovableClock clock = new MovableClock();
        DownloadStore store = new DownloadStore(root.resolve("downloads"), clock);
        String token = store.open(1L);
        Path dir = store.dir(token);
        Files.writeString(dir.resolve("a.docx"), "x");

        clock.now = clock.now.plus(Duration.ofMinutes(59));
        assertThat(store.find(token)).isPresent();

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertThat(store.find(token)).isEmpty();
        store.cleanUp();
        assertThat(dir).doesNotExist();
    }

    @Test
    void 서버가_다시_켜지면_남은_파일을_지운다() throws Exception {
        Path leftover = Files.createDirectories(root.resolve("downloads/old-token"));
        Files.writeString(leftover.resolve("a.docx"), "x");

        new DownloadStore(root.resolve("downloads"), Clock.systemUTC());

        assertThat(leftover).doesNotExist();
    }
}
