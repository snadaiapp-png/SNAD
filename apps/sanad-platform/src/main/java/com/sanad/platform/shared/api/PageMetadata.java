package com.sanad.platform.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageMetadata(
    @JsonProperty("number") int number,
    @JsonProperty("size") int size,
    @JsonProperty("totalElements") long totalElements,
    @JsonProperty("totalPages") int totalPages,
    @JsonProperty("first") boolean first,
    @JsonProperty("last") boolean last,
    @JsonProperty("hasNext") boolean hasNext,
    @JsonProperty("hasPrevious") boolean hasPrevious,
    @JsonProperty("sort") List<SortMetadata> sort
) {}
