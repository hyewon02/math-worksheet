package com.mathworksheet.ocr;

import java.nio.file.Path;

/**
 * OCR 엔진. Mathpix 가격·정책 변경에 대비해 인터페이스로 분리한다(3장 "확장 대비", 12장).
 * 구현체는 응답을 손대지 않고 원문 그대로 돌려줘야 한다.
 */
public interface OcrEngine {

    /** OcrResult.엔진 칸에 저장되는 이름(예: "mathpix") */
    String name();

    /**
     * @throws OcrException 호출 실패. 인증·결제 오류는 retryable=false
     */
    OcrResponse recognize(Path image);
}
