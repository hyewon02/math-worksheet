package com.mathworksheet.worksheet.domain;

import java.math.BigDecimal;

/** 문제지 만들기에서 문제별로 고르는 풀이 공간(설계서 8장). 형식의 유형별 기본값에 곱한다 */
public enum SpaceSize {
    SMALL("0.5"), NORMAL("1"), LARGE("2");

    private final BigDecimal factor;

    SpaceSize(String factor) {
        this.factor = new BigDecimal(factor);
    }

    public BigDecimal factor() {
        return factor;
    }
}
