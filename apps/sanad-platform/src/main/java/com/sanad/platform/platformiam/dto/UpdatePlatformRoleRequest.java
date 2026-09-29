package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePlatformRoleRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description) {
}
