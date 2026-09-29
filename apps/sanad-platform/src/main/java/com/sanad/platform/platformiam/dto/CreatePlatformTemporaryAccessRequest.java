package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreatePlatformTemporaryAccessRequest(
        @NotNull UUID capabilityId,
        @NotNull @Future Instant effectiveTo,
        @NotBlank @Size(max = 500) String reason) {
}
