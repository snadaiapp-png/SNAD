package com.sanad.platform.access.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9._:-]+") String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description) {
}
