package com.mathworksheet.ocr;

/**
 * OCR 호출 실패. retryable=false(인증·결제 오류)는 재시도하지 않고 키 확인 안내로 보낸다(5장).
 */
public class OcrException extends RuntimeException {

    private final boolean retryable;

    public OcrException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
