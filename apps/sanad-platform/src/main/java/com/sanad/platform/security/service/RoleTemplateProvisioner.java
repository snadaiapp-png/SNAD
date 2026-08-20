package com.sanad.platform.security.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime provisioner for the nine canonical SNAD tenant role templates.
 *
 * <p>Migrations establish and validate the canonical matrix for existing
 * tenants. This component closes the lifecycle gap for tenants created after
 * those migrations. {@code role_template_bindings} is the authoritative
 * provenance source: a same-code customer role without a binding is never
 * silently taken over.
 */
@Component
public class RoleTemplateProvisioner {

    private static final String TEMPLATE_VERSION = "V20260820_7";
    private static final String PROVISIONED_BY = "RoleTemplateProvisioner";

    private final JdbcTemplate jdbc;

    public RoleTemplateProvisioner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Exact matrix validated by V20260820_7. Package-private for contract tests. */
    static Map<String, Set<String>> canonicalCapabilityMatrix() {
        LinkedHashMap<String, Set<String>> matrix = new LinkedHashMap<>();
        matrix.put("CRM_SALES", linkedSet(
                "CRM.ACCOUNT.READ", "CRM.ACCOUNT.WRITE",
                "CRM.CONTACT.READ", "CRM.CONTACT.WRITE",
                "CRM.LEAD.READ", "CRM.LEAD.WRITE", "CRM.LEAD.CONVERT",
                "CRM.OPPORTUNITY.READ", "CRM.OPPORTUNITY.WRITE",
                "CRM.ACTIVITY.READ", "CRM.ACTIVITY.WRITE",
                "CRM.TASK.READ", "CRM.TASK.WRITE",
                "CRM.NOTE.READ", "CRM.NOTE.WRITE", "CRM.TAG.READ"));
        matrix.put("HR_MANAGER", linkedSet(
                "HR.EMPLOYEE.READ", "HR.EMPLOYEE.WRITE", "HR.EMPLOYEE.ARCHIVE"));
        matrix.put("ERP_PURCHASER", linkedSet("ERP.VIEW", "ERP.PROCUREMENT", "ERP.WRITE"));
        matrix.put("ERP_APPROVER", linkedSet("ERP.VIEW", "ERP.APPROVE"));
        matrix.put("FINANCE_USER", linkedSet("FINANCE.VIEW", "FINANCE.WRITE"));
        matrix.put("FINANCE_APPROVER", linkedSet("FINANCE.VIEW", "FINANCE.APPROVE"));
        matrix.put("STORE_MANAGER", linkedSet(
                "ECOMMERCE.VIEW", "ECOMMERCE.WRITE", "ECOMMERCE.PUBLISH"));
        matrix.put("WORKFLOW_APPROVER", linkedSet("WORKFLOW.VIEW", "WORKFLOW.APPROVE"));
        matrix.put("EXECUTIVE_VIEWER", linkedSet(
                "EXECUTIVE_VIEW", "EXECUTIVE_COMMAND_CENTER.VIEW",
                "EXECUTIVE_MANAGEMENT.VIEW", "EXECUTIVE_REPORT.VIEW"));
        return java.util.Collections.unmodifiableMap(matrix);
    }

    private static Set<String> linkedSet(String... values) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(List.of(values)));
    }

    @Transactional
    public void provision(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");

        // New-tenant provisioning can run before a normal tenant request context
        // exists. Scope the current transaction explicitly; SET LOCAL resets at
        // transaction end and does not leak across pooled connections.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());

        for (Map.Entry<String, Set<String>> entry : canonicalCapabilityMatrix().entrySet()) {
            provisionTemplate(tenantId, entry.getKey(), entry.getValue());
        }
    }

    private void provisionTemplate(UUID tenantId, String templateKey, Set<String> expectedCapabilities) {
        UUID roleId = findBoundRoleId(tenantId, templateKey);
        if (roleId == null) {
            UUID sameCodeRole = findRoleByCode(tenantId, templateKey);
            if (sameCodeRole != null) {
                throw new IllegalStateException(
                        "Cannot provision SNAD template " + templateKey
                                + ": tenant already has an unbound customer-managed role with that code");
            }
            roleId = UUID.randomUUID();
            TemplateMetadata metadata = metadata(templateKey);
            jdbc.update("INSERT INTO roles (id, tenant_id, code, name, description, status, "
                            + "is_system_managed, role_origin, template_key, template_version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', TRUE, 'SNAD_TEMPLATE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    roleId, tenantId, templateKey, metadata.name(), metadata.description(),
                    templateKey, TEMPLATE_VERSION);
            jdbc.update("INSERT INTO role_template_bindings "
                            + "(id, tenant_id, role_id, template_key, template_version, provisioned_at, provisioned_by) "
                            + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                    UUID.randomUUID(), tenantId, roleId, templateKey, TEMPLATE_VERSION, PROVISIONED_BY);
        } else {
            jdbc.update("UPDATE roles SET is_system_managed=TRUE, role_origin='SNAD_TEMPLATE', "
                            + "template_key=?, template_version=?, status='ACTIVE', updated_at=CURRENT_TIMESTAMP "
                            + "WHERE tenant_id=? AND id=?",
                    templateKey, TEMPLATE_VERSION, tenantId, roleId);
        }

        reconcileCapabilities(tenantId, roleId, templateKey, expectedCapabilities);
    }

    private void reconcileCapabilities(UUID tenantId, UUID roleId, String templateKey,
                                       Set<String> expectedCapabilities) {
        Map<String, UUID> expectedIds = new LinkedHashMap<>();
        for (String code : expectedCapabilities) {
            try {
                UUID capabilityId = jdbc.queryForObject(
                        "SELECT id FROM access_capabilities WHERE code=? AND status='ACTIVE'",
                        UUID.class, code);
                if (capabilityId == null) throw new EmptyResultDataAccessException(1);
                expectedIds.put(code, capabilityId);
            } catch (EmptyResultDataAccessException missing) {
                throw new IllegalStateException(
                        "Mandatory ACTIVE capability " + code + " is missing for template " + templateKey,
                        missing);
            }
        }

        List<CapabilityRow> actual = jdbc.query(
                "SELECT rc.capability_id, ac.code FROM role_capabilities rc "
                        + "LEFT JOIN access_capabilities ac ON ac.id=rc.capability_id "
                        + "WHERE rc.tenant_id=? AND rc.role_id=?",
                (rs, rowNum) -> new CapabilityRow(
                        rs.getObject("capability_id", UUID.class), rs.getString("code")),
                tenantId, roleId);

        for (CapabilityRow row : actual) {
            if (row.code() == null || !expectedCapabilities.contains(row.code())) {
                jdbc.update("DELETE FROM role_capabilities WHERE tenant_id=? AND role_id=? AND capability_id=?",
                        tenantId, roleId, row.capabilityId());
            }
        }

        for (Map.Entry<String, UUID> expected : expectedIds.entrySet()) {
            jdbc.update("INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at) "
                            + "SELECT ?, ?, ?, ?, CURRENT_TIMESTAMP "
                            + "WHERE NOT EXISTS (SELECT 1 FROM role_capabilities "
                            + "WHERE tenant_id=? AND role_id=? AND capability_id=?)",
                    UUID.randomUUID(), tenantId, roleId, expected.getValue(),
                    tenantId, roleId, expected.getValue());
        }
    }

    private UUID findBoundRoleId(UUID tenantId, String templateKey) {
        try {
            return jdbc.queryForObject(
                    "SELECT role_id FROM role_template_bindings WHERE tenant_id=? AND template_key=?",
                    UUID.class, tenantId, templateKey);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID findRoleByCode(UUID tenantId, String code) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM roles WHERE tenant_id=? AND code=?",
                    UUID.class, tenantId, code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private TemplateMetadata metadata(String key) {
        return switch (key) {
            case "CRM_SALES" -> new TemplateMetadata("CRM Sales", "Sales representative: core CRM read/write and lead conversion");
            case "HR_MANAGER" -> new TemplateMetadata("HR Manager", "HR manager: employee administration");
            case "ERP_PURCHASER" -> new TemplateMetadata("ERP Purchaser", "ERP purchaser: procurement operations without approval authority");
            case "ERP_APPROVER" -> new TemplateMetadata("ERP Approver", "ERP approver: approval authority separated from purchasing writes");
            case "FINANCE_USER" -> new TemplateMetadata("Finance User", "Finance user: finance write operations without approval authority");
            case "FINANCE_APPROVER" -> new TemplateMetadata("Finance Approver", "Finance approver: finance approval authority separated from writes");
            case "STORE_MANAGER" -> new TemplateMetadata("Store Manager", "E-commerce store management and publishing");
            case "WORKFLOW_APPROVER" -> new TemplateMetadata("Workflow Approver", "Workflow approval authority without workflow writes");
            case "EXECUTIVE_VIEWER" -> new TemplateMetadata("Executive Viewer", "Read-only executive management and reporting access");
            default -> throw new IllegalArgumentException("Unknown canonical template: " + key);
        };
    }

    private record CapabilityRow(UUID capabilityId, String code) {}
    private record TemplateMetadata(String name, String description) {}
}
