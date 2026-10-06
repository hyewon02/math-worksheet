package com.mathworksheet.worksheet.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathworksheet.common.ApiException;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.worksheet.domain.SpaceSize;
import com.mathworksheet.worksheet.domain.Template;
import com.mathworksheet.worksheet.domain.Worksheet;
import com.mathworksheet.worksheet.domain.WorksheetItem;
import com.mathworksheet.worksheet.repository.TemplateRepository;
import com.mathworksheet.worksheet.repository.WorksheetRepository;
import com.mathworksheet.worksheet.service.WorksheetDtos.ItemRequest;
import com.mathworksheet.worksheet.service.WorksheetDtos.ItemView;
import com.mathworksheet.worksheet.service.WorksheetDtos.RefreshRequest;
import com.mathworksheet.worksheet.service.WorksheetDtos.TemplateView;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetRequest;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetSummary;
import com.mathworksheet.worksheet.service.WorksheetDtos.WorksheetView;

/**
 * 문제지 만들기·저장된 문제지(설계서 7장). 담을 때 문제 내용을 스냅샷으로 복사하고,
 * 문제은행에서 수정된 문제는 "최신으로 갱신"을 누를 때만 바뀐다(설계서 6장).
 */
@Service
public class WorksheetService {

    private final WorksheetRepository worksheets;
    private final TemplateRepository templates;
    private final ProblemRepository problems;
    private final SnapshotFactory snapshots;

    public WorksheetService(WorksheetRepository worksheets, TemplateRepository templates, ProblemRepository problems,
                            SnapshotFactory snapshots) {
        this.worksheets = worksheets;
        this.templates = templates;
        this.problems = problems;
        this.snapshots = snapshots;
    }

    @Transactional
    public WorksheetView create(WorksheetRequest request) {
        Worksheet worksheet = new Worksheet("WS-%04d".formatted(worksheets.nextNumber()), request.title().strip(),
                dateOrToday(request.date()), template(request.templateId()));
        arrange(worksheet, request.items());
        worksheets.save(worksheet);
        return view(worksheet);
    }

    @Transactional
    public WorksheetView update(Long id, WorksheetRequest request) {
        Worksheet worksheet = find(id);
        worksheet.checkVersion(request.version());
        worksheet.describe(request.title().strip(), dateOrToday(request.date()), template(request.templateId()));
        arrange(worksheet, request.items());
        worksheet.touch();
        worksheets.flush();
        return view(worksheet);
    }

    @Transactional
    public WorksheetView refresh(Long id, RefreshRequest request) {
        Worksheet worksheet = find(id);
        worksheet.checkVersion(request.version());
        Map<Long, Problem> current = currentProblems(worksheet);
        for (WorksheetItem item : worksheet.getItems()) {
            boolean selected = request.itemIds() == null || request.itemIds().isEmpty()
                    || request.itemIds().contains(item.getId());
            Problem problem = current.get(item.getProblemId());
            if (selected && problem != null && snapshots.isOutdated(item.getSnapshot(), problem)) {
                item.replaceSnapshot(snapshots.of(problem));
            }
        }
        worksheet.checkGroupsContiguous();
        worksheet.touch();
        worksheets.flush();
        return view(worksheet);
    }

    /** 복제해서 새 문제지의 출발점으로(설계서 7장). 스냅샷도 그대로 복사한다 */
    @Transactional
    public WorksheetView duplicate(Long id) {
        Worksheet copy = find(id).duplicate("WS-%04d".formatted(worksheets.nextNumber()));
        worksheets.save(copy);
        return view(copy);
    }

    @Transactional(readOnly = true)
    public WorksheetView get(Long id) {
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public List<WorksheetSummary> list() {
        return worksheets.findAllByOrderByUpdatedAtDesc().stream().map(w -> {
            Map<Long, Problem> current = currentProblems(w);
            long outdated = w.getItems().stream().filter(i -> isOutdated(i, current)).count();
            return new WorksheetSummary(w.getId(), w.getNumber(), w.getTitle(), w.getDate(), w.getTemplate().getName(),
                    w.getItems().size(), outdated, w.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return templates.findAll().stream().map(WorksheetService::templateView).toList();
    }

    public Worksheet find(Long id) {
        return worksheets.findById(id).orElseThrow(() -> ApiException.notFound("문제지를 찾을 수 없습니다."));
    }

    // ---------- 내부 ----------

    private void arrange(Worksheet worksheet, List<ItemRequest> requested) {
        Map<Long, WorksheetItem> existing = worksheet.getItems().stream()
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(WorksheetItem::getId, Function.identity()));
        Map<Long, Problem> newProblems = bankProblems(requested.stream()
                .filter(r -> r.itemId() == null).map(ItemRequest::problemId).toList());

        List<WorksheetItem> ordered = new ArrayList<>();
        List<SpaceSize> spaces = new ArrayList<>();
        Set<Long> seenProblems = new HashSet<>();
        for (ItemRequest r : requested) {
            SpaceSize space = r.space() == null ? SpaceSize.NORMAL : r.space();
            WorksheetItem item;
            if (r.itemId() != null) {
                item = Optional.ofNullable(existing.get(r.itemId()))
                        .orElseThrow(() -> ApiException.badRequest("이 문제지에 없는 문항입니다: " + r.itemId()));
            } else if (r.problemId() != null) {
                item = worksheet.add(snapshots.of(newProblems.get(r.problemId())), space);
            } else {
                throw ApiException.badRequest("담을 문제를 골라 주세요.");
            }
            if (item.getProblemId() != null && !seenProblems.add(item.getProblemId())) {
                throw ApiException.badRequest("같은 문제를 두 번 담을 수 없습니다.");
            }
            ordered.add(item);
            spaces.add(space);
        }
        worksheet.arrange(ordered, spaces);
    }

    /** 새로 담을 수 있는 것은 문제은행에 있는(검수 완료, 휴지통 아님) 문제뿐이다 */
    private Map<Long, Problem> bankProblems(List<Long> ids) {
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest("담을 문제를 골라 주세요.");
        }
        Map<Long, Problem> found = problems.findAllById(ids).stream()
                .filter(Problem::isInBank)
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw ApiException.badRequest("문제은행에 없는 문제입니다: " + id);
            }
        }
        return found;
    }

    private Map<Long, Problem> currentProblems(Worksheet worksheet) {
        List<Long> ids = worksheet.getItems().stream().map(WorksheetItem::getProblemId)
                .filter(java.util.Objects::nonNull).toList();
        return problems.findAllById(ids).stream().collect(Collectors.toMap(Problem::getId, Function.identity()));
    }

    private boolean isOutdated(WorksheetItem item, Map<Long, Problem> current) {
        Problem problem = current.get(item.getProblemId());
        return problem != null && problem.getDeletedAt() == null && snapshots.isOutdated(item.getSnapshot(), problem);
    }

    private WorksheetView view(Worksheet w) {
        Map<Long, Problem> current = currentProblems(w);
        List<ItemView> items = new ArrayList<>();
        int number = 1;
        for (WorksheetItem item : w.getItems()) {
            Problem problem = current.get(item.getProblemId());
            boolean deleted = problem == null || problem.getDeletedAt() != null;
            items.add(new ItemView(item.getId(), number++, item.getProblemId(), item.getSpaceSize(),
                    isOutdated(item, current), deleted, item.getSnapshot()));
        }
        return new WorksheetView(w.getId(), w.getNumber(), w.getTitle(), w.getDate(), templateView(w.getTemplate()),
                w.getVersion(), w.getDuplicatedFromId(), w.getCreatedAt(), w.getUpdatedAt(), items);
    }

    private Template template(Long id) {
        return templates.findById(id).orElseThrow(() -> ApiException.badRequest("형식을 찾을 수 없습니다."));
    }

    private static TemplateView templateView(Template t) {
        return new TemplateView(t.getId(), t.getName(), t.getColumnCount(), t.isBuiltIn());
    }

    private static LocalDate dateOrToday(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }
}
