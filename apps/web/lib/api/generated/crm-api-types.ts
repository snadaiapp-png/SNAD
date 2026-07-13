/**
 * SNAD CRM API — Generated TypeScript types.
 * ----------------------------------------------------------------------------
 * DO NOT EDIT BY HAND. This file is generated from
 *   docs/crm/contracts/openapi/crm-openapi.json
 * by the `npm run crm:generate-api-types` script (see
 *   scripts/crm/generate-crm-api-types.sh).
 *
 * Regeneration command (from apps/web):
 *   npm run crm:generate-api-types
 *
 * Branch: crm/003-stable-api-contracts
 * Gate:   CRM-G2 — API Contract and Concurrency Gate
 */

export type paths = {
    readonly "/accounts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listAccounts"];
        readonly put?: never;
        readonly post: operations["createAccount"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/accounts/{accountId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getAccount"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateAccount"];
        readonly trace?: never;
    };
    readonly "/accounts/{accountId}/archive": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["archiveAccount"];
        readonly trace?: never;
    };
    readonly "/accounts/{accountId}/customer-360": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["customer360"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/accounts/{accountId}/restore": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["restoreAccount"];
        readonly trace?: never;
    };
    readonly "/activities": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listActivities"];
        readonly put?: never;
        readonly post: operations["createActivity"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/activities/{activityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getActivity"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateActivity"];
        readonly trace?: never;
    };
    readonly "/activities/{activityId}/complete": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["completeActivity"];
        readonly trace?: never;
    };
    readonly "/contacts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listContacts"];
        readonly put?: never;
        readonly post: operations["createContact"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getContact"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateContact"];
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/archive": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["archiveContact"];
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/restore": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["restoreContact"];
        readonly trace?: never;
    };
    readonly "/custom-fields": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listCustomFields"];
        readonly put?: never;
        readonly post: operations["createCustomField"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/custom-fields/search": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["searchCustomFieldValues"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/custom-fields/values/{entityType}/{entityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["readCustomFieldValues"];
        readonly put: operations["upsertCustomFieldValues"];
        readonly post: operations["upsertCustomFieldValuesPost"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/custom-fields/{customFieldId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateCustomField"];
        readonly trace?: never;
    };
    readonly "/imports": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listImportJobs"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/upload": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["uploadImport"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/{jobId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getImportJob"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/{jobId}/cancel": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["cancelImport"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/{jobId}/errors": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listImportErrors"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/{jobId}/errors.csv": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["downloadImportErrors"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/imports/{jobId}/run": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["runImport"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/leads": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listLeads"];
        readonly put?: never;
        readonly post: operations["createLead"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/leads/{leadId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getLead"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/leads/{leadId}/convert": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["convertLead"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/leads/{leadId}/status": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["changeLeadStatus"];
        readonly trace?: never;
    };
    readonly "/opportunities": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listOpportunities"];
        readonly put?: never;
        readonly post: operations["createOpportunity"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/opportunities/{opportunityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getOpportunity"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateOpportunity"];
        readonly trace?: never;
    };
    readonly "/opportunities/{opportunityId}/stage": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["moveOpportunityStage"];
        readonly trace?: never;
    };
    readonly "/pipelines": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listPipelines_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/pipelines/{pipelineId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updatePipeline"];
        readonly trace?: never;
    };
    readonly "/pipelines/{pipelineId}/stages": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listPipelineStages"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/timeline/{subjectType}/{subjectId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["timeline"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        readonly AccountResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly displayName?: string;
            readonly normalizedDisplayName?: string;
            readonly accountType?: string;
            readonly lifecycleStatus?: string;
            readonly primaryCurrencyCode?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly source?: string;
            /** Format: uuid */
            readonly parentAccountId?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ActivityResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly activityType?: string;
            readonly subject?: string;
            readonly body?: string;
            readonly relatedType?: string;
            /** Format: uuid */
            readonly relatedId?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly status?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly startAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
            /** Format: date-time */
            readonly completedAt?: string;
            readonly result?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ActivitySummaryResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly activityType?: string;
            readonly subject?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ArchiveAccountResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly lifecycleStatus?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CompleteActivityRequest: {
            readonly result?: string;
        };
        readonly ContactResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            /** Format: uuid */
            readonly accountId?: string;
            readonly givenName?: string;
            readonly familyName?: string;
            readonly displayName?: string;
            readonly primaryEmail?: string;
            readonly normalizedEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly lifecycleStatus?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly consentSummary?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ContactSummaryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly displayName?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly lifecycleStatus?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ConvertLeadRequest: {
            readonly accountName?: string;
            readonly createOpportunity?: boolean;
            /** Format: uuid */
            readonly pipelineId?: string;
            /** Format: uuid */
            readonly stageId?: string;
            readonly opportunityName?: string;
            readonly amount?: number;
            readonly currencyCode?: string;
            /** Format: date */
            readonly expectedCloseDate?: string;
        };
        readonly CreateAccountRequest: {
            readonly displayName: string;
            readonly accountType?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: uuid */
            readonly parentAccountId?: string;
            readonly primaryCurrencyCode?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly source?: string;
        };
        readonly CreateActivityRequest: {
            readonly activityType: string;
            readonly subject: string;
            readonly body?: string;
            readonly relatedType?: string;
            /** Format: uuid */
            readonly relatedId?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly startAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
        };
        readonly CreateContactRequest: {
            /** Format: uuid */
            readonly accountId?: string;
            readonly givenName: string;
            readonly familyName?: string;
            /** Format: email */
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly consentSummary?: string;
        };
        readonly CreateCustomFieldRequest: {
            readonly entityType: string;
            readonly fieldKey: string;
            readonly labelAr: string;
            readonly labelEn: string;
            readonly dataType: string;
            readonly sensitive?: boolean;
            readonly searchable?: boolean;
            readonly required?: boolean;
        };
        readonly CreateLeadRequest: {
            readonly displayName: string;
            readonly companyName?: string;
            /** Format: email */
            readonly email?: string;
            readonly phone?: string;
            readonly source?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: uuid */
            readonly queueId?: string;
            readonly score?: number;
        };
        readonly CreateOpportunityRequest: {
            /** Format: uuid */
            readonly accountId: string;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly pipelineId: string;
            /** Format: uuid */
            readonly stageId: string;
            readonly name: string;
            readonly amount?: number;
            readonly currencyCode: string;
            /** Format: date */
            readonly expectedCloseDate?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
        };
        readonly CustomFieldResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly entityType?: string;
            readonly fieldKey?: string;
            readonly labelAr?: string;
            readonly labelEn?: string;
            readonly dataType?: string;
            readonly sensitive?: boolean;
            readonly searchable?: boolean;
            readonly required?: boolean;
            readonly active?: boolean;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CustomFieldValuesResponse: {
            readonly entityType?: string;
            /** Format: uuid */
            readonly entityId?: string;
            readonly values?: {
                readonly [key: string]: unknown;
            };
        };
        readonly Customer360Response: {
            readonly account?: components["schemas"]["AccountResponse"];
            readonly contacts?: readonly components["schemas"]["ContactSummaryResponse"][];
            readonly opportunities?: readonly components["schemas"]["OpportunitySummaryResponse"][];
            readonly activities?: readonly components["schemas"]["ActivitySummaryResponse"][];
            readonly timeline?: readonly components["schemas"]["TimelineEventResponse"][];
            readonly customFields?: {
                readonly [key: string]: unknown;
            };
        };
        readonly ImportErrorResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly jobId?: string;
            /** Format: int64 */
            readonly rowNumber?: number;
            readonly errorCode?: string;
            readonly errorMessage?: string;
            readonly rowData?: {
                readonly [key: string]: unknown;
            };
        };
        readonly ImportJobResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly entityType?: string;
            readonly fileName?: string;
            /** Format: int64 */
            readonly totalRows?: number;
            /** Format: int64 */
            readonly processedRows?: number;
            /** Format: int64 */
            readonly successfulRows?: number;
            /** Format: int64 */
            readonly failedRows?: number;
            readonly status?: string;
            readonly mappingJson?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ImportRunResponse: {
            /** Format: uuid */
            readonly jobId?: string;
            readonly status?: string;
            /** Format: int64 */
            readonly processedRows?: number;
            /** Format: int64 */
            readonly successfulRows?: number;
            /** Format: int64 */
            readonly failedRows?: number;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly LeadConversionResponse: {
            readonly lead?: components["schemas"]["LeadResponse"];
            readonly account?: components["schemas"]["AccountResponse"];
            readonly contact?: components["schemas"]["ContactResponse"];
            readonly opportunity?: components["schemas"]["OpportunityResponse"];
            readonly idempotent?: boolean;
        };
        readonly LeadResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly displayName?: string;
            readonly companyName?: string;
            readonly email?: string;
            readonly phone?: string;
            readonly source?: string;
            readonly status?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly score?: number;
            /** Format: uuid */
            readonly convertedAccountId?: string;
            /** Format: uuid */
            readonly convertedContactId?: string;
            /** Format: uuid */
            readonly convertedOpportunityId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ListResponseAccountResponse: {
            readonly data?: readonly components["schemas"]["AccountResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseActivityResponse: {
            readonly data?: readonly components["schemas"]["ActivityResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseContactResponse: {
            readonly data?: readonly components["schemas"]["ContactResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseCustomFieldResponse: {
            readonly data?: readonly components["schemas"]["CustomFieldResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseCustomFieldValuesResponse: {
            readonly data?: readonly components["schemas"]["CustomFieldValuesResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseImportErrorResponse: {
            readonly data?: readonly components["schemas"]["ImportErrorResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseImportJobResponse: {
            readonly data?: readonly components["schemas"]["ImportJobResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseLeadResponse: {
            readonly data?: readonly components["schemas"]["LeadResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseOpportunityResponse: {
            readonly data?: readonly components["schemas"]["OpportunityResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponsePipelineResponse: {
            readonly data?: readonly components["schemas"]["PipelineResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseStageResponse: {
            readonly data?: readonly components["schemas"]["StageResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseTimelineEventResponse: {
            readonly data?: readonly components["schemas"]["TimelineEventResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly Meta: {
            /** Format: uuid */
            readonly requestId?: string;
            /** Format: date-time */
            readonly timestamp?: string;
        };
        readonly MoveOpportunityRequest: {
            /** Format: uuid */
            readonly stageId: string;
            readonly status?: string;
            readonly reason?: string;
        };
        readonly OpportunityResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            /** Format: uuid */
            readonly accountId?: string;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly pipelineId?: string;
            /** Format: uuid */
            readonly stageId?: string;
            readonly name?: string;
            readonly amount?: number;
            readonly currencyCode?: string;
            readonly probability?: number;
            readonly status?: string;
            readonly winLossReason?: string;
            /** Format: date */
            readonly expectedCloseDate?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly OpportunitySummaryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly name?: string;
            readonly amount?: number;
            readonly currencyCode?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly Page: {
            readonly nextCursor?: string;
            readonly hasMore?: boolean;
            /** Format: int32 */
            readonly limit?: number;
        };
        readonly PipelineResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly name?: string;
            readonly currencyCode?: string;
            readonly active?: boolean;
            readonly stages?: readonly components["schemas"]["StageResponse"][];
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly SingleResponseAccountResponse: {
            readonly data?: components["schemas"]["AccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseActivityResponse: {
            readonly data?: components["schemas"]["ActivityResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseArchiveAccountResponse: {
            readonly data?: components["schemas"]["ArchiveAccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseContactResponse: {
            readonly data?: components["schemas"]["ContactResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseCustomFieldResponse: {
            readonly data?: components["schemas"]["CustomFieldResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseCustomFieldValuesResponse: {
            readonly data?: components["schemas"]["CustomFieldValuesResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseCustomer360Response: {
            readonly data?: components["schemas"]["Customer360Response"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseImportJobResponse: {
            readonly data?: components["schemas"]["ImportJobResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseImportRunResponse: {
            readonly data?: components["schemas"]["ImportRunResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseLeadConversionResponse: {
            readonly data?: components["schemas"]["LeadConversionResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseLeadResponse: {
            readonly data?: components["schemas"]["LeadResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseOpportunityResponse: {
            readonly data?: components["schemas"]["OpportunityResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponsePipelineResponse: {
            readonly data?: components["schemas"]["PipelineResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly StageResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly pipelineId?: string;
            readonly name?: string;
            /** Format: int32 */
            readonly sequence?: number;
            readonly probability?: number;
            readonly terminalState?: string;
            readonly active?: boolean;
        };
        readonly TimelineEventResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly subjectType?: string;
            /** Format: uuid */
            readonly subjectId?: string;
            readonly eventType?: string;
            readonly summary?: string;
            readonly sourceType?: string;
            /** Format: uuid */
            readonly sourceId?: string;
            /** Format: date-time */
            readonly occurredAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
        };
        readonly UpdateAccountRequest: {
            readonly displayName?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: uuid */
            readonly parentAccountId?: string;
            readonly primaryCurrencyCode?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly source?: string;
        };
        readonly UpdateActivityRequest: {
            readonly subject?: string;
            readonly body?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly startAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
        };
        readonly UpdateContactRequest: {
            /** Format: uuid */
            readonly accountId?: string;
            readonly givenName?: string;
            readonly familyName?: string;
            /** Format: email */
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly consentSummary?: string;
        };
        readonly UpdateCustomFieldRequest: {
            readonly labelAr?: string;
            readonly labelEn?: string;
            readonly sensitive?: boolean;
            readonly searchable?: boolean;
            readonly required?: boolean;
        };
        readonly UpdateCustomFieldValuesRequest: {
            readonly values: {
                readonly [key: string]: unknown;
            };
        };
        readonly UpdateLeadStatusRequest: {
            readonly status: string;
            readonly reason?: string;
        };
        readonly UpdateOpportunityRequest: {
            readonly name?: string;
            readonly amount?: number;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: date */
            readonly expectedCloseDate?: string;
        };
        readonly UpdatePipelineRequest: {
            readonly name?: string;
            readonly currencyCode?: string;
        };
        readonly FieldError: {
            readonly field: string;
            readonly code: string;
            readonly message: string;
        };
        readonly ApiErrorResponse: {
            /** Format: date-time */
            readonly timestamp?: string;
            /** Format: int32 */
            readonly status?: number;
            readonly error?: string;
            readonly message?: string;
            readonly path?: string;
            readonly tenantIds?: readonly string[];
        };
        readonly ErrorResponse: components["schemas"]["ApiErrorResponse"];
    };
    responses: {
        /** @description Validation error */
        readonly ValidationError: {
            headers: {
                readonly [name: string]: unknown;
            };
            content: {
                readonly "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Resource not found */
        readonly NotFound: {
            headers: {
                readonly [name: string]: unknown;
            };
            content: {
                readonly "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Idempotency or state conflict */
        readonly Conflict: {
            headers: {
                readonly [name: string]: unknown;
            };
            content: {
                readonly "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Stale ETag / optimistic concurrency conflict */
        readonly ConcurrencyConflict: {
            headers: {
                readonly [name: string]: unknown;
            };
            content: {
                readonly "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description If-Match header required */
        readonly PreconditionRequired: {
            headers: {
                readonly [name: string]: unknown;
            };
            content: {
                readonly "application/json": components["schemas"]["ErrorResponse"];
            };
        };
    };
    parameters: {
        readonly Limit: number;
        /** @description Opaque tenant-bound pagination cursor. */
        readonly Cursor: string;
        readonly Sort: string;
        readonly Direction: "asc" | "desc";
        /** @description Strong ETag required for versioned mutations. */
        readonly IfMatch: string;
        /** @description Required for governed idempotent POST operations. */
        readonly IdempotencyKey: string;
    };
    requestBodies: never;
    headers: {
        /** @description Strong version ETag. Send it back as If-Match on mutations. */
        readonly ETag: string;
    };
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
    readonly listAccounts: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
                readonly search?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseAccountResponse"];
                };
            };
        };
    };
    readonly createAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateAccountRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
        };
    };
    readonly updateAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateAccountRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly archiveAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseArchiveAccountResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly customer360: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomer360Response"];
                };
            };
        };
    };
    readonly restoreAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listActivities: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
                readonly relatedType?: string;
                readonly relatedId?: string;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseActivityResponse"];
                };
            };
        };
    };
    readonly createActivity: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateActivityRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getActivity: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly activityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
        };
    };
    readonly updateActivity: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly activityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateActivityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly completeActivity: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly activityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CompleteActivityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listContacts: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
                readonly accountId?: string;
                readonly search?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseContactResponse"];
                };
            };
        };
    };
    readonly createContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateContactRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
        };
    };
    readonly updateContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateContactRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly archiveContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly restoreContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listCustomFields: {
        readonly parameters: {
            readonly query?: {
                readonly entityType?: string;
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseCustomFieldResponse"];
                };
            };
        };
    };
    readonly createCustomField: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateCustomFieldRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly searchCustomFieldValues: {
        readonly parameters: {
            readonly query: {
                readonly entityType: string;
                readonly fieldKey: string;
                readonly query: string;
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseCustomFieldValuesResponse"];
                };
            };
        };
    };
    readonly readCustomFieldValues: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly entityType: string;
                readonly entityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldValuesResponse"];
                };
            };
        };
    };
    readonly upsertCustomFieldValues: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly entityType: string;
                readonly entityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateCustomFieldValuesRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldValuesResponse"];
                };
            };
        };
    };
    readonly upsertCustomFieldValuesPost: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly entityType: string;
                readonly entityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateCustomFieldValuesRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldValuesResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly updateCustomField: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly customFieldId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateCustomFieldRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listImportJobs: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseImportJobResponse"];
                };
            };
        };
    };
    readonly uploadImport: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "multipart/form-data": {
                    readonly entityType: string;
                    readonly mapping?: string;
                    /** Format: binary */
                    readonly file: string;
                };
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportJobResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getImportJob: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly jobId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportJobResponse"];
                };
            };
        };
    };
    readonly cancelImport: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly jobId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportJobResponse"];
                };
            };
        };
    };
    readonly listImportErrors: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly jobId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseImportErrorResponse"];
                };
            };
        };
    };
    readonly downloadImportErrors: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly jobId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "text/csv": string;
                };
            };
        };
    };
    readonly runImport: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly jobId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportRunResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly listLeads: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseLeadResponse"];
                };
            };
        };
    };
    readonly createLead: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateLeadRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getLead: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly leadId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadResponse"];
                };
            };
        };
    };
    readonly convertLead: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly leadId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ConvertLeadRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadConversionResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly changeLeadStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly leadId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateLeadStatusRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listOpportunities: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
                readonly accountId?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseOpportunityResponse"];
                };
            };
        };
    };
    readonly createOpportunity: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateOpportunityRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
            readonly 400: components["responses"]["ValidationError"];
            readonly 409: components["responses"]["Conflict"];
        };
    };
    readonly getOpportunity: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly opportunityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
        };
    };
    readonly updateOpportunity: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly opportunityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateOpportunityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly moveOpportunityStage: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly opportunityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["MoveOpportunityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listPipelines_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponsePipelineResponse"];
                };
            };
        };
    };
    readonly updatePipeline: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly pipelineId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdatePipelineRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly ETag: components["headers"]["ETag"];
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponsePipelineResponse"];
                };
            };
            readonly 412: components["responses"]["ConcurrencyConflict"];
            readonly 428: components["responses"]["PreconditionRequired"];
        };
    };
    readonly listPipelineStages: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly pipelineId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseStageResponse"];
                };
            };
        };
    };
    readonly timeline: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
                readonly sort?: string;
                readonly direction?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly subjectType: string;
                readonly subjectId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseTimelineEventResponse"];
                };
            };
        };
    };
}
