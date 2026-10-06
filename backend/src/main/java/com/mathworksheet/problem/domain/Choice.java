package com.mathworksheet.problem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 객관식 보기 하나(①~⑤). 내용에는 ① 표시를 넣지 않는다 */
@Entity
@Table(name = "choice")
public class Choice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(nullable = false)
    private int number;

    @Lob
    @Column(name = "content_markdown", nullable = false)
    private String contentMarkdown;

    /** 그래프형 보기: 보기 영역 전체가 그림 1장(설계서 9장) */
    @Column(name = "image_type", nullable = false)
    private boolean imageType;

    protected Choice() {
    }

    Choice(Problem problem, int number, String contentMarkdown, boolean imageType) {
        this.problem = problem;
        this.number = number;
        this.contentMarkdown = contentMarkdown;
        this.imageType = imageType;
    }

    public int getNumber() {
        return number;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public boolean isImageType() {
        return imageType;
    }
}
