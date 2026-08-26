package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Task C6-C R2 — JDBC adapter CONTACT guard unit proof.
 *
 * <p>This unit test verifies the test-only contract that
 * {@link JdbcOwnershipRecordAdapter#updateOwner} throws
 * {@link OwnershipDomainException} with the canonical message
 * "CONTACT owner projection must use ContactTransferUseCases" whenever
 * called with {@code recordType == CONTACT}, BEFORE any SQL is issued.</p>
 *
 * <p>The {@link NamedParameterJdbcTemplate} is mocked so the guard can be
 * proven without a live database; this is the unit-test half of the proof.
 * The PostgreSQL-direct half lives in
 * {@code ContactOwnershipCanonicalizationSpringPostgresTest} (TEST 12).</p>
 *
 * <h3>C6-C requirements covered</h3>
 * <ul>
 *   <li>SECTION 6 — JDBC guard unit test: real {@code JdbcOwnershipRecordAdapter}
 *       wrapping a mocked {@code NamedParameterJdbcTemplate}; CONTACT+USER
 *       updateOwner must throw {@link OwnershipDomainException} with the
 *       canonical message and the template's {@code update(...)} method
 *       must NEVER be invoked.</li>
 * </ul>
 *
 * <p>Required report values:</p>
 * <ul>
 *   <li>{@code JDBC_CONTACT_GUARD_UNIT_PROVEN=YES}</li>
 *   <li>{@code JDBC_CONTACT_SQL_EXECUTED=NO}</li>
 * </ul>
 */
class JdbcOwnershipRecordAdapterContactGuardTest {

    private NamedParameterJdbcTemplate jdbc;
    private JdbcOwnershipRecordAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        adapter = new JdbcOwnershipRecordAdapter(jdbc);
    }

    @Test
    @DisplayName("C6-C.6.1. updateOwner(CONTACT, USER) throws OwnershipDomainException with canonical message before SQL")
    void updateOwnerContactThrowsBeforeSql() {
        UUID tenantId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter.updateOwner(
                tenantId, AssignmentRecordType.CONTACT, contactId,
                OwnerType.USER, userId))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining(
                        "CONTACT owner projection must use ContactTransferUseCases");

        // No SQL may be issued through the mocked JdbcTemplate.
        verify(jdbc, times(0)).update(anyString(), any(SqlParameterSource.class));
        verify(jdbc, times(0)).update(anyString(), any(SqlParameterSource.class), any());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("C6-C.6.2. updateOwner(CONTACT, USER) — partial-null arguments still throw before SQL")
    void updateOwnerContactThrowsOnNullArgsBeforeSql() {
        // Even when caller passes a null argument, the CONTACT guard executes
        // AFTER the null-check so the message is still the CONTACT canonical
        // rejection (not the generic "Complete owner projection command required").
        UUID tenantId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        // ownerId == null: generic null-check fires first — that's correct.
        assertThatThrownBy(() -> adapter.updateOwner(
                tenantId, AssignmentRecordType.CONTACT, contactId,
                OwnerType.USER, null))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("Complete owner projection command required");

        // All-non-null CONTACT: canonical guard fires.
        assertThatThrownBy(() -> adapter.updateOwner(
                tenantId, AssignmentRecordType.CONTACT, contactId,
                OwnerType.USER, UUID.randomUUID()))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining(
                        "CONTACT owner projection must use ContactTransferUseCases");

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("C6-C.6.3. updateOwner(LEAD, USER) is NOT blocked — generic SQL path remains for non-CONTACT records (negative control)")
    void updateOwnerNonContactNotBlocked() {
        UUID tenantId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // For non-CONTACT records the adapter must continue to issue the generic
        // SQL UPDATE — that path is the production behavior for ACCOUNT/LEAD/etc.
        // Mocking the template to return 1 row proves the CONTACT guard is scoped
        // narrowly and does not affect the generic owner-update path.
        org.mockito.Mockito.when(jdbc.update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(SqlParameterSource.class)))
                .thenReturn(1);

        adapter.updateOwner(tenantId, AssignmentRecordType.LEAD, leadId,
                OwnerType.USER, userId);

        // The generic SQL path was exercised — proving the CONTACT guard is
        // narrowly scoped and the LEAD owner mutation still works.
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1))
                .update(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(SqlParameterSource.class));

        // Sanity assertion so the test reads clearly.
        assertThat(AssignmentRecordType.CONTACT)
                .as("CONTACT must remain an enum value reachable by the adapter")
                .isNotNull();
    }
}
