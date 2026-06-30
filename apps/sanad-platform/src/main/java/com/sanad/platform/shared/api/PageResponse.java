package com.sanad.platform.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Unified paginated response wrapper.
 * Prevents leaking Spring's Page metadata directly to API consumers.
 */
public record PageResponse<T>(
    @JsonProperty("content") List<T> content,
    @JsonProperty("page") PageMetadata page
) {
    public static <T> PageResponse<T> of(List<T> content, int number, int size,
                                          long totalElements, int totalPages,
                                          boolean first, boolean last,
                                          boolean hasNext, boolean hasPrevious,
                                          List<SortMetadata> sort) {
        return new PageResponse<>(content,
            new PageMetadata(number, size, totalElements, totalPages,
                first, last, hasNext, hasPrevious, sort));
    }
}
