package com.sanad.platform.crm.web;

import com.sanad.platform.crm.legacy.infrastructure.LegacyCrmInfrastructureService;

import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.activity.domain.ActivityRepository.ActivityRecord;
import com.sanad.platform.crm.lead.application.LeadConversionUseCases;
import com.sanad.platform.crm.lead.application.LeadUseCases;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.lead.domain.LeadRepository.LeadRecord;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.OpportunityRepository.OpportunityRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.PipelineRecord;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository.StageRecord;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.query.application.QueryUseCases;
import com.sanad.platform.crm.query.domain.TimelineProjectionRepository.TimelineEvent;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * V1 compatibility facade. Persistence, transactions, audit and timeline ownership
 * remain in application use cases and infrastructure adapters.
 */
@Service
class CrmService {
    private final LegacyCrmInfrastructureService extended;
    private final AccountUseCases accountUseCases;
    private final ContactUseCases contactUseCases;
    private final LeadUseCases leadUseCases;
    private final LeadConversionUseCases leadConversionUseCases;
    private final OpportunityUseCases opportunityUseCases;
    private final ActivityUseCases activityUseCases;
    private final QueryUseCases queryUseCases;

    CrmService(LegacyCrmInfrastructureService extended,
               AccountUseCases accountUseCases,
               ContactUseCases contactUseCases,
               LeadUseCases leadUseCases,
               LeadConversionUseCases leadConversionUseCases,
               OpportunityUseCases opportunityUseCases,
               ActivityUseCases activityUseCases,
               QueryUseCases queryUseCases) {
        this.extended = extended;
        this.accountUseCases = accountUseCases;
        this.contactUseCases = contactUseCases;
        this.leadUseCases = leadUseCases;
        this.leadConversionUseCases = leadConversionUseCases;
        this.opportunityUseCases = opportunityUseCases;
        this.activityUseCases = activityUseCases;
        this.queryUseCases = queryUseCases;
    }

    Map<String, Object> createAccount(Authentication authentication, CreateAccountRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        AccountRecord created = accountUseCases.create(tenantId, actorId,
                new AccountRepository.CreateAccountCommand(
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

    List<Map<String, Object>> listAccounts(Authentication authentication, int requestedLimit,
                                           String search) {
        return accountUseCases.list(tenantId(authentication), limit(requestedLimit), search).stream()
                .map(this::toAccountRow)
                .toList();
    }

    Map<String, Object> getAccount(Authentication authentication, UUID accountId) {
        return toAccountRow(accountUseCases.getById(tenantId(authentication), accountId));
    }

    Map<String, Object> updateAccount(Authentication authentication, UUID accountId,
                                      UpdateAccountRequest request) {
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

    Map<String, Object> archiveAccount(Authentication authentication, UUID accountId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        AccountRecord current = accountUseCases.getById(tenantId, accountId);
        return toAccountRow(accountUseCases.archive(tenantId, actorId, accountId, current.version()));
    }

    Map<String, Object> createContact(Authentication authentication, CreateContactRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        if (request.accountId() != null) {
            account(tenantId, request.accountId());
        }
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        ContactRecord created = contactUseCases.create(tenantId, actorId,
                new ContactRepository.CreateContactCommand(
                        request.accountId(),
                        required(request.givenName(), 120, "givenName"),
                        optional(request.familyName(), 120, "familyName"),
                        optional(request.primaryEmail(), 255, "primaryEmail"),
                        optional(request.primaryPhone(), 64, "primaryPhone"),
                        locale(request.preferredLocale()),
                        zone(request.timeZone()),
                        ownerUserId,
                        value(request.consentSummary(), "UNKNOWN").toUpperCase(Locale.ROOT)));
        return toContactRow(created);
    }

    List<Map<String, Object>> listContacts(Authentication authentication, int requestedLimit,
                                           UUID accountId, String search) {
        UUID tenantId = tenantId(authentication);
        if (accountId != null) {
            account(tenantId, accountId);
        }
        return contactUseCases.list(tenantId, limit(requestedLimit), accountId, search).stream()
                .map(this::toContactRow)
                .toList();
    }

    Map<String, Object> createLead(Authentication authentication, CreateLeadRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        LeadRecord created = leadUseCases.create(tenantId, actorId,
                new LeadRepository.CreateLeadCommand(
                        required(request.displayName(), 240, "displayName"),
                        optional(request.companyName(), 240, "companyName"),
                        optional(request.email(), 255, "email"),
                        optional(request.phone(), 64, "phone"),
                        optional(request.source(), 120, "source"),
                        ownerUserId,
                        request.score()));
        return toLeadRow(created);
    }

    List<Map<String, Object>> listLeads(Authentication authentication, int requestedLimit,
                                        String status) {
        String normalizedStatus = status == null || status.isBlank()
                ? null : status.trim().toUpperCase(Locale.ROOT);
        return leadUseCases.list(tenantId(authentication), limit(requestedLimit), normalizedStatus)
                .stream().map(this::toLeadRow).toList();
    }

    Map<String, Object> convertLead(Authentication authentication, UUID leadId,
                                    ConvertLeadRequest request) {
        var result = leadConversionUseCases.convert(
                tenantId(authentication),
                userId(authentication),
                leadId,
                new LeadConversionUseCases.ConvertCommand(
                        request.accountName(),
                        request.createOpportunity(),
                        request.pipelineId(),
                        request.stageId(),
                        request.opportunityName(),
                        request.amount(),
                        request.currencyCode(),
                        request.expectedCloseDate()));

        if (result.idempotent()) {
            LinkedHashMap<String, Object> replay = new LinkedHashMap<>();
            replay.put("lead", toLeadRow(result.lead()));
            replay.put("accountId", result.lead().convertedAccountId());
            replay.put("contactId", result.lead().convertedContactId());
            replay.put("opportunityId", result.lead().convertedOpportunityId());
            replay.put("idempotent", true);
            return replay;
        }

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("lead", toLeadRow(result.lead()));
        response.put("account", toAccountRow(result.account()));
        response.put("contact", toContactRow(result.contact()));
        response.put("opportunity", result.opportunity() == null
                ? null : toOpportunityRow(result.opportunity()));
        response.put("idempotent", false);
        return response;
    }

    Map<String, Object> createPipeline(Authentication authentication, CreatePipelineRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        List<String> stages = request.stages() == null || request.stages().isEmpty()
                ? List.of("New", "Qualified", "Proposal", "Won", "Lost")
                : request.stages();
        if (stages.size() < 2 || stages.size() > 20) {
            throw bad("pipeline stages must contain 2 to 20 items");
        }
        HashSet<String> uniqueStages = new HashSet<>();
        for (String stageName : stages) {
            if (!uniqueStages.add(norm(required(stageName, 160, "stage")))) {
                throw bad("pipeline stage names must be unique");
            }
        }
        PipelineRecord created = opportunityUseCases.createPipeline(tenantId, actorId,
                new PipelineRepository.CreatePipelineCommand(
                        required(request.name(), 160, "name"),
                        currency(request.currencyCode()),
                        stages));
        List<StageRecord> createdStages = opportunityUseCases.listStages(tenantId, created.id());
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(toPipelineRow(created));
        result.put("stageIds", createdStages.stream().map(StageRecord::id).toList());
        return result;
    }

    List<Map<String, Object>> listPipelines(Authentication authentication) {
        return opportunityUseCases.listPipelines(tenantId(authentication)).stream()
                .map(this::toPipelineRow)
                .toList();
    }

    Map<String, Object> createOpportunity(Authentication authentication,
                                          CreateOpportunityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        account(tenantId, request.accountId());
        if (request.contactId() != null) {
            Map<String, Object> contact = contact(tenantId, request.contactId());
            Object contactAccountId = contact.get("account_id");
            if (contactAccountId != null && !request.accountId().equals(contactAccountId)) {
                throw bad("CRM contact is not linked to the selected account");
            }
        }
        pipeline(tenantId, request.pipelineId());
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        OpportunityRecord created = opportunityUseCases.create(tenantId, actorId,
                new OpportunityRepository.CreateOpportunityCommand(
                        request.accountId(),
                        request.contactId(),
                        request.pipelineId(),
                        request.stageId(),
                        required(request.name(), 240, "name"),
                        request.amount(),
                        currency(request.currencyCode()),
                        request.expectedCloseDate(),
                        ownerUserId));
        return toOpportunityRow(created);
    }

    List<Map<String, Object>> listOpportunities(Authentication authentication, int requestedLimit,
                                                UUID accountId) {
        UUID tenantId = tenantId(authentication);
        if (accountId != null) {
            account(tenantId, accountId);
        }
        return opportunityUseCases.list(tenantId, limit(requestedLimit), accountId).stream()
                .map(this::toOpportunityRow)
                .toList();
    }

    Map<String, Object> moveOpportunity(Authentication authentication, UUID opportunityId,
                                        MoveOpportunityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        OpportunityRecord opportunity = opportunityUseCases.getById(tenantId, opportunityId);
        if (Set.of("WON", "LOST", "CANCELLED", "ARCHIVED").contains(opportunity.status())
                && !opportunity.stageId().equals(request.stageId())) {
            throw conflict("Terminal CRM opportunity cannot move to another stage");
        }
        StageRecord target = opportunityUseCases.listStages(tenantId, opportunity.pipelineId())
                .stream()
                .filter(stage -> stage.id().equals(request.stageId()))
                .findFirst()
                .orElseThrow(() -> missing("CRM pipeline stage not found"));
        String status = target.terminalState() == null
                ? value(request.status(), "OPEN").toUpperCase(Locale.ROOT)
                : target.terminalState();
        OpportunityRecord moved = opportunityUseCases.moveStage(
                tenantId,
                actorId,
                opportunityId,
                request.stageId(),
                status,
                optional(request.reason(), 500, "reason"),
                opportunity.version());
        return toOpportunityRow(moved);
    }

    Map<String, Object> createActivity(Authentication authentication, CreateActivityRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        UUID ownerUserId = request.ownerUserId() != null ? request.ownerUserId() : actorId;
        extended.validateOwner(tenantId, ownerUserId);
        extended.validateRelated(tenantId, request.relatedType(), request.relatedId());
        if (request.startAt() != null && request.dueAt() != null
                && request.dueAt().isBefore(request.startAt())) {
            throw bad("dueAt cannot be before startAt");
        }
        String relatedType = request.relatedType() == null
                ? null : request.relatedType().toUpperCase(Locale.ROOT);
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
        return toActivityRow(created);
    }

    List<Map<String, Object>> timeline(Authentication authentication, String subjectType,
                                       UUID subjectId, int requestedLimit) {
        UUID tenantId = tenantId(authentication);
        extended.validateRelated(tenantId, subjectType, subjectId);
        return queryUseCases.getTimeline(tenantId, subjectType, subjectId, limit(requestedLimit))
                .stream()
                .map(this::toTimelineRow)
                .toList();
    }

    private Map<String, Object> toAccountRow(AccountRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("tenant_id", null);
        row.put("display_name", record.displayName());
        row.put("normalized_name", record.normalizedName());
        row.put("account_type", record.accountType());
        row.put("lifecycle_status", record.lifecycleStatus());
        row.put("parent_account_id", record.parentAccountId());
        row.put("owner_user_id", record.ownerUserId());
        row.put("primary_currency_code", record.primaryCurrencyCode());
        row.put("preferred_locale", record.preferredLocale());
        row.put("time_zone", record.timeZone());
        row.put("source", record.source());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toContactRow(ContactRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("account_id", record.accountId());
        row.put("given_name", record.givenName());
        row.put("family_name", record.familyName());
        row.put("display_name", record.displayName());
        row.put("primary_email", record.primaryEmail());
        row.put("primary_phone", record.primaryPhone());
        row.put("preferred_locale", record.preferredLocale());
        row.put("time_zone", record.timeZone());
        row.put("lifecycle_status", record.lifecycleStatus());
        row.put("owner_user_id", record.ownerUserId());
        row.put("consent_summary", record.consentSummary());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toLeadRow(LeadRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("display_name", record.displayName());
        row.put("company_name", record.companyName());
        row.put("email", record.email());
        row.put("phone", record.phone());
        row.put("source", record.source());
        row.put("status", record.status());
        row.put("owner_user_id", record.ownerUserId());
        row.put("score", record.score());
        row.put("converted_account_id", record.convertedAccountId());
        row.put("converted_contact_id", record.convertedContactId());
        row.put("converted_opportunity_id", record.convertedOpportunityId());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toPipelineRow(PipelineRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("name", record.name());
        row.put("currency_code", record.currencyCode());
        row.put("active", record.active());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toOpportunityRow(OpportunityRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("account_id", record.accountId());
        row.put("contact_id", record.contactId());
        row.put("pipeline_id", record.pipelineId());
        row.put("stage_id", record.stageId());
        row.put("name", record.name());
        row.put("amount", record.amount());
        row.put("currency_code", record.currencyCode());
        row.put("probability", record.probability());
        row.put("status", record.status());
        row.put("win_loss_reason", record.winLossReason());
        row.put("expected_close_date", record.expectedCloseDate());
        row.put("owner_user_id", record.ownerUserId());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toActivityRow(ActivityRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.id());
        row.put("version", record.version());
        row.put("activity_type", record.activityType());
        row.put("subject", record.subject());
        row.put("body", record.body());
        row.put("related_type", record.relatedType());
        row.put("related_id", record.relatedId());
        row.put("owner_user_id", record.ownerUserId());
        row.put("status", record.status());
        row.put("priority", record.priority());
        row.put("start_at", record.startAt());
        row.put("due_at", record.dueAt());
        row.put("completed_at", record.completedAt());
        row.put("result", record.result());
        row.put("created_at", timestamp(record.createdAt()));
        row.put("updated_at", timestamp(record.updatedAt()));
        return row;
    }

    private Map<String, Object> toTimelineRow(TimelineEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.id());
        row.put("subject_type", event.subjectType());
        row.put("subject_id", event.subjectId());
        row.put("event_type", event.eventType());
        row.put("summary", event.summary());
        row.put("source_type", event.sourceType());
        row.put("source_id", event.sourceId());
        row.put("occurred_at", timestamp(event.occurredAt()));
        row.put("created_by", event.createdBy());
        return row;
    }

    private Map<String, Object> account(UUID tenantId, UUID id) {
        return toAccountRow(accountUseCases.getById(tenantId, id));
    }

    private Map<String, Object> contact(UUID tenantId, UUID id) {
        return toContactRow(contactUseCases.getById(tenantId, id));
    }

    private Map<String, Object> pipeline(UUID tenantId, UUID id) {
        return toPipelineRow(opportunityUseCases.getPipeline(tenantId, id));
    }

    private UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated CRM context", exception);
        }
    }

    private static int limit(int requested) {
        return Math.max(1, Math.min(requested, 200));
    }

    private static String required(String value, int max, String field) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw bad(field + " is required");
        if (result.length() > max) throw bad(field + " exceeds " + max);
        return result;
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw bad(field + " exceeds " + max);
        return result;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String norm(String value) {
        return value(value, "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }

    private static String currency(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) throw bad("currency must be ISO alpha-3");
        return result;
    }

    private static String locale(String value) {
        return value == null || value.isBlank()
                ? null : new Locale.Builder().setLanguageTag(value.trim()).build().toLanguageTag();
    }

    private static String zone(String value) {
        return value == null || value.isBlank() ? null : ZoneId.of(value.trim()).getId();
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
