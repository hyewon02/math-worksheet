package com.mathworksheet.worksheet.verify;

import java.util.List;

/**
 * 역변환 비교 결과(verify.json). 문제마다 일치 여부와 다른 부분을 담는다.
 */
public record VerifyReport(String manuscript, String docx, List<UnitResult> units) {

    public boolean allMatched() {
        return units.stream().allMatch(UnitResult::matched);
    }

    public List<UnitResult> mismatches() {
        return units.stream().filter(u -> !u.matched()).toList();
    }

    /**
     * @param key         문제 번호, 또는 묶음 공통 지문은 "묶음@첫 번호"
     * @param expected    원고 글자(정규화 전, 수식은 ⟦…⟧)
     * @param actual      역변환 글자(정규화 전). 결과에 없으면 null
     * @param differences 정규화 후 다른 부분(원고 → 결과)
     */
    public record UnitResult(String key, boolean matched, int expectedMath, int actualMath,
                             String expected, String actual, List<Difference> differences) {
    }

    /** @param before 원고에서 다른 부분 바로 앞 글자(위치 확인용) */
    public record Difference(String before, String expected, String actual) {
    }
}
