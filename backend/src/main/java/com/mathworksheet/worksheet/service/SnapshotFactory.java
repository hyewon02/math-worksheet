package com.mathworksheet.worksheet.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.mathworksheet.problem.domain.Figure;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ProblemGroup;
import com.mathworksheet.worksheet.domain.ProblemSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.ChoiceSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.FigureSnapshot;
import com.mathworksheet.worksheet.domain.ProblemSnapshot.GroupSnapshot;

/** 문제은행의 문제를 문제지 스냅샷으로 복사한다. 트랜잭션 안에서 불러야 한다(지연 로딩) */
@Component
public class SnapshotFactory {

    public ProblemSnapshot of(Problem p) {
        ProblemGroup g = p.getGroup();
        GroupSnapshot group = g == null ? null
                : new GroupSnapshot(g.getId(), g.getVersion(), g.getStemMarkdown(), figures(g.getFigures()));
        return new ProblemSnapshot(p.getId(), p.getVersion(), p.getType(), p.getBodyMarkdown(),
                p.getChoices().stream().map(c -> new ChoiceSnapshot(c.getNumber(), c.getContentMarkdown(), c.isImageType())).toList(),
                p.getAnswer(), p.getAnswerStatus(), p.source(), figures(p.getFigures()), group);
    }

    /** 스냅샷을 뜬 뒤 문제나 공통 지문이 수정되었는지 */
    public boolean isOutdated(ProblemSnapshot snapshot, Problem current) {
        if (current.getVersion() != snapshot.version()) {
            return true;
        }
        ProblemGroup g = current.getGroup();
        GroupSnapshot sg = snapshot.group();
        if (g == null || sg == null) {
            return (g == null) != (sg == null);
        }
        return !Objects.equals(g.getId(), sg.groupId()) || g.getVersion() != sg.version();
    }

    private static List<FigureSnapshot> figures(List<Figure> figures) {
        return figures.stream().map(f -> new FigureSnapshot(f.getMarker(), f.getFilePath(), f.getDisplayWidth())).toList();
    }
}
