package com.mathworksheet.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** 목록 응답. Spring의 Page를 그대로 내보내지 않고 화면에 필요한 칸만 고정한다 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
