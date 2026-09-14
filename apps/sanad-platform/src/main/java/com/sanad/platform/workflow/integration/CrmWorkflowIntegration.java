package com.sanad.platform.workflow.integration;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * R1 — CRM module integration contract (first real module integration).
 *
 * <p>Bounded, metadata-only: CRM declares the entities it owns, deep links
 * back into CRM surfaces, and the attachment classes it accepts for CRM-sourced
 * business records. No events/actions/queries are declared in R1 (empty
 * immutable collections are valid per R1.7); domain actions remain owned by
 * the module's {@code WorkflowSystemActionAdapter}s.</p>
 *
 * <p>CRM remains the sole owner of its business aggregates — Workflow only
 * ever holds bounded references (sourceModule/sourceEntityType/sourceEntityId).</p>
 */
@Component
public class CrmWorkflowIntegration implements WorkflowModuleIntegrationContract {

    @Override public String moduleCode() { return "CRM"; }

    @Override public String moduleVersion() { return "1.0.0"; }

    @Override public String displayName() { return "إدارة العملاء (CRM)"; }

    @Override
    public Collection<EntityDescriptor> entities() {
        return List.of(
                new EntityDescriptor("CUSTOMER", "عميل (CRM)", "crm_contacts",
                        "/crm/customers/{id}"),
                new EntityDescriptor("ACCOUNT", "حساب (CRM)", "crm_accounts",
                        "/crm/accounts/{id}"),
                new EntityDescriptor("OPPORTUNITY", "فرصة (CRM)", "crm_opportunities",
                        "/crm/opportunities/{id}"));
    }

    @Override
    public Collection<DeepLinkDescriptor> deepLinks() {
        return List.of(
                new DeepLinkDescriptor("CRM_CUSTOMER", "فتح العميل في CRM",
                        "/crm/customers/{sourceEntityId}"),
                new DeepLinkDescriptor("CRM_ACCOUNT", "فتح الحساب في CRM",
                        "/crm/accounts/{sourceEntityId}"));
    }

    @Override
    public Collection<AttachmentCapabilityDescriptor> attachmentCapabilities() {
        return List.of(
                new AttachmentCapabilityDescriptor("IMAGE", true, "TENANT_SCOPED"),
                new AttachmentCapabilityDescriptor("PDF", true, "TENANT_SCOPED"),
                new AttachmentCapabilityDescriptor("DOCUMENT", true, "TENANT_SCOPED"));
    }
}
