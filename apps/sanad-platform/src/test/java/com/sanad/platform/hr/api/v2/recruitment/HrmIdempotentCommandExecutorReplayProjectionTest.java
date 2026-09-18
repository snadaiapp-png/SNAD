package com.sanad.platform.hr.api.v2.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.api.v2.HrmIdempotentCommandExecutor;
import com.sanad.platform.hr.recruitment.application.HireConversionResult;
import com.sanad.platform.idempotency.IdempotencyBeginResult;
import com.sanad.platform.idempotency.RequestIdempotencyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HRM-G1 T10 RED — the shared G0 idempotent executor must support a replay
 * projection without forcing endpoint-specific dedup plumbing.
 *
 * <p>T8's conversion ledger correctly returns {@code replayed=true}. The
 * existing executor, however, deserializes the first stored response verbatim;
 * for a same-request-id replay that would otherwise preserve
 * {@code replayed=false}. T10 therefore needs an additive shared-executor
 * overload that applies a caller-provided replay projection only on cache
 * hits, while leaving fresh execution and persisted payload unchanged.</p>
 */
class HrmIdempotentCommandExecutorReplayProjectionTest {

    @Test
    void replayProjection_marksHireConversionReplay_withoutReexecutingCommand() throws Exception {
        RequestIdempotencyService idempotency = mock(RequestIdempotencyService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HrmIdempotentCommandExecutor executor =
                new HrmIdempotentCommandExecutor(idempotency, mapper);

        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        HireConversionResult stored = result(false);

        when(idempotency.begin(any(UUID.class), any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn(new IdempotencyBeginResult(
                        operationId, true, 200, mapper.writeValueAsString(stored)));

        Method replayAwareExecute = Arrays.stream(HrmIdempotentCommandExecutor.class.getMethods())
                .filter(m -> m.getName().equals("execute"))
                .filter(m -> m.getParameterCount() == 8)
                .filter(m -> UnaryOperator.class.equals(m.getParameterTypes()[7]))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "T10 RED: HrmIdempotentCommandExecutor lacks shared replay-projection overload"));

        AtomicBoolean commandInvoked = new AtomicBoolean(false);
        Supplier<HireConversionResult> command = () -> {
            commandInvoked.set(true);
            return result(false);
        };
        UnaryOperator<HireConversionResult> markReplay = prior ->
                new HireConversionResult(
                        prior.offerId(), prior.applicationId(), prior.personId(), prior.personReused(),
                        prior.employeeNumber(), prior.employmentId(), prior.assignmentId(),
                        prior.contractId(), prior.compensationPackageId(), prior.onboardingPlanId(), true);

        Object projected = replayAwareExecute.invoke(
                executor,
                tenantId,
                principalId,
                "HRM.RECRUITMENT.HIRE.CONVERT",
                "req-1",
                "f".repeat(64),
                HireConversionResult.class,
                command,
                markReplay);

        assertThat(projected).isInstanceOf(HireConversionResult.class);
        assertThat(((HireConversionResult) projected).replayed()).isTrue();
        assertThat(commandInvoked).isFalse();
        verify(idempotency, never()).complete(any(UUID.class), any(Integer.class), anyString());
    }

    private static HireConversionResult result(boolean replayed) {
        return new HireConversionResult(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false,
                "EMP-TEST", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), replayed);
    }
}
