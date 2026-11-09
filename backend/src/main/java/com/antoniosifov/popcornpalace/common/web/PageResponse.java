package com.antoniosifov.popcornpalace.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The JSON shape of every paginated list. Defined explicitly so the API
 * contract does not depend on how Spring Data serializes its own Page type.
 *
 * @param page zero-based page index
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
{
    public static <T> PageResponse<T> from(Page<T> page)
    {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
