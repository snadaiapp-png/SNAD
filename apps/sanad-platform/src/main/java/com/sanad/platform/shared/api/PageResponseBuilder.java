package com.sanad.platform.shared.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stage 03A — Helper that converts a Spring Data {@link Page} into the
 * unified {@link PageResponse} shape exposed by the API.
 *
 * <p>Spring's {@code PageImpl} metadata is intentionally NOT exposed
 * directly to the API; this builder maps it to the platform's
 * {@link PageMetadata} / {@link SortMetadata} records.</p>
 */
public final class PageResponseBuilder {

    private PageResponseBuilder() {}

    /**
     * Build a {@link PageResponse} from a Spring {@link Page} and a
     * list of mapped content items.
     *
     * @param page    the Spring Data page (carries metadata)
     * @param content the already-mapped response DTOs (one per page item)
     * @param <T>     the response DTO type
     * @return a {@link PageResponse} ready to be returned by a controller
     */
    public static <T> PageResponse<T> from(Page<?> page, List<T> content) {
        List<SortMetadata> sort = page.getPageable().getSort().stream()
                .map(o -> new SortMetadata(o.getProperty(),
                        o.isAscending() ? "asc" : "desc"))
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isFirst(),
                        page.isLast(),
                        page.hasNext(),
                        page.hasPrevious(),
                        sort
                )
        );
    }
}
