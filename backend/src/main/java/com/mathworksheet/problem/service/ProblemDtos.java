package com.mathworksheet.problem.service;

import java.util.List;

import com.mathworksheet.problem.domain.AnswerStatus;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.problem.domain.Tag;

import jakarta.validation.constraints.NotNull;

/** 문제은행 API의 요청·응답 */
public final class ProblemDtos {

    private ProblemDtos() {
    }

    /**
     * 문제은행 검색 조건(설계서 7장 "문제은행"). 모두 선택 사항.
     *
     * @param answerMissing true면 정답 미입력 문제만
     * @param q             본문 검색어. 띄어쓰기와 LaTeX 명령어는 무시한다
     */
    public record SearchCondition(String textbook, String grade, ProblemType type, String tag,
                                  Boolean answerMissing, String q) {
    }

    /**
     * 정답 수정. later=true면 "나중에 입력".
     *
     * @param version 화면이 불러온 문제의 version. 그 사이 다른 선생님이 고쳤으면 409
     */
    public record AnswerUpdate(String answer, boolean later, @NotNull(message = "version이 필요합니다.") Long version) {
    }

    public record ChoiceView(int number, String contentMarkdown, boolean imageType) {
    }

    public record FigureView(String marker, String url, int displayWidth) {
    }

    public record GroupView(Long id, String stemMarkdown, List<FigureView> figures) {
    }

    public record ProblemView(Long id, Long version, ProblemType type, String textbook, String grade, Integer page,
                              String originalNumber, String bodyMarkdown, List<ChoiceView> choices, String answer,
                              AnswerStatus answerStatus, List<String> tags, GroupView group, Integer groupOrder,
                              List<FigureView> figures) {

        public static ProblemView of(Problem p) {
            GroupView group = p.getGroup() == null ? null : new GroupView(p.getGroup().getId(),
                    p.getGroup().getStemMarkdown(),
                    p.getGroup().getFigures().stream().map(f -> figure(f.getMarker(), f.getDisplayWidth())).toList());
            return new ProblemView(p.getId(), p.getVersion(), p.getType(), p.getTextbook(), p.getGrade(), p.getPage(),
                    p.getOriginalNumber(), p.getBodyMarkdown(),
                    p.getChoices().stream().map(c -> new ChoiceView(c.getNumber(), c.getContentMarkdown(), c.isImageType())).toList(),
                    p.getAnswer(), p.getAnswerStatus(),
                    p.getTags().stream().map(Tag::getName).toList(),
                    group, p.getGroupOrder(),
                    p.getFigures().stream().map(f -> figure(f.getMarker(), f.getDisplayWidth())).toList());
        }

        private static FigureView figure(String marker, int displayWidth) {
            return new FigureView(marker, "/api/figures/" + marker, displayWidth);
        }
    }
}
