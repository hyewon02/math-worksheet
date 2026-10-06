package com.mathworksheet.problem.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 공통 지문 묶음(`[3~4]`). 공통 지문과 공통 그림을 하위 문제들이 공유한다 */
@Entity
@Table(name = "problem_group")
public class ProblemGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(name = "stem_markdown", nullable = false)
    private String stemMarkdown;

    @Version
    private Long version;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<Figure> figures = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProblemGroup() {
    }

    public ProblemGroup(String stemMarkdown) {
        this.stemMarkdown = stemMarkdown;
    }

    public Long getId() {
        return id;
    }

    public String getStemMarkdown() {
        return stemMarkdown;
    }

    public Long getVersion() {
        return version;
    }

    public void addFigure(String marker, String filePath, int displayWidth) {
        figures.add(Figure.ofGroup(this, marker, filePath, figures.size() + 1, displayWidth));
    }

    public List<Figure> getFigures() {
        return figures;
    }
}
