package com.sanad.platform.access.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description) {
}
