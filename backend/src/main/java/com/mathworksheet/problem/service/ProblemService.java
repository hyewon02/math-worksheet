package com.mathworksheet.problem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathworksheet.common.ApiException;
import com.mathworksheet.common.PageResponse;
import com.mathworksheet.problem.domain.AnswerStatus;
import com.mathworksheet.problem.domain.Problem;
import com.mathworksheet.problem.domain.ReviewStatus;
import com.mathworksheet.problem.domain.SearchText;
import com.mathworksheet.problem.domain.Tag;
import com.mathworksheet.problem.repository.ProblemRepository;
import com.mathworksheet.problem.service.ProblemDtos.AnswerUpdate;
import com.mathworksheet.problem.service.ProblemDtos.ProblemView;
import com.mathworksheet.problem.service.ProblemDtos.SearchCondition;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

/** 문제은행 조회와 정답 수정 */
@Service
public class ProblemService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProblemRepository problems;

    public ProblemService(ProblemRepository problems) {
        this.problems = problems;
    }

    /** 검수 완료되고 휴지통에 없는 문제만 최근 등록 순으로 */
    @Transactional(readOnly = true)
    public PageResponse<ProblemView> search(SearchCondition condition, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(problems.findAll(bankSpec(condition), pageable), ProblemView::of);
    }

    @Transactional(readOnly = true)
    public ProblemView get(Long id) {
        return ProblemView.of(find(id));
    }

    /** 정답만 고치는 경우는 시험 변환을 거치지 않는다(설계서 5장). version이 바뀌어 담긴 문제지에 "수정됨"이 뜬다 */
    @Transactional
    public ProblemView updateAnswer(Long id, AnswerUpdate request) {
        Problem problem = find(id);
        if (!Objects.equals(problem.getVersion(), request.version())) {
            throw ApiException.conflict();
        }
        if (request.later()) {
            problem.answerLater();
        } else {
            problem.enterAnswer(request.answer());
        }
        problems.flush();
        return ProblemView.of(problem);
    }

    private Problem find(Long id) {
        return problems.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("문제를 찾을 수 없습니다. 삭제되었을 수 있습니다."));
    }

    static Specification<Problem> bankSpec(SearchCondition c) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("reviewStatus"), ReviewStatus.DONE));
            where.add(cb.isNull(root.get("deletedAt")));
            if (hasText(c.textbook())) {
                where.add(cb.equal(root.get("textbook"), c.textbook().strip()));
            }
            if (hasText(c.grade())) {
                where.add(cb.equal(root.get("grade"), c.grade().strip()));
            }
            if (c.type() != null) {
                where.add(cb.equal(root.get("type"), c.type()));
            }
            if (Boolean.TRUE.equals(c.answerMissing())) {
                where.add(cb.equal(root.get("answerStatus"), AnswerStatus.LATER));
            }
            if (hasText(c.q())) {
                String keyword = SearchText.normalize(c.q());
                if (!keyword.isEmpty()) {
                    where.add(cb.like(root.get("searchText"), "%" + escapeLike(keyword) + "%", '\\'));
                }
            }
            if (hasText(c.tag())) {
                Join<Problem, Tag> tag = root.join("tags");
                where.add(cb.equal(tag.get("name"), c.tag().strip()));
                query.distinct(true);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** 검색어의 %와 _를 글자 그대로 찾는다 */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
