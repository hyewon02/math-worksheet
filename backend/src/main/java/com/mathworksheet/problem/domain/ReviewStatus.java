package com.mathworksheet.problem.domain;

/**
 * 검수 상태(설계서 5장 "상태 전이: 문제"). 완료(DONE)된 문제만 문제은행에 보인다.
 * "시험 변환 중"은 잠깐 거치는 상태라 저장하지 않고, 휴지통은 deleted_at으로 나타낸다.
 */
public enum ReviewStatus {
    SPLIT_PENDING, REVIEW_PENDING, ON_HOLD, CONVERT_ERROR, DONE
}
