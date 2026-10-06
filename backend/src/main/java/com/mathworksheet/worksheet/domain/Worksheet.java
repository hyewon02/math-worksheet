package com.mathworksheet.worksheet.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mathworksheet.common.ApiException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 저장된 문제지(설계서 6장). 문제지 번호(WS-0042)로 문제지·정답지의 짝을 맞춘다 */
@Entity
@Table(name = "worksheet")
public class Worksheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "worksheet_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(name = "duplicated_from_id")
    private Long duplicatedFromId;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "worksheet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<WorksheetItem> items = new ArrayList<>();

    protected Worksheet() {
    }

    public Worksheet(String number, String title, LocalDate date, Template template) {
        this.number = number;
        this.title = title;
        this.date = date;
        this.template = template;
    }

    public void describe(String title, LocalDate date, Template template) {
        this.title = title;
        this.date = date;
        this.template = template;
    }

    /**
     * 문항(자식 엔티티)만 바뀌면 문제지 자체의 {@code @Version}은 오르지 않는다. 수정 시각을 바꿔 version을 올려야
     * 두 선생님이 같은 문제지의 순서를 동시에 바꿀 때 충돌을 잡을 수 있다.
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 요청한 version이 현재와 다르면 다른 선생님이 먼저 수정한 것이다 */
    public void checkVersion(Long expected) {
        if (!Objects.equals(version, expected)) {
            throw ApiException.conflict();
        }
    }

    /** 새로 담은 문제는 맨 뒤에 붙는다. 순서는 {@link #arrange}로 정한다 */
    public WorksheetItem add(ProblemSnapshot snapshot, SpaceSize space) {
        WorksheetItem item = new WorksheetItem(this, snapshot, space);
        item.place(items.size() + 1, space);
        items.add(item);
        return item;
    }

    /**
     * 순서·풀이 공간을 다시 정하고 목록에 없는 문항은 뺀다. 번호는 1번부터 다시 매긴다(설계서 5장 7단계).
     * 같은 묶음의 문제는 붙어 있어야 한다. 공통 지문이 한 번만 나오기 때문이다.
     */
    public void arrange(List<WorksheetItem> ordered, List<SpaceSize> spaces) {
        items.retainAll(ordered);
        items.sort((a, b) -> Integer.compare(ordered.indexOf(a), ordered.indexOf(b)));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).place(i + 1, spaces.get(ordered.indexOf(items.get(i))));
        }
        checkGroupsContiguous();
    }

    public void checkGroupsContiguous() {
        List<Long> closed = new ArrayList<>();
        Long current = null;
        for (WorksheetItem item : items) {
            ProblemSnapshot.GroupSnapshot group = item.getSnapshot().group();
            Long groupId = group == null ? null : group.groupId();
            if (!Objects.equals(groupId, current)) {
                if (current != null) {
                    closed.add(current);
                }
                if (groupId != null && closed.contains(groupId)) {
                    throw ApiException.badRequest("같은 묶음의 문제는 붙어 있어야 합니다. 묶음 문제의 순서를 확인해 주세요.");
                }
                current = groupId;
            }
        }
    }

    public Worksheet duplicate(String newNumber) {
        Worksheet copy = new Worksheet(newNumber, title + " (복사본)", LocalDate.now(), template);
        copy.duplicatedFromId = id;
        for (WorksheetItem item : items) {
            copy.items.add(item.copyTo(copy));
        }
        return copy;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDate() {
        return date;
    }

    public Template getTemplate() {
        return template;
    }

    public Long getDuplicatedFromId() {
        return duplicatedFromId;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<WorksheetItem> getItems() {
        return items;
    }
}
