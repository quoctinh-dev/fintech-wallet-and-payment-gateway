package com.fintech.wallet.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationResponse<T> {

    private List<T> items;
    private PaginationMeta pagination;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationMeta {
        private int currentPage;
        private int pageSize;
        private int totalPages;
        private long totalItems;
    }

    public static <T> PaginationResponse<T> fromPage(Page<T> page) {
        PaginationMeta meta = PaginationMeta.builder()
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalItems(page.getTotalElements())
                .build();

        return PaginationResponse.<T>builder()
                .items(page.getContent())
                .pagination(meta)
                .build();
    }
}