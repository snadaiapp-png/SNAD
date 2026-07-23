package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferBoundaryContractTest {

    @Test
    void workflowStubSupportsExactlyOneApprover() {
        InlineTransferWorkflowStubAdapter workflow = new InlineTransferWorkflowStubAdapter();
        UUID tenantId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        UUID first = workflow.startTransferApproval(
                tenantId, transferId, List.of(approverId));
        UUID replay = workflow.startTransferApproval(
                tenantId, transferId, List.of(approverId));

        assertThat(first).isEqualTo(replay);
        assertThat(workflow.isStub()).isTrue();
        assertThatThrownBy(() -> workflow.startTransferApproval(
                tenantId, transferId, List.of(approverId, UUID.randomUUID())))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void disabledHrmBoundaryNeverReturnsSyntheticAbsenceData() {
        DisabledHrmOwnershipAdapter hrm = new DisabledHrmOwnershipAdapter();

        assertThat(hrm.isStub()).isTrue();
        assertThatThrownBy(() -> hrm.isAbsent(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("disabled");
    }
}
