package com.mathworksheet.problem.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.mathworksheet.common.ApiException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 검수본 문제(설계서 6장). OCR 원문(OcrResult)과 분리되어 있으며, 본문은 검수 화면에서 사람이 고칠 때만 바뀐다.
 * 여러 선생님이 동시에 고칠 수 있어 {@code @Version} 낙관적 잠금을 건다.
 */
@Entity
@Table(name = "problem")
public class Problem {

    private static final Pattern CHOICE_ANSWER = Pattern.compile("[①②③④⑤]");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProblemGroup group;

    @Column(name = "group_order")
    private Integer groupOrder;

    @Column(name = "origin_problem_id")
    private Long originProblemId;

    @Column(length = 200)
    private String textbook;

    @Column(length = 10)
    private String grade;

    private Integer page;

    @Column(name = "original_number", length = 20)
    private String originalNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ProblemType type;

    @Column(length = 1000)
    private String answer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "answer_status", nullable = false, length = 10)
    private AnswerStatus answerStatus;

    @Lob
    @Column(name = "body_markdown", nullable = false)
    private String bodyMarkdown;

    @Lob
    @Column(name = "search_text", nullable = false)
    private String searchText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "hold_memo", length = 1000)
    private String holdMemo;

    @Version
    private Long version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    private List<Choice> choices = new ArrayList<>();

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<Figure> figures = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "problem_tag",
            joinColumns = @JoinColumn(name = "problem_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Problem() {
    }

    public Problem(ProblemType type, String bodyMarkdown, String textbook, String grade, Integer page, String originalNumber) {
        this.type = type;
        this.bodyMarkdown = bodyMarkdown;
        this.textbook = textbook;
        this.grade = grade;
        this.page = page;
        this.originalNumber = originalNumber;
        this.answerStatus = AnswerStatus.LATER;
        this.reviewStatus = ReviewStatus.REVIEW_PENDING;
    }

    @PrePersist
    @PreUpdate
    void refreshSearchText() {
        String[] parts = new String[choices.size() + 1];
        parts[0] = bodyMarkdown;
        for (int i = 0; i < choices.size(); i++) {
            parts[i + 1] = choices.get(i).getContentMarkdown();
        }
        this.searchText = SearchText.of(parts);
    }

    // ---------- 변경 ----------

    public void joinGroup(ProblemGroup group, int order) {
        this.group = group;
        this.groupOrder = order;
    }

    public void addChoice(String contentMarkdown) {
        choices.add(new Choice(this, choices.size() + 1, contentMarkdown, false));
    }

    public void addFigure(String marker, String filePath, int displayWidth) {
        figures.add(Figure.ofProblem(this, marker, filePath, figures.size() + 1, displayWidth));
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    /**
     * 정답 입력(문제은행 카드에서 바로 수정, 재검수 불필요). 객관식은 ①~⑤ 중 하나.
     * 본문이 아니라 정답만 바뀌므로 시험 변환을 다시 거치지 않는다(설계서 5장).
     */
    public void enterAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            throw ApiException.badRequest("정답을 입력해 주세요. 나중에 입력하려면 '나중에 입력'을 고르세요.");
        }
        if (type == ProblemType.CHOICE && !CHOICE_ANSWER.matcher(answer.strip()).matches()) {
            throw ApiException.badRequest("객관식 정답은 ①~⑤ 중 하나를 골라 주세요.");
        }
        this.answer = answer.strip();
        this.answerStatus = AnswerStatus.ENTERED;
    }

    public void answerLater() {
        this.answer = null;
        this.answerStatus = AnswerStatus.LATER;
    }

    /** 검수 완료 + 시험 변환 통과. 3단계(검수 화면)에서 시험 변환 결과로 호출한다 */
    public void completeReview() {
        this.reviewStatus = ReviewStatus.DONE;
    }

    // ---------- 조회 ----------

    public boolean isInBank() {
        return reviewStatus == ReviewStatus.DONE && deletedAt == null;
    }

    /** 정답지의 "정답 미입력 · 출처: …"에 쓰는 출처. 예: "쎈 중2-1 52쪽 1203번" */
    public String source() {
        StringBuilder s = new StringBuilder(textbook == null ? "" : textbook);
        if (page != null) {
            s.append(' ').append(page).append('쪽');
        }
        if (originalNumber != null && !originalNumber.isBlank()) {
            s.append(' ').append(originalNumber).append('번');
        }
        return s.toString().strip();
    }

    public Long getId() {
        return id;
    }

    public ProblemGroup getGroup() {
        return group;
    }

    public Integer getGroupOrder() {
        return groupOrder;
    }

    public Long getOriginProblemId() {
        return originProblemId;
    }

    public String getTextbook() {
        return textbook;
    }

    public String getGrade() {
        return grade;
    }

    public Integer getPage() {
        return page;
    }

    public String getOriginalNumber() {
        return originalNumber;
    }

    public ProblemType getType() {
        return type;
    }

    public String getAnswer() {
        return answer;
    }

    public AnswerStatus getAnswerStatus() {
        return answerStatus;
    }

    public String getBodyMarkdown() {
        return bodyMarkdown;
    }

    public String getSearchText() {
        return searchText;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public List<Figure> getFigures() {
        return figures;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
