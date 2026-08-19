package com.sanad.platform.crm.mobile.sync.model;

import java.time.Instant;

public record DeviceRegistrationResponse(
    String deviceId,
    String status,
    Instant registeredAt,
    Instant updatedAt
) {}
