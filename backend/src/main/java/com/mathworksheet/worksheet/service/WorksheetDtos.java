package com.mathworksheet.worksheet.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mathworksheet.worksheet.domain.ProblemSnapshot;
import com.mathworksheet.worksheet.domain.SpaceSize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 문제지 API의 요청·응답 */
public final class WorksheetDtos {

    private WorksheetDtos() {
    }

    /**
     * 문제지 저장. 문항 순서가 곧 문제 번호다.
     *
     * @param date    비우면 오늘(설계서 8장 "날짜는 오늘로 자동")
     * @param version 수정할 때만. 화면이 불러온 문제지의 version
     */
    public record WorksheetRequest(
            @NotBlank(message = "제목을 입력해 주세요.") @Size(max = 200, message = "제목은 200자까지 쓸 수 있습니다.") String title,
            LocalDate date,
            @NotNull(message = "형식을 골라 주세요.") Long templateId,
            @NotEmpty(message = "문제를 하나 이상 담아 주세요.") List<@Valid ItemRequest> items,
            Long version) {
    }

    /**
     * 문항 하나. 이미 담긴 문항은 itemId로(스냅샷 유지), 새로 담는 문제는 problemId로 가리킨다.
     *
     * @param space 비우면 보통
     */
    public record ItemRequest(Long itemId, Long problemId, SpaceSize space) {
    }

    public record TemplateView(Long id, String name, int columnCount, boolean builtIn) {
    }

    /**
     * @param number         문제지 안 번호(1부터)
     * @param outdated       담은 뒤 문제은행에서 수정됨 → "최신으로 갱신" 버튼
     * @param problemDeleted 원본 문제가 휴지통에 있거나 지워짐. 스냅샷으로는 그대로 만들 수 있다
     */
    public record ItemView(Long itemId, int number, Long problemId, SpaceSize space, boolean outdated,
                           boolean problemDeleted, ProblemSnapshot snapshot) {
    }

    public record WorksheetView(Long id, String number, String title, LocalDate date, TemplateView template,
                                Long version, Long duplicatedFromId, LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<ItemView> items) {
    }

    public record WorksheetSummary(Long id, String number, String title, LocalDate date, String templateName,
                                   int itemCount, long outdatedCount, LocalDateTime updatedAt) {
    }

    /** "최신으로 갱신". itemIds를 비우면 수정된 문항 전부 */
    public record RefreshRequest(@NotNull(message = "version이 필요합니다.") Long version, List<Long> itemIds) {
    }
}
