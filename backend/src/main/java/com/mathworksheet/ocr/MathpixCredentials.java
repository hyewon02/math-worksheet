package com.mathworksheet.ocr;

/**
 * Mathpix app_id / app_key. 로그·화면에는 {@link #maskedKey()}만 쓴다(10장 "노출 방지").
 */
public record MathpixCredentials(String appId, String appKey) {

    public boolean isComplete() {
        return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }

    /** 화면 표시용: ••••1234 */
    public String maskedKey() {
        if (appKey == null || appKey.length() < 4) {
            return "••••";
        }
        return "••••" + appKey.substring(appKey.length() - 4);
    }

    @Override
    public String toString() {
        return "MathpixCredentials[appId=" + appId + ", appKey=" + maskedKey() + "]";
    }
}
