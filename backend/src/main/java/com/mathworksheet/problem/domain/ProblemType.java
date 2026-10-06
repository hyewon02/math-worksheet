package com.mathworksheet.problem.domain;

/** 문제 유형. 원고 형식의 type 속성 값(choice/short/essay)과 짝을 이룬다 */
public enum ProblemType {
    CHOICE, SHORT, ESSAY;

    public String manuscriptName() {
        return name().toLowerCase();
    }
}
