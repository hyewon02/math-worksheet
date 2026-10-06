package com.mathworksheet.worksheet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 형식 설정(설계서 8장 "구성 요소"). 형식 파일(.docx) 옆에 같은 이름의 .json으로 둔다.
 *
 * @param space       유형별 기본 풀이 공간(choice/short/essay → "3cm")
 * @param choices5Max 가장 긴 보기가 이 칸 수 이하면 한 줄에 5개. 단 너비·글자 크기가 형식마다 달라 형식별로 정한다
 * @param choices3Max 이 칸 수 이하면 한 줄에 3개, 넘으면 1개
 * @param columns       단 수
 * @param columnWidthCm 단 너비(cm). 그림 크기(단 너비 대비 %)를 cm로 바꿀 때 쓴다(설계서 9장)
 */
public record FormatSettings(String name, Map<String, String> space, int choices5Max, int choices3Max,
                             int columns, double columnWidthCm) {

    /** 설정 파일이 없을 때. 설계서 8장 기본값 */
    public static final FormatSettings DEFAULT =
            new FormatSettings("기본", Map.of("choice", "1cm", "short", "3cm", "essay", "8cm"), 9, 15, 2, 8.2);

    /** 형식 파일 옆의 설정 파일. 없으면 기본값 */
    public static FormatSettings forTemplate(Path template) {
        String file = template.getFileName().toString().replaceFirst("\\.docx$", "") + ".json";
        Path json = template.resolveSibling(file);
        if (!Files.exists(json)) {
            return DEFAULT;
        }
        try {
            return new ObjectMapper().readValue(json.toFile(), FormatSettings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("형식 설정을 읽을 수 없습니다: " + json, e);
        }
    }

    /** Lua 필터에 넘길 Pandoc 메타데이터 인자 */
    List<String> pandocArguments() {
        return List.of("-M", "choices5-max=" + choices5Max, "-M", "choices3-max=" + choices3Max);
    }
}
