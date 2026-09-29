package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdatePlatformUserRequest(
        @Email @Size(max = 255) String email,
        @Size(max = 200) String displayName) {
}
