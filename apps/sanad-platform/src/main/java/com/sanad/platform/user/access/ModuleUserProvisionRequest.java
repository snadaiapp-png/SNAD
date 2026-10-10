package com.sanad.platform.user.access;

import com.sanad.platform.user.dto.CreateUserRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ModuleUserProvisionRequest(
        @NotNull @Valid CreateUserRequest user,
        @NotBlank String routeRoot,
        @NotEmpty List<@NotBlank String> capabilityCodes) {
}
