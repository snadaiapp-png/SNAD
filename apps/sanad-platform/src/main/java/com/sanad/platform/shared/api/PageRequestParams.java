package com.sanad.platform.shared.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Standard pagination request parameters.
 * Controllers accept this as a @Valid @ModelAttribute.
 */
public record PageRequestParams(
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size,
    String sort
) {
    public PageRequestParams {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}
