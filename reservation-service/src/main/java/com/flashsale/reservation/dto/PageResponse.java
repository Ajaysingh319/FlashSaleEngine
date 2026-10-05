package com.flashsale.reservation.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** One page of a list endpoint, with enough paging metadata for a dashboard to page through results. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
