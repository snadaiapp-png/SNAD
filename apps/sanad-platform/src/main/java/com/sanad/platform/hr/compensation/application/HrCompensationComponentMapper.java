package com.sanad.platform.hr.compensation.application;

import com.sanad.platform.hr.api.v2.dto.CreateCompensationRequest;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — API-to-domain mapping for compensation components.
 * Component identity is generated at the domain boundary; the API layer
 * never supplies persistence identities.
 */
public final class HrCompensationComponentMapper {

    private HrCompensationComponentMapper() {
    }

    public static List<CompensationComponent> toDomain(List<CreateCompensationRequest.ComponentInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        return inputs.stream()
                .filter(Objects::nonNull)
                .map(input -> new CompensationComponent(
                        UUID.randomUUID(), null, null,
                        CompensationComponentType.valueOf(input.componentType()),
                        input.componentCode(), input.amount(), input.percentage()))

                .toList();
    }
}
