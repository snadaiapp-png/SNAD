package com.sanad.platform.crm.web;

import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.lead.application.LeadUseCases;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadRecord;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadConversionRecord;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.activity.domain.ActivityRepository.ActivityRecord;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class CrmService {
    // TABLES set removed: no raw SQL remains on any CRM entity table.
    // Only crm_timeline_events retains a legacy INSERT/SELECT — to be migrated
    // to TimelineEventPort in a follow-up work item.
    private final NamedParameterJdbcTemplate jdbc;
    private final CrmExtendedService extended;
    private final AccountUseCases accountUseCases;
    private final ContactUseCases contactUseCases;
    private final LeadUseCases leadUseCases;
    private final OpportunityUseCases opportunityUseCases;
    private final ActivityUseCases activityUseCases;

    CrmService(NamedParameterJdbcTemplate jdbc, CrmExtendedService extended,
                AccountUseCases accountUseCases, ContactUseCases contactUseCases,
                LeadUseCases leadUseCases, OpportunityUseCases opportunityUseCases,
                ActivityUseCases activityUseCases) {
        this.jdbc = jdbc;
        this.extended = extended;
        this.accountUseCases = accountUseCases;
        this.contactUseCases = contactUseCases;
        this.leadUseCases = leadUseCases;
        this.opportunityUseCases = opportunityUseCases;
        this.activityUseCases = activityUseCases;
    }

    // ------------------------------------------------------------------
    // Account V1 — delegates to AccountUseCases (modular domain layer).
    // No raw SQL remains here; the dual-SQL pattern is removed.
    // ------------------------------------------------------------------

    @Transactional
    Map<String, Object> createAccount(Authentication authentication, CreateAccountRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        // V1 backward-compat: if ownerUserId is null, default to the actor (creator).
        // The legacy SQL path allowed NULL owner_user_id; the modular UseCases require
        // a non-null owner. Defaulting to the actor preserves V1 behavior.
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        AccountRecord created = accountUseCases.create(tenantId, actorId, new AccountRepository.CreateAccountCommand(
                required(request.displayName(), 240, "displayName"),
                value(request.accountType(), "BUSINESS").toUpperCase(Locale.ROOT),
                ownerUserId,
                request.parentAccountId(),
                currency(request.primaryCurrencyCode()),
                locale(request.preferredLocale()),
                zone(request.timeZone()),
                optional(request.source(), 80, "source")));
        return toAccountRow(created);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listAccounts(Authentication authentication, int requestedLimit, String search) {
        UUID tenantId = tenantId(authentication);
        return accountUseCases.list(tenantId, limit(requestedLimit), search).stream()
                .map(this::toAccountRow)
                .toList();
    }

    @Transactional(readOnly = true)
    Map<String, Object> getAccount(Authentication authentication, UUID accountId) {
        return toAccountRow(accountUseCases.getById(tenantId(authentication), accountId));
    }

    @Transactional
    Map<String, Object> updateAccount(Authentication authentication, UUID accountId, UpdateAccountRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        AccountRecord current = accountUseCases.getById(tenantId, accountId);
        AccountRecord updated = accountUseCases.update(tenantId, actorId, accountId,
                new AccountRepository.UpdateAccountCommand(
                        request.displayName(),
                        request.ownerUserId(),
                        request.parentAccountId(),
                        currency(request.primaryCurrencyCode()),
                        locale(request.preferredLocale()),
                        zone(request.timeZone()),
                        optional(request.source(), 80, "source")),
                current.version());
        return toAccountRow(updated);
    }

    @Transactional
    Map<String, Object> archiveAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        AccountRecord current = accountUseCases.getById(tenantId, accountId);
        return toAccountRow(accountUseCases.archive(tenantId, actorId, accountId, current.version()));
    }

    /**
     * Maps an {@link AccountRecord} (camelCase domain record) to the V1-compatible
     * row shape (snake_case keys matching the {@code crm_accounts} table columns).
     * This preserves the V1 API contract while removing the legacy SQL.
     */
    private Map<String, Object> toAccountRow(AccountRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("tenant_id", null);
        row.put("display_name", r.displayName());
        row.put("normalized_name", r.normalizedName());
        row.put("account_type", r.accountType());
        row.put("lifecycle_status", r.lifecycleStatus());
        row.put("parent_account_id", r.parentAccountId());
        row.put("owner_user_id", r.ownerUserId());
        row.put("primary_currency_code", r.primaryCurrencyCode());
        row.put("preferred_locale", r.preferredLocale());
        row.put("time_zone", r.timeZone());
        row.put("source", r.source());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    // ------------------------------------------------------------------
    // Contact V1 — delegates to ContactUseCases (modular domain layer).
    // No raw SQL on crm_contacts remains here.
    // ------------------------------------------------------------------

    @Transactional
    Map<String, Object> createContact(Authentication authentication, CreateContactRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        if (request.accountId() != null) account(tenantId, request.accountId());
        // V1 backward-compat: default owner to actor if not specified.
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        ContactRecord created = contactUseCases.create(tenantId, actorId, new ContactRepository.CreateContactCommand(
                request.accountId(),
                required(request.givenName(), 120, "givenName"),
                optional(request.familyName(), 120, "familyName"),
                optional(request.primaryEmail(), 255, "primaryEmail"),
                optional(request.primaryPhone(), 64, "primaryPhone"),
                locale(request.preferredLocale()),
                zone(request.timeZone()),
                ownerUserId,
                value(request.consentSummary(), "UNKNOWN").toUpperCase(Locale.ROOT)));
        Instant now = Instant.now();
        timeline(tenantId, "CONTACT", created.id(), "crm.contact.created", "Contact created", "CRM_CONTACT", created.id(), actorId, now);
        if (request.accountId() != null) timeline(tenantId, "ACCOUNT", request.accountId(), "crm.contact.linked", "Contact linked", "CRM_CONTACT", created.id(), actorId, now);
        return toContactRow(created);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listContacts(Authentication authentication, int requestedLimit, UUID accountId, String search) {
        UUID tenantId = tenantId(authentication);
        if (accountId != null) account(tenantId, accountId);
        return contactUseCases.list(tenantId, limit(requestedLimit), accountId, search).stream()
                .map(this::toContactRow)
                .toList();
    }

    /**
     * Maps a {@link ContactRecord} to the V1-compatible snake_case row shape.
     */
    private Map<String, Object> toContactRow(ContactRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("account_id", r.accountId());
        row.put("given_name", r.givenName());
        row.put("family_name", r.familyName());
        row.put("display_name", r.displayName());
        row.put("primary_email", r.primaryEmail());
        row.put("primary_phone", r.primaryPhone());
        row.put("preferred_locale", r.preferredLocale());
        row.put("time_zone", r.timeZone());
        row.put("lifecycle_status", r.lifecycleStatus());
        row.put("owner_user_id", r.ownerUserId());
        row.put("consent_summary", r.consentSummary());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    // ------------------------------------------------------------------
    // Lead V1 — delegates to LeadUseCases (modular domain layer).
    // No raw SQL on crm_leads remains here.
    // ------------------------------------------------------------------

    @Transactional
    Map<String, Object> createLead(Authentication authentication, CreateLeadRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        // V1 backward-compat: default owner to actor if not specified.
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        LeadRecord created = leadUseCases.create(tenantId, actorId, new LeadRepository.CreateLeadCommand(
                required(request.displayName(), 240, "displayName"),
                optional(request.companyName(), 240, "companyName"),
                optional(request.email(), 255, "email"),
                optional(request.phone(), 64, "phone"),
                optional(request.source(), 120, "source"),
                ownerUserId,
                request.score()));
        timeline(tenantId, "LEAD", created.id(), "crm.lead.created", "Lead created", "CRM_LEAD", created.id(), actorId, Instant.now());
        return toLeadRow(created);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listLeads(Authentication authentication, int requestedLimit, String status) {
        UUID tenantId = tenantId(authentication);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return leadUseCases.list(tenantId, limit(requestedLimit), normalizedStatus).stream()
                .map(this::toLeadRow)
                .toList();
    }

    @Transactional
    Map<String, Object> convertLead(Authentication authentication, UUID leadId, ConvertLeadRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        LeadRecord lead = leadUseCases.getById(tenantId, leadId);
        if ("CONVERTED".equals(lead.status())) {
            LinkedHashMap<String, Object> replay = new LinkedHashMap<>();
            replay.put("lead", toLeadRow(lead));
            replay.put("accountId", lead.convertedAccountId());
            replay.put("contactId", lead.convertedContactId());
            replay.put("opportunityId", lead.convertedOpportunityId());
            replay.put("idempotent", true);
            return replay;
        }
        // createAccount/createContact/createOpportunity already use the modular use cases
        Map<String, Object> account = createAccount(authentication, new CreateAccountRequest(
                value(request.accountName(), lead.displayName()), "PROSPECT", lead.ownerUserId(),
                null, value(request.currencyCode(), "SAR"), "ar-SA", "Asia/Riyadh", "LEAD_CONVERSION"));
        String displayName = lead.displayName().trim();
        int separator = displayName.indexOf(' ');
        String givenName = separator < 0 ? displayName : displayName.substring(0, separator);
        String familyName = separator < 0 ? null : displayName.substring(separator + 1).trim();
        Map<String, Object> contact = createContact(authentication, new CreateContactRequest(
                (UUID) account.get("id"), givenName, familyName, lead.email(), lead.phone(),
                "ar-SA", "Asia/Riyadh", lead.ownerUserId(), "UNKNOWN"));
        Map<String, Object> opportunity = null;
        if (Boolean.TRUE.equals(request.createOpportunity())) {
            Map<String, Object> pipeline = request.pipelineId() == null
                    ? ensureDefaultPipeline(authentication, value(request.currencyCode(), "SAR"))
                    : pipeline(tenantId, request.pipelineId());
            UUID pipelineId = (UUID) pipeline.get("id");
            UUID stageId = request.stageId() == null ? firstStage(tenantId, pipelineId) : request.stageId();
            opportunity = createOpportunity(authentication, new CreateOpportunityRequest(
                    (UUID) account.get("id"), (UUID) contact.get("id"), pipelineId, stageId,
                    value(request.opportunityName(), "Opportunity - " + lead.displayName()),
                    request.amount(), value(request.currencyCode(), "SAR"),
                    request.expectedCloseDate(), lead.ownerUserId()));
        }
        LeadConversionRecord conversion = leadUseCases.convert(tenantId, actorId, leadId,
                new LeadRepository.ConvertLeadCommand(
                        value(request.accountName(), lead.displayName()),
                        request.createOpportunity(),
                        request.pipelineId(),
                        request.stageId(),
                        value(request.opportunityName(), "Opportunity - " + lead.displayName()),
                        request.amount(),
                        value(request.currencyCode(), "SAR"),
                        request.expectedCloseDate()),
                lead.version());
        timeline(tenantId, "LEAD", leadId, "crm.lead.converted", "Lead converted", "CRM_LEAD", leadId, actorId, Instant.now());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("lead", toLeadRow(conversion.lead()));
        result.put("account", account);
        result.put("contact", contact);
        result.put("opportunity", opportunity);
        result.put("idempotent", conversion.idempotent());
        return result;
    }

    /**
     * Maps a {@link LeadRecord} to the V1-compatible snake_case row shape.
     */
    private Map<String, Object> toLeadRow(LeadRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("display_name", r.displayName());
        row.put("company_name", r.companyName());
        row.put("email", r.email());
        row.put("phone", r.phone());
        row.put("source", r.source());
        row.put("status", r.status());
        row.put("owner_user_id", r.ownerUserId());
        row.put("score", r.score());
        row.put("converted_account_id", r.convertedAccountId());
        row.put("converted_contact_id", r.convertedContactId());
        row.put("converted_opportunity_id", r.convertedOpportunityId());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    // ------------------------------------------------------------------
    // Opportunity / Pipeline V1 — delegates to OpportunityUseCases.
    // No raw SQL on crm_opportunities or crm_pipelines remains here.
    // ------------------------------------------------------------------

    @Transactional
    Map<String, Object> createPipeline(Authentication authentication, CreatePipelineRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        List<String> stages = request.stages() == null || request.stages().isEmpty()
                ? List.of("New", "Qualified", "Proposal", "Won", "Lost") : request.stages();
        if (stages.size() < 2 || stages.size() > 20) throw bad("pipeline stages must contain 2 to 20 items");
        HashSet<String> uniqueStages = new HashSet<>();
        for (String stageName : stages) if (!uniqueStages.add(norm(required(stageName, 160, "stage")))) throw bad("pipeline stage names must be unique");
        PipelineRecord created = opportunityUseCases.createPipeline(tenantId, actorId,
                new PipelineRepository.CreatePipelineCommand(
                        required(request.name(), 160, "name"),
                        currency(request.currencyCode()),
                        stages));
        // Surface stageIds to maintain V1 response shape
        List<StageRecord> createdStages = opportunityUseCases.listStages(tenantId, created.id());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(toPipelineRow(created));
        result.put("stageIds", createdStages.stream().map(StageRecord::id).toList());
        return result;
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listPipelines(Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        return opportunityUseCases.listPipelines(tenantId).stream()
                .map(this::toPipelineRow)
                .toList();
    }

    @Transactional
    Map<String, Object> createOpportunity(Authentication authentication, CreateOpportunityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        account(tenantId, request.accountId());
        if (request.contactId() != null) {
            Map<String, Object> contact = contact(tenantId, request.contactId());
            Object contactAccountId = contact.get("account_id");
            if (contactAccountId != null && !request.accountId().equals(contactAccountId))
                throw bad("CRM contact is not linked to the selected account");
        }
        pipeline(tenantId, request.pipelineId());
        extended.validateOwner(tenantId, request.ownerUserId());
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        OpportunityRecord created = opportunityUseCases.create(tenantId, actorId,
                new OpportunityRepository.CreateOpportunityCommand(
                        request.accountId(), request.contactId(), request.pipelineId(), request.stageId(),
                        required(request.name(), 240, "name"), request.amount(),
                        currency(request.currencyCode()), request.expectedCloseDate(),
                        ownerUserId));
        timeline(tenantId, "OPPORTUNITY", created.id(), "crm.opportunity.created", "Opportunity created", "CRM_OPPORTUNITY", created.id(), actorId, Instant.now());
        return toOpportunityRow(created);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> listOpportunities(Authentication authentication, int requestedLimit, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        if (accountId != null) account(tenantId, accountId);
        return opportunityUseCases.list(tenantId, limit(requestedLimit), accountId).stream()
                .map(this::toOpportunityRow)
                .toList();
    }

    @Transactional
    Map<String, Object> moveOpportunity(Authentication authentication, UUID opportunityId, MoveOpportunityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        OpportunityRecord opp = opportunityUseCases.getById(tenantId, opportunityId);
        if (Set.of("WON", "LOST", "CANCELLED", "ARCHIVED").contains(opp.status()) && !opp.stageId().equals(request.stageId()))
            throw conflict("Terminal CRM opportunity cannot move to another stage");
        // Stage target validation (terminal state lookup) - via UseCases
        StageRecord target = opportunityUseCases.listStages(tenantId, opp.pipelineId()).stream()
                .filter(s -> s.id().equals(request.stageId()))
                .findFirst()
                .orElseThrow(() -> missing("CRM pipeline stage not found"));
        String status = target.terminalState() == null
                ? value(request.status(), "OPEN").toUpperCase(Locale.ROOT)
                : target.terminalState();
        OpportunityRecord moved = opportunityUseCases.moveStage(tenantId, actorId, opportunityId,
                request.stageId(), status, optional(request.reason(), 500, "reason"), opp.version());
        timeline(tenantId, "OPPORTUNITY", opportunityId, "crm.opportunity.stage_changed", "Opportunity stage changed", "CRM_OPPORTUNITY", opportunityId, actorId, Instant.now());
        return toOpportunityRow(moved);
    }

    // ------------------------------------------------------------------
    // Activity V1 — delegates to ActivityUseCases.
    // No raw SQL on crm_activities remains here.
    // ------------------------------------------------------------------

    @Transactional
    Map<String, Object> createActivity(Authentication authentication, CreateActivityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        // V1 backward-compat: default owner to actor if not specified.
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        extended.validateRelated(tenantId, request.relatedType(), request.relatedId());
        if (request.startAt() != null && request.dueAt() != null && request.dueAt().isBefore(request.startAt()))
            throw bad("dueAt cannot be before startAt");
        String relatedType = request.relatedType() == null ? null : request.relatedType().toUpperCase(Locale.ROOT);
        ActivityRecord created = activityUseCases.create(tenantId, actorId,
                new ActivityRepository.CreateActivityCommand(
                        value(request.activityType(), "TASK").toUpperCase(Locale.ROOT),
                        required(request.subject(), 240, "subject"),
                        optional(request.body(), 4000, "body"),
                        relatedType,
                        request.relatedId(),
                        ownerUserId,
                        request.priority() == null ? 50 : request.priority(),
                        request.startAt(),
                        request.dueAt()));
        if (relatedType != null && request.relatedId() != null) timeline(tenantId, relatedType, request.relatedId(), "crm.activity.created", "Activity created", "CRM_ACTIVITY", created.id(), actorId, Instant.now());
        return toActivityRow(created);
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> timeline(Authentication authentication, String subjectType, UUID subjectId, int requestedLimit) {
        UUID tenantId = tenantId(authentication);
        extended.validateRelated(tenantId, subjectType, subjectId);
        return jdbc.queryForList("SELECT * FROM crm_timeline_events WHERE tenant_id=:tenantId AND subject_type=:subjectType AND subject_id=:subjectId ORDER BY occurred_at DESC,id LIMIT :limit",
                p().addValue("tenantId", tenantId).addValue("subjectType", subjectType.toUpperCase(Locale.ROOT)).addValue("subjectId", subjectId).addValue("limit", limit(requestedLimit)));
    }

    // ------------------------------------------------------------------
    // Row mappers (camelCase domain record -> snake_case V1 shape)
    // ------------------------------------------------------------------

    private Map<String, Object> toPipelineRow(PipelineRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("name", r.name());
        row.put("currency_code", r.currencyCode());
        row.put("active", r.active());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    private Map<String, Object> toOpportunityRow(OpportunityRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("account_id", r.accountId());
        row.put("contact_id", r.contactId());
        row.put("pipeline_id", r.pipelineId());
        row.put("stage_id", r.stageId());
        row.put("name", r.name());
        row.put("amount", r.amount());
        row.put("currency_code", r.currencyCode());
        row.put("probability", r.probability());
        row.put("status", r.status());
        row.put("win_loss_reason", r.winLossReason());
        row.put("expected_close_date", r.expectedCloseDate());
        row.put("owner_user_id", r.ownerUserId());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    private Map<String, Object> toActivityRow(ActivityRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("activity_type", r.activityType());
        row.put("subject", r.subject());
        row.put("body", r.body());
        row.put("related_type", r.relatedType());
        row.put("related_id", r.relatedId());
        row.put("owner_user_id", r.ownerUserId());
        row.put("status", r.status());
        row.put("priority", r.priority());
        row.put("start_at", r.startAt());
        row.put("due_at", r.dueAt());
        row.put("completed_at", r.completedAt());
        row.put("result", r.result());
        row.put("created_at", r.createdAt() == null ? null : Timestamp.from(r.createdAt()));
        row.put("updated_at", r.updatedAt() == null ? null : Timestamp.from(r.updatedAt()));
        return row;
    }

    // assertNoAccountCycle removed: AccountUseCases.update performs the cycle check
    // via the modular domain layer.
    private Map<String, Object> ensureDefaultPipeline(Authentication authentication, String currencyCode) {
        UUID tenantId = tenantId(authentication);
        // Look up via UseCases to avoid raw SQL on crm_pipelines
        return opportunityUseCases.listPipelines(tenantId).stream()
                .filter(p -> "Default Sales Pipeline".equals(p.name()) && p.active())
                .findFirst()
                .map(this::toPipelineRow)
                .orElseGet(() -> createPipeline(authentication,
                        new CreatePipelineRequest("Default Sales Pipeline", currencyCode,
                                List.of("New", "Qualified", "Proposal", "Won", "Lost"))));
    }
    private UUID firstStage(UUID tenantId, UUID pipelineId) {
        return opportunityUseCases.listStages(tenantId, pipelineId).stream()
                .filter(StageRecord::active)
                .map(StageRecord::id)
                .findFirst()
                .orElseThrow(() -> missing("CRM pipeline has no active stage"));
    }
    private Map<String, Object> account(UUID tenantId, UUID id) { return toAccountRow(accountUseCases.getById(tenantId, id)); }
    private Map<String, Object> contact(UUID tenantId, UUID id) { return toContactRow(contactUseCases.getById(tenantId, id)); }
    private Map<String, Object> lead(UUID tenantId, UUID id) { return toLeadRow(leadUseCases.getById(tenantId, id)); }
    private Map<String, Object> pipeline(UUID tenantId, UUID id) { return toPipelineRow(opportunityUseCases.getPipeline(tenantId, id)); }
    private Map<String, Object> opportunity(UUID tenantId, UUID id) { return toOpportunityRow(opportunityUseCases.getById(tenantId, id)); }
    private Map<String, Object> activity(UUID tenantId, UUID id) { return toActivityRow(activityUseCases.getById(tenantId, id)); }
    private Map<String, Object> stage(UUID tenantId, UUID pipelineId, UUID stageId) {
        return opportunityUseCases.listStages(tenantId, pipelineId).stream()
                .filter(s -> s.id().equals(stageId) && s.active())
                .findFirst()
                .map(this::toStageRow)
                .orElseThrow(() -> missing("CRM pipeline stage not found"));
    }
    private Map<String, Object> toStageRow(StageRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("pipeline_id", r.pipelineId());
        row.put("name", r.name());
        row.put("sequence", r.sequence());
        row.put("probability", r.probability());
        row.put("terminal_state", r.terminalState());
        row.put("active", r.active());
        return row;
    }
    // one() helper removed: all entity lookups now go through UseCases.
    // stageHistory() helper removed: OpportunityUseCases.moveStage records stage
    // history via the modular domain layer.
    private void timeline(UUID tenantId, String subjectType, UUID subjectId, String eventType, String summary, String sourceType, UUID sourceId, UUID actorId, Instant now) { jdbc.update("INSERT INTO crm_timeline_events (id,tenant_id,subject_type,subject_id,event_type,summary,source_type,source_id,occurred_at,created_by) VALUES (:id,:tenantId,:subjectType,:subjectId,:eventType,:summary,:sourceType,:sourceId,:now,:actorId)", p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId).addValue("subjectType", subjectType).addValue("subjectId", subjectId).addValue("eventType", eventType).addValue("summary", summary).addValue("sourceType", sourceType).addValue("sourceId", sourceId).addValue("now", Timestamp.from(now)).addValue("actorId", actorId)); }
    // context(UUID, UUID, UUID, Instant) helper removed: it was only used by the
    // legacy moveOpportunity / stageHistory path which is now delegated to
    // OpportunityUseCases.moveStage.
    private UUID tenantId(Authentication authentication) { return context(authentication, "tenant_id"); }
    private UUID userId(Authentication authentication) { return context(authentication, "user_id"); }
    private UUID context(Authentication authentication, String key) { if (authentication == null || !authentication.isAuthenticated() || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required"); try { return UUID.fromString(details.get(key).toString()); } catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception); } }
    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private static int limit(int requested) { return Math.max(1, Math.min(requested, 200)); }
    private static String required(String value, int max, String field) { String result = value == null ? "" : value.trim(); if (result.isEmpty()) throw bad(field + " is required"); if (result.length() > max) throw bad(field + " exceeds " + max); return result; }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw bad(field + " exceeds " + max); return result; }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String norm(String value) { return value(value, "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim(); }
    private static String email(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private static String currency(String value) { if (value == null || value.isBlank()) return null; String result = value.trim().toUpperCase(Locale.ROOT); if (!result.matches("[A-Z]{3}")) throw bad("currency must be ISO alpha-3"); return result; }
    private static String locale(String value) { return value == null || value.isBlank() ? null : new Locale.Builder().setLanguageTag(value.trim()).build().toLanguageTag(); }
    private static String zone(String value) { return value == null || value.isBlank() ? null : ZoneId.of(value.trim()).getId(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }
}
