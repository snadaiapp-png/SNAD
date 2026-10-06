package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G09 RED contract.
 *
 * <p>Records the mandatory failure-injection/security scenarios that must be
 * represented by executable tests before G09 implementation can be certified.
 * This test intentionally starts RED and must not be weakened or removed to
 * make CI green.</p>
 */
class R0C13G09RequiredContractsRedTest {

    private static final Path BILLING_TEST_ROOT =
            Path.of("src/test/java/com/sanad/platform/subscription/billing");

    private static final List<String> REQUIRED_EXECUTABLE_CONTRACT_MARKERS = List.of(
            "financeFailureAfterProviderSuccessDoesNotActivateSubscription",
            "outboxFailureRollsBackRequiredLocalMutation",
            "auditFailureRollsBackRequiredPrivilegedMutation",
            "providerTimeoutProducesRetryableStateNotPaid",
            "unauthorizedRefundHasZeroProviderOrDomainSideEffect",
            "liveModeWithoutAuthorityFailsClosed"
    );

    @Test
    void g09MandatoryFailureInjectionContractsMustExistBeforeImplementationClosure() throws IOException {
        String executableTests;
        try (Stream<Path> files = Files.walk(BILLING_TEST_ROOT)) {
            executableTests = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(
                            R0C13G09RequiredContractsRedTest.class.getSimpleName() + ".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }

        assertThat(REQUIRED_EXECUTABLE_CONTRACT_MARKERS)
                .as("R13-G09 must start RED until every mandatory failure-injection contract is executable")
                .allSatisfy(marker -> assertThat(executableTests)
                        .as("missing executable G09 contract marker: %s", marker)
                        .contains(marker));
    }
}
