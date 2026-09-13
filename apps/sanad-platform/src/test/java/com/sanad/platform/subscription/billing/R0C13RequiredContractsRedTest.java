package com.sanad.platform.subscription.billing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G01 deliberate RED contract.
 *
 * <p>The approved R0C13 production bounded context does not exist on the
 * baseline. This test MUST fail before implementation, proving the gap without
 * changing application behavior. G02/G03 turns it green by introducing the
 * approved production package and contracts.</p>
 */
class R0C13RequiredContractsRedTest {

    private static final Path BILLING_ROOT =
            Path.of("src/main/java/com/sanad/platform/subscription/billing");

    @Test
    void approvedRevenueBillingBoundedContextMustExist() {
        assertThat(Files.isDirectory(BILLING_ROOT))
                .as("R0C13 RED: approved subscription/billing production bounded context is absent")
                .isTrue();
    }
}
