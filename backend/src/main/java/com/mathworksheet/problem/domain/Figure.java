package com.mathworksheet.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 잘라낸 그림. 본문의 {@code [그림:marker]} 표시와 이미지 파일을 잇는다.
 * 문제 또는 묶음 중 한쪽에만 속한다(DB 제약 ck_figure_owner).
 */
@Entity
@Table(name = "figure")
public class Figure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProblemGroup group;

    @Column(nullable = false, unique = true, length = 50)
    private String marker;

    /** 이미지 저장소 기준 상대 경로(예: figures/12/1234_1.png) */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 단 너비 대비 %(30/50/80/100) */
    @Column(name = "display_width", nullable = false)
    private int displayWidth;

    protected Figure() {
    }

    public static Figure ofProblem(Problem problem, String marker, String filePath, int sortOrder, int displayWidth) {
        Figure f = new Figure(marker, filePath, sortOrder, displayWidth);
        f.problem = problem;
        return f;
    }

    public static Figure ofGroup(ProblemGroup group, String marker, String filePath, int sortOrder, int displayWidth) {
        Figure f = new Figure(marker, filePath, sortOrder, displayWidth);
        f.group = group;
        return f;
    }

    private Figure(String marker, String filePath, int sortOrder, int displayWidth) {
        this.marker = marker;
        this.filePath = filePath;
        this.sortOrder = sortOrder;
        this.displayWidth = displayWidth;
    }

    public String getMarker() {
        return marker;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }
}
