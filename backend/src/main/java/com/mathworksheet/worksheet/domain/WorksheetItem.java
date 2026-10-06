package com.mathworksheet.worksheet.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 문제지에 담긴 문제 하나. 원본 문제(problemId)를 가리키되, 내용은 담을 때 복사한 스냅샷으로 만든다.
 * 원본이 휴지통에서 완전히 지워지면 problemId는 NULL이 되고 스냅샷만 남는다.
 */
@Entity
@Table(name = "worksheet_item")
public class WorksheetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worksheet_id")
    private Worksheet worksheet;

    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "space_size", nullable = false, length = 10)
    private SpaceSize spaceSize;

    @Lob
    @Convert(converter = ProblemSnapshotConverter.class)
    @Column(nullable = false)
    private ProblemSnapshot snapshot;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    protected WorksheetItem() {
    }

    WorksheetItem(Worksheet worksheet, ProblemSnapshot snapshot, SpaceSize spaceSize) {
        this.worksheet = worksheet;
        this.problemId = snapshot.problemId();
        this.spaceSize = spaceSize;
        replaceSnapshot(snapshot);
    }

    /** "최신으로 갱신" */
    public void replaceSnapshot(ProblemSnapshot snapshot) {
        this.snapshot = snapshot;
        this.snapshotVersion = snapshot.version();
    }

    void place(int sortOrder, SpaceSize spaceSize) {
        this.sortOrder = sortOrder;
        this.spaceSize = spaceSize;
    }

    WorksheetItem copyTo(Worksheet other) {
        WorksheetItem copy = new WorksheetItem(other, snapshot, spaceSize);
        copy.problemId = problemId;
        copy.sortOrder = sortOrder;
        return copy;
    }

    public Long getId() {
        return id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public SpaceSize getSpaceSize() {
        return spaceSize;
    }

    public ProblemSnapshot getSnapshot() {
        return snapshot;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }
}
