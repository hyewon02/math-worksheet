package com.mathworksheet.ocr;

/**
 * OCR 응답 원문. 수정하지 않고 그대로 OcrResult에 보관한다.
 *
 * @param rawJson  엔진이 돌려준 JSON 전체
 * @param text     Mathpix Markdown(응답의 text 필드)
 * @param lineData 줄별 좌표(응답의 line_data 필드) JSON 배열
 */
public record OcrResponse(String rawJson, String text, String lineData) {
}
