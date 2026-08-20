package com.sanad.platform.security.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTemplateProvisionerContractTest {

    @Test
    void canonicalMatrix_containsExactlyNineTemplatesAndCorrectedCapabilitySets() {
        var matrix = RoleTemplateProvisioner.canonicalCapabilityMatrix();

        assertThat(matrix).hasSize(9);
        assertThat(matrix.keySet()).containsExactlyInAnyOrder(
                "CRM_SALES", "HR_MANAGER", "ERP_PURCHASER", "ERP_APPROVER",
                "FINANCE_USER", "FINANCE_APPROVER", "STORE_MANAGER",
                "WORKFLOW_APPROVER", "EXECUTIVE_VIEWER");
        assertThat(matrix.get("HR_MANAGER")).containsExactlyInAnyOrder(
                "HR.EMPLOYEE.READ", "HR.EMPLOYEE.WRITE", "HR.EMPLOYEE.ARCHIVE");
        assertThat(matrix.get("ERP_PURCHASER")).isEqualTo(Set.of(
                "ERP.VIEW", "ERP.PROCUREMENT", "ERP.WRITE"));
        assertThat(matrix.get("ERP_APPROVER")).isEqualTo(Set.of(
                "ERP.VIEW", "ERP.APPROVE"));
        assertThat(matrix.get("FINANCE_USER")).isEqualTo(Set.of(
                "FINANCE.VIEW", "FINANCE.WRITE"));
        assertThat(matrix.get("FINANCE_APPROVER")).isEqualTo(Set.of(
                "FINANCE.VIEW", "FINANCE.APPROVE"));
        assertThat(matrix.get("STORE_MANAGER")).isEqualTo(Set.of(
                "ECOMMERCE.VIEW", "ECOMMERCE.WRITE", "ECOMMERCE.PUBLISH"));
        assertThat(matrix.get("WORKFLOW_APPROVER")).isEqualTo(Set.of(
                "WORKFLOW.VIEW", "WORKFLOW.APPROVE"));
        assertThat(matrix.get("EXECUTIVE_VIEWER")).isEqualTo(Set.of(
                "EXECUTIVE_VIEW", "EXECUTIVE_COMMAND_CENTER.VIEW",
                "EXECUTIVE_MANAGEMENT.VIEW", "EXECUTIVE_REPORT.VIEW"));
        assertThat(matrix.get("CRM_SALES")).hasSize(16);
    }
}
