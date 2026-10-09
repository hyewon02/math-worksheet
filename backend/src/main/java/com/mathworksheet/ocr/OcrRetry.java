package com.mathworksheet.ocr;

import java.nio.file.Path;
import java.time.Duration;

/**
 * OCR 재시도(설계서 5장 2단계: 실패하면 3회까지 재시도). 인증·결제·사진 오류처럼 다시 해도 안 되는 것은 바로 멈춘다.
 * 같은 사진을 반복 호출해 요금이 쌓이지 않도록 시도 횟수를 넘기지 않는다.
 */
public final class OcrRetry {

    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public static final int MAX_ATTEMPTS = 3;

    private OcrRetry() {
    }

    public static OcrResponse recognize(OcrEngine engine, Path image, Sleeper sleeper) {
        OcrException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return engine.recognize(image);
            } catch (OcrException e) {
                if (!e.isRetryable() || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                last = e;
                try {
                    sleeper.sleep(Duration.ofSeconds(2L * attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
