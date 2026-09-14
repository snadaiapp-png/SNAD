package com.sanad.platform.subscription.rbac;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class G1ForensicCredentialHygieneTest {
    @Test
    void dynamic403ForensicUsesDisposableTestIdentityNotSeededOwnerCredential() throws Exception {
        Path source = locate("apps/sanad-platform/src/test/java/com/sanad/platform/subscription/rbac/G1GDynamic403ForensicPostgresTest.java");
        String text = Files.readString(source);
        assertThat(text).doesNotContain("Senen1985");
        assertThat(text).doesNotContain("snad.ai.app@gmail.com");
    }

    private static Path locate(String relative) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 8 && dir != null; depth++, dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new AssertionError("Cannot locate " + relative);
    }
}
