package com.sanad.platform.subscription.read;

import java.util.List;
import java.util.function.Function;

/**
 * Shared pagination contract for new executive read models
 * ({@code ?page=0&size=20} → {@code PageResponse<T>}).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /** Maps the content of a page while keeping pagination metadata. */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(),
                page, size, totalElements, totalPages);
    }
}
