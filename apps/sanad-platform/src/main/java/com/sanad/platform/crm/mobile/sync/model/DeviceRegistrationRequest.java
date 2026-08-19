package com.sanad.platform.crm.mobile.sync.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Client-supplied metadata for registering or refreshing a mobile device. */
public record DeviceRegistrationRequest(
    String deviceId,
    @NotBlank String deviceName,
    @NotBlank @Pattern(regexp = "ios|android") String devicePlatform,
    String deviceVersion,
    String appVersion,
    String pushToken
) {}
