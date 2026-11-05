package com.sanad.platform.integration.zid;

import com.sanad.platform.integration.zid.application.ZidSyncAuditService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redaction guarantees for the audit trail: JWT-shaped tokens, opaque
 * long tokens and base64 blobs never survive into stored audit rows.
 */
class ZidSyncAuditRedactionTest {

    @Test
    void redactsJwtLikeTokens() {
        String message = "auth failed for token eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                + ".dozjgNryP4J3jVm91lSupmeafN3fFLAkmAbVzXbYxxx";
        String redacted = ZidSyncAuditService.redact(message);
        assertThat(redacted).doesNotContain("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    void redactsLongOpaqueTokens() {
        String longToken = "def5020035c1f9ab2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef";
        String redacted = ZidSyncAuditService.redact("refresh rejected: " + longToken);
        assertThat(redacted).doesNotContain(longToken);
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    void redactsBase64Blobs() {
        String blob = "TWFpbmRlciBibG9iIHRoYXQgc2hvdWxkIG5ldmVyIGFwcGVhciBpbiBsb2dzIGF0IGFsbA==";
        String redacted = ZidSyncAuditService.redact("payload echo: " + blob);
        assertThat(redacted).doesNotContain(blob);
    }

    @Test
    void preservesNormalOperationalMessages() {
        String message = "orders pull failed on page 2 (status 429)";
        assertThat(ZidSyncAuditService.redact(message)).isEqualTo(message);
    }

    @Test
    void handlesNullSafely() {
        assertThat(ZidSyncAuditService.redact(null)).isNull();
    }
}
