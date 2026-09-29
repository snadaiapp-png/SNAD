package com.sanad.platform.platformiam.dto;

import jakarta.validation.constraints.Size;

public record PlatformLifecycleRequest(@Size(max = 500) String reason) {
}
