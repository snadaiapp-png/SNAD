package com.sanad.platform.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SortMetadata(
    @JsonProperty("field") String field,
    @JsonProperty("direction") String direction
) {}
