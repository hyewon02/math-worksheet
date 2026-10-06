package com.mathworksheet.worksheet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.mathworksheet.problem.domain.ProblemType;
import com.mathworksheet.worksheet.FormatSettings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 문제지 형식(설계서 6장 Template, 8장 "형식 설정"). 형식 파일은 디스크에 두고 경로만 저장한다 */
@Entity
@Table(name = "template")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** 형식 폴더(AppPaths.templates()) 기준 파일 이름 */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "choices5_max", nullable = false)
    private int choices5Max;

    @Column(name = "choices3_max", nullable = false)
    private int choices3Max;

    @Column(name = "space_choice", nullable = false, precision = 4, scale = 1)
    private BigDecimal spaceChoice;

    @Column(name = "space_short", nullable = false, precision = 4, scale = 1)
    private BigDecimal spaceShort;

    @Column(name = "space_essay", nullable = false, precision = 4, scale = 1)
    private BigDecimal spaceEssay;

    @Column(name = "column_width", nullable = false, precision = 4, scale = 1)
    private BigDecimal columnWidth;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Template() {
    }

    public static Template builtIn(String filePath, FormatSettings settings) {
        Template t = new Template();
        t.filePath = filePath;
        t.builtIn = true;
        t.apply(settings);
        return t;
    }

    /** 내장 형식은 앱이 업데이트될 때마다 설정 파일 값으로 다시 맞춘다 */
    public void apply(FormatSettings settings) {
        this.name = settings.name();
        this.columnCount = settings.columns();
        this.choices5Max = settings.choices5Max();
        this.choices3Max = settings.choices3Max();
        this.spaceChoice = cm(settings.space().get("choice"));
        this.spaceShort = cm(settings.space().get("short"));
        this.spaceEssay = cm(settings.space().get("essay"));
        this.columnWidth = BigDecimal.valueOf(settings.columnWidthCm());
    }

    private static BigDecimal cm(String value) {
        return new BigDecimal(value.replace("cm", "").strip());
    }

    /** 유형별 기본 풀이 공간(cm) */
    public BigDecimal baseSpace(ProblemType type) {
        return switch (type) {
            case CHOICE -> spaceChoice;
            case SHORT -> spaceShort;
            case ESSAY -> spaceEssay;
        };
    }

    public FormatSettings toSettings() {
        return new FormatSettings(name,
                java.util.Map.of("choice", spaceChoice.toPlainString() + "cm",
                        "short", spaceShort.toPlainString() + "cm",
                        "essay", spaceEssay.toPlainString() + "cm"),
                choices5Max, choices3Max, columnCount, columnWidth.doubleValue());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public BigDecimal getColumnWidth() {
        return columnWidth;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }
}
