package com.mathworksheet.worksheet.domain;

import java.util.List;

import com.mathworksheet.problem.domain.AnswerStatus;
import com.mathworksheet.problem.domain.ProblemType;

/**
 * 문제지에 담을 때 복사해 두는 문제 내용(설계서 6장 "스냅샷과 갱신"). 문제은행에서 문제가 바뀌거나 지워져도
 * 저장된 문제지는 이 스냅샷으로 만들 당시 그대로 다시 내려받을 수 있다.
 *
 * @param version  스냅샷을 뜰 때의 문제 version. 현재 version과 다르면 "수정됨"
 * @param source   출처(교재 쪽 번호). 정답 미입력 표시에 쓴다
 * @param group    묶음 문제면 공통 지문, 아니면 null
 */
public record ProblemSnapshot(
        Long problemId,
        long version,
        ProblemType type,
        String bodyMarkdown,
        List<ChoiceSnapshot> choices,
        String answer,
        AnswerStatus answerStatus,
        String source,
        List<FigureSnapshot> figures,
        GroupSnapshot group) {

    public record ChoiceSnapshot(int number, String contentMarkdown, boolean imageType) {
    }

    /** @param filePath 이미지 저장소 기준 상대 경로, @param displayWidth 단 너비 대비 % */
    public record FigureSnapshot(String marker, String filePath, int displayWidth) {
    }

    public record GroupSnapshot(Long groupId, long version, String stemMarkdown, List<FigureSnapshot> figures) {
    }

    public boolean answerEntered() {
        return answerStatus == AnswerStatus.ENTERED && answer != null && !answer.isBlank();
    }
}
