package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformRoleRequest(
        @NotBlank @Size(max = 150) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description) {
}
