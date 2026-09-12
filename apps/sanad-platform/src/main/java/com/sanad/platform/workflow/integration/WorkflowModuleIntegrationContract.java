package com.sanad.platform.workflow.integration;

import java.util.Collection;
import java.util.List;

/**
 * R1 GATE R1.7 — stable, module-owned Workflow integration contract (SPI).
 *
 * <p>Every current and future SNAD module integrates with Workflow through
 * this metadata-oriented, bounded contract. Modules describe WHAT they expose
 * (entities, events, triggers, actions, queries, capabilities, deep links,
 * attachment capabilities, report dimensions, AI context) — never HOW
 * (no raw repositories, no JdbcTemplate, no EntityManager, no arbitrary SQL,
 * no arbitrary network clients, no reflection entrypoints).</p>
 *
 * <p>Every collection method defaults to an immutable empty collection: a
 * module may integrate partially (e.g. entities + deep links only) and still
 * be a valid contract implementation. Implementations must be deterministic
 * and side-effect free — the registry reads them at startup and the Designer
 * catalog reads them per request.</p>
 *
 * <p>Auto-discovery (R1 GATE R1.8): Spring beans implementing this contract
 * are registered by {@link WorkflowModuleIntegrationRegistry} with zero
 * Workflow-core edits — the R1.9 future-module proof depends on this.</p>
 */
public interface WorkflowModuleIntegrationContract {

    /** Machine module code (e.g. {@code CRM}); registry normalizes to upper-case. */
    String moduleCode();

    /** Owning module's version string (e.g. {@code 1.0.0}); informational. */
    String moduleVersion();

    /** Human display name (Arabic-aware) for Designer surfaces. */
    String displayName();

    /** Business entities this module owns and may expose to Workflow. */
    default Collection<EntityDescriptor> entities() { return List.of(); }

    /** Domain events this module can emit toward Workflow triggers. */
    default Collection<EventDescriptor> events() { return List.of(); }

    /** Named trigger points this module supports (manual/API are platform-level). */
    default Collection<TriggerDescriptor> triggers() { return List.of(); }

    /** Governed domain actions Workflow may invoke via module-owned adapters. */
    default Collection<ActionDescriptor> actions() { return List.of(); }

    /** Bounded read queries exposed for step configuration / context. */
    default Collection<QueryDescriptor> queries() { return List.of(); }

    /** Workflow-relevant capability codes this module requires/understands. */
    default Collection<CapabilityDescriptor> capabilities() { return List.of(); }

    /** Deep-link templates back into the owning module's UI. */
    default Collection<DeepLinkDescriptor> deepLinks() { return List.of(); }

    /** Attachment capabilities for R1.15 boundary integration. */
    default Collection<AttachmentCapabilityDescriptor> attachmentCapabilities() { return List.of(); }

    /** Report dimensions for future (R2) analytics compatibility only. */
    default Collection<ReportDimensionDescriptor> reportDimensions() { return List.of(); }

    /** AI context descriptors for future (R2+) compatibility only. */
    default Collection<AiContextDescriptor> aiContextDescriptors() { return List.of(); }

    // === Descriptor records (bounded, serializable metadata) ===

    record EntityDescriptor(String entityType, String displayName, String sourceTable,
                            String deepLinkTemplate) {}

    record EventDescriptor(String eventType, String displayName, String description) {}

    record TriggerDescriptor(String triggerKey, String displayName, String description) {}

    record ActionDescriptor(String actionCode, String displayName, String moduleCode,
                            String inputSchema, String outputSchema, String requiredCapability,
                            String riskLevel, boolean compensatable) {}

    record QueryDescriptor(String queryCode, String displayName, String description) {}

    record CapabilityDescriptor(String capabilityCode, String displayName) {}

    record DeepLinkDescriptor(String linkKey, String displayName, String urlTemplate) {}

    record AttachmentCapabilityDescriptor(String attachmentClass, boolean uploadAllowed,
                                          String accessPolicy) {}

    record ReportDimensionDescriptor(String dimensionCode, String displayName) {}

    record AiContextDescriptor(String contextKey, String displayName, String description) {}
}
