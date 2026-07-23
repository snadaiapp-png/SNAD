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
    readonly "/accounts/{accountId}/addresses": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["accountAddresses"];
        readonly put?: never;
        readonly post: operations["createAccountAddress"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/accounts/{accountId}/communication-methods": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["accountCommunicationMethods"];
        readonly put?: never;
        readonly post: operations["createAccountCommunicationMethod"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/accounts/{accountId}/contact-relationships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["relationshipsByAccount"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/addresses/export": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["exportAddresses"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/addresses/import": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["importAddresses"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/addresses/search": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["searchAddresses"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/addresses/{addressId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["address"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateAddress"];
        readonly trace?: never;
    };
    readonly "/addresses/{addressId}/archive": {
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
        readonly patch: operations["archiveAddress"];
        readonly trace?: never;
    };
    readonly "/addresses/{addressId}/history": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["addressHistory"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/addresses/{addressId}/primary": {
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
        readonly patch: operations["setPrimaryAddress"];
        readonly trace?: never;
    };
    readonly "/addresses/{addressId}/reactivate": {
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
        readonly patch: operations["reactivateAddress"];
        readonly trace?: never;
    };
    readonly "/assignment-rules": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listRules"];
        readonly put?: never;
        readonly post: operations["createRule"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignment-rules/{ruleId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getRule"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignment-rules/{ruleId}/simulate": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["simulateRule"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignment-rules/{ruleId}/versions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["createRuleVersion"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignment-rules/{ruleId}/versions/{version}/activate": {
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
        readonly patch: operations["activateRuleVersion"];
        readonly trace?: never;
    };
    readonly "/assignments/bulk-reassign": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["bulkReassign"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignments/reassign": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["reassign"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/assignments/{recordType}/{recordId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getCurrentAssignment"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/communication-methods/export": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["exportCommunicationMethods"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/communication-methods/import": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["importCommunicationMethods"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/communication-methods/search": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["searchCommunicationMethods"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["communicationMethod"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateCommunicationMethod"];
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}/archive": {
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
        readonly patch: operations["archiveCommunicationMethod"];
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}/history": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["communicationHistory"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}/preferred": {
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
        readonly patch: operations["setPreferredCommunicationMethod"];
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}/reactivate": {
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
        readonly patch: operations["reactivateCommunicationMethod"];
        readonly trace?: never;
    };
    readonly "/communication-methods/{communicationMethodId}/verification": {
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
        readonly patch: operations["changeVerification"];
        readonly trace?: never;
    };
    readonly "/communication-policy": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["policy"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["update_2"];
        readonly trace?: never;
    };
    readonly "/contact-relationship-imports": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["importRows"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["relationship"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateRelationship"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/activate": {
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
        readonly patch: operations["activate"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/archive": {
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
        readonly patch: operations["archive"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/commands": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["command"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/deactivate": {
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
        readonly patch: operations["deactivate"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/history": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["relationshipHistory"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/primary": {
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
        readonly patch: operations["setPrimary"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/reactivate": {
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
        readonly patch: operations["reactivate"];
        readonly trace?: never;
    };
    readonly "/contact-relationships/{relationshipId}/versioned": {
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
        readonly patch: operations["updateRelationship_1"];
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
    readonly "/contacts/{contactId}/addresses": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["contactAddresses"];
        readonly put?: never;
        readonly post: operations["createContactAddress"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/contacts/{contactId}/communication-methods": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["contactCommunicationMethods"];
        readonly put?: never;
        readonly post: operations["createContactCommunicationMethod"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/ownership-history": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["ownershipHistory"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/profile": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["profile"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateProfile"];
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/profile-versioned": {
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
        readonly patch: operations["updateProfile_1"];
        readonly trace?: never;
    };
    readonly "/contacts/{contactId}/relationships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["relationshipsByContact"];
        readonly put?: never;
        readonly post: operations["createRelationship"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/integrations/ai": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["requestAi"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/integrations/{requestId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["status"];
        readonly put?: never;
        readonly post?: never;
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
    readonly "/my-work": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myWork"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/ownership-history/{recordType}/{recordId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["history"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
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
    readonly "/queues": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listQueues"];
        readonly put?: never;
        readonly post: operations["createQueue"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/queues/{queueId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getQueue"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateQueue"];
        readonly trace?: never;
    };
    readonly "/queues/{queueId}/items": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listQueueItems"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/queues/{queueId}/items/{itemId}/claim": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["claimQueueItem"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/queues/{queueId}/items/{itemId}/release": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["releaseQueueItem"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/queues/{queueId}/memberships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listQueueMemberships"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/relationship-roles": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["roles"];
        readonly put?: never;
        readonly post: operations["createRole"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/teams": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTeams"];
        readonly put?: never;
        readonly post: operations["createTeam"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/teams/{teamId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getTeam"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateTeam"];
        readonly trace?: never;
    };
    readonly "/teams/{teamId}/memberships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTeamMemberships"];
        readonly put?: never;
        readonly post: operations["addTeamMembership"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/teams/{teamId}/memberships/{membershipId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["endTeamMembership"];
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateTeamMembership"];
        readonly trace?: never;
    };
    readonly "/territories": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTerritories"];
        readonly put?: never;
        readonly post: operations["createTerritory"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/territories/{territoryId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getTerritory"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateTerritory"];
        readonly trace?: never;
    };
    readonly "/territories/{territoryId}/assignments": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["assignTerritory"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/territories/{territoryId}/assignments/{assignmentId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["removeTerritoryAssignment"];
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
    readonly "/transfers": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTransfers"];
        readonly put?: never;
        readonly post: operations["createTransfer"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/transfers/{transferId}/approve": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["decideTransfer"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/transfers/{transferId}/cancel": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["cancelTransfer"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/transfers/{transferId}/submit": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["submitTransfer"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/integrations/{requestId}/confirm": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /**
         * Confirm AI recommendation
         * @description Human confirmation of an AI recommendation. Requires CRM.AI.CONFIRM capability. Idempotent via Idempotency-Key. Accepts If-Match for optimistic concurrency.
         */
        readonly post: operations["confirmCrmAiRecommendation"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/integrations/{requestId}/reject": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /**
         * Reject AI recommendation
         * @description Human rejection of an AI recommendation. Requires CRM.AI.CONFIRM capability. Idempotent via Idempotency-Key.
         */
        readonly post: operations["rejectCrmAiRecommendation"];
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
        readonly AddTeamMembershipRequest: {
            /** Format: uuid */
            readonly userId: string;
            /** @enum {string} */
            readonly role: "SALES_MANAGER" | "ACCOUNT_MANAGER" | "SALES_REPRESENTATIVE" | "LEAD_QUALIFIER" | "OPPORTUNITY_SPECIALIST" | "READONLY_CONTRIBUTOR";
            readonly primary?: boolean;
            /** Format: int32 */
            readonly capacityMax?: number;
            readonly metadata?: string;
        };
        readonly AddressHistoryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly addressId?: string;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly eventType?: string;
            /** Format: int64 */
            readonly previousVersion?: number;
            /** Format: int64 */
            readonly newVersion?: number;
            readonly snapshot?: string;
            /** Format: uuid */
            readonly changedBy?: string;
            /** Format: date-time */
            readonly changedAt?: string;
        };
        readonly AddressImportItem: {
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly address?: components["schemas"]["CreateAddressRequest"];
        };
        readonly AddressImportRequest: {
            readonly rows: readonly components["schemas"]["AddressImportItem"][];
        };
        readonly AddressResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly addressType?: string;
            readonly label?: string;
            readonly rawFormattedAddress?: string;
            readonly line1?: string;
            readonly line2?: string;
            readonly line3?: string;
            readonly district?: string;
            readonly city?: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode?: string;
            readonly countryExtensionJson?: string;
            readonly latitude?: number;
            readonly longitude?: number;
            readonly primaryAddress?: boolean;
            readonly verified?: boolean;
            readonly verificationSource?: string;
            readonly status?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: date-time */
            readonly archivedAt?: string;
        };
        readonly AddressSearchResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly addressType?: string;
            readonly label?: string;
            readonly rawFormattedAddress?: string;
            readonly line1?: string;
            readonly line2?: string;
            readonly district?: string;
            readonly city?: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode?: string;
            readonly primaryAddress?: boolean;
            readonly verified?: boolean;
            readonly status?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly AiRequest: {
            /** @enum {string} */
            readonly capability: "CUSTOMER_SUMMARY" | "NEXT_BEST_ACTION" | "SCORING";
            readonly sourceEntityType: string;
            /** Format: uuid */
            readonly sourceEntityId: string;
            /** Format: int64 */
            readonly sourceEntityVersion?: number;
            readonly dataClassification: string;
            readonly payload: components["schemas"]["JsonNode"];
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
        readonly AssignTerritoryRequest: {
            /** @enum {string} */
            readonly assigneeType: "USER" | "TEAM";
            /** Format: uuid */
            readonly assigneeId: string;
            /** @enum {string} */
            readonly role?: "PRIMARY" | "BACKUP" | "OBSERVER";
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly effectiveFrom?: string;
            /** Format: date-time */
            readonly effectiveTo?: string;
        };
        readonly Assignment: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly subjectType?: string;
            /** Format: uuid */
            readonly subjectId?: string;
            /** Format: uuid */
            readonly assignedUserId?: string;
            readonly assignmentRole?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "ENDED" | "CANCELLED";
            /** Format: date-time */
            readonly startsAt?: string;
            /** Format: date-time */
            readonly endsAt?: string;
            readonly reason?: string;
            /** @enum {string} */
            readonly ownerType?: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: uuid */
            readonly ownerTeamId?: string;
            /** Format: uuid */
            readonly ownerQueueId?: string;
            /** @enum {string} */
            readonly recordType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: uuid */
            readonly recordId?: string;
            /** Format: uuid */
            readonly assignedByRuleId?: string;
            /** Format: uuid */
            readonly assignedByUserId?: string;
            /** Format: uuid */
            readonly correlationId?: string;
            readonly workflowResult?: string;
            /** Format: date-time */
            readonly effectiveFrom?: string;
            /** Format: date-time */
            readonly effectiveTo?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
        };
        readonly AssignmentDecision: {
            readonly matched?: boolean;
            /** Format: uuid */
            readonly ruleId?: string;
            /** Format: int32 */
            readonly ruleVersion?: number;
            /** @enum {string} */
            readonly distributionMethod?: "DIRECT_OWNER" | "TEAM_ASSIGNMENT" | "QUEUE_ASSIGNMENT" | "ROUND_ROBIN" | "LEAST_LOADED" | "WEIGHTED" | "TERRITORY_BASED" | "SKILL_BASED" | "RULE_CHAIN";
            /** @enum {string} */
            readonly ownerType?: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly ownerId?: string;
            readonly fallbackUsed?: boolean;
            readonly trace?: readonly string[];
        };
        readonly AssignmentRule: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly code?: string;
            /** Format: int32 */
            readonly currentVersion?: number;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "DEPRECATED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
        };
        readonly AssignmentRuleVersion: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly ruleId?: string;
            /** Format: int32 */
            readonly version?: number;
            readonly displayName?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly recordType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: int32 */
            readonly priority?: number;
            readonly matchConditions?: string;
            /** @enum {string} */
            readonly distributionMethod?: "DIRECT_OWNER" | "TEAM_ASSIGNMENT" | "QUEUE_ASSIGNMENT" | "ROUND_ROBIN" | "LEAST_LOADED" | "WEIGHTED" | "TERRITORY_BASED" | "SKILL_BASED" | "RULE_CHAIN";
            /** Format: uuid */
            readonly targetOwnerId?: string;
            /** Format: uuid */
            readonly targetTeamId?: string;
            /** Format: uuid */
            readonly targetQueueId?: string;
            /** Format: uuid */
            readonly fallbackOwnerId?: string;
            /** Format: date-time */
            readonly effectiveFrom?: string;
            /** Format: date-time */
            readonly effectiveTo?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "DEPRECATED";
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            readonly active?: boolean;
        };
        readonly BulkAssignmentResponse: {
            readonly assignments?: readonly components["schemas"]["Assignment"][];
        };
        readonly BulkReassignRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            readonly recordIds: readonly string[];
            /** @enum {string} */
            readonly ownerType: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly ownerId: string;
            readonly reason: string;
        };
        readonly CancelTransferRequest: {
            readonly reason: string;
        };
        readonly ClaimQueueItemRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
        };
        readonly ClaimResult: {
            readonly assignment?: components["schemas"]["Assignment"];
            readonly replayed?: boolean;
        };
        readonly CommunicationHistoryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly communicationMethodId?: string;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly eventType?: string;
            /** Format: int64 */
            readonly previousVersion?: number;
            /** Format: int64 */
            readonly newVersion?: number;
            readonly snapshot?: string;
            /** Format: uuid */
            readonly changedBy?: string;
            /** Format: date-time */
            readonly changedAt?: string;
        };
        readonly CommunicationImportItem: {
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly communication?: components["schemas"]["CreateCommunicationMethodRequest"];
        };
        readonly CommunicationImportRequest: {
            readonly rows: readonly components["schemas"]["CommunicationImportItem"][];
        };
        readonly CommunicationMethodResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly methodType?: string;
            readonly rawValue?: string;
            readonly normalizedValue?: string;
            readonly displayValue?: string;
            readonly label?: string;
            readonly preferred?: boolean;
            readonly verified?: boolean;
            readonly verificationStatus?: string;
            /** Format: date-time */
            readonly verifiedAt?: string;
            readonly privacyClassification?: string;
            readonly consentStateReference?: string;
            readonly usagePurpose?: string;
            readonly status?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: date-time */
            readonly archivedAt?: string;
        };
        readonly CommunicationPolicy: {
            readonly emailUniqueWithinOwner?: boolean;
            readonly phoneUniqueWithinOwner?: boolean;
            readonly singlePreferredPerType?: boolean;
        };
        readonly CommunicationSearchResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly ownerType?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            readonly methodType?: string;
            readonly displayValue?: string;
            readonly label?: string;
            readonly preferred?: boolean;
            readonly verified?: boolean;
            readonly verificationStatus?: string;
            readonly privacyClassification?: string;
            readonly consentStateReference?: string;
            readonly usagePurpose?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CompleteActivityRequest: {
            readonly result?: string;
        };
        readonly ContactProfileResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly legalName?: string;
            readonly preferredName?: string;
            readonly givenName?: string;
            readonly middleName?: string;
            readonly familyName?: string;
            readonly displayName?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly pronouns?: string;
            readonly lifecycleStatus?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly source?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ContactRelationshipImportRequest: {
            /** Format: uuid */
            readonly importId?: string;
            readonly rows?: readonly components["schemas"]["ImportRow"][];
        };
        readonly ContactRelationshipResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly contactDisplayName?: string;
            readonly accountDisplayName?: string;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly customRoleId?: string;
            readonly customRoleNameAr?: string;
            readonly customRoleNameEn?: string;
            readonly status?: string;
            readonly primaryRelationship?: boolean;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            readonly jobTitle?: string;
            readonly department?: string;
            readonly decisionAuthority?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
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
        readonly CreateAddressRequest: {
            readonly addressType: string;
            readonly label?: string;
            readonly rawFormattedAddress?: string;
            readonly line1: string;
            readonly line2?: string;
            readonly line3?: string;
            readonly district?: string;
            readonly city: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode: string;
            readonly countryExtension?: components["schemas"]["JsonNode"];
            readonly latitude?: number;
            readonly longitude?: number;
            readonly primaryAddress?: boolean;
            readonly verified?: boolean;
            readonly verificationSource?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
        };
        readonly CreateCommunicationMethodRequest: {
            readonly methodType: string;
            readonly rawValue: string;
            readonly displayValue?: string;
            readonly label?: string;
            readonly preferred?: boolean;
            readonly privacyClassification?: string;
            readonly consentStateReference?: string;
            readonly usagePurpose?: string;
            readonly countryHint?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
        };
        readonly CreateContactRelationshipRequest: {
            /** Format: uuid */
            readonly accountId?: string;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly customRoleId?: string;
            readonly primaryRelationship?: boolean;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            readonly jobTitle?: string;
            readonly department?: string;
            readonly decisionAuthority?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
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
        readonly CreateQueueRequest: {
            readonly code: string;
            readonly displayName: string;
            /** @enum {string} */
            readonly recordType: "LEAD" | "OPPORTUNITY" | "TASK" | "ACTIVITY" | "ACCOUNT";
            readonly description?: string;
            /** Format: int32 */
            readonly maxItemsPerUser?: number;
            /** Format: int32 */
            readonly slaMinutes?: number;
            /** Format: uuid */
            readonly escalationTargetQueueId?: string;
            /** Format: uuid */
            readonly defaultOwnerId?: string;
        };
        readonly CreateRelationshipRoleRequest: {
            readonly code?: string;
            readonly nameAr?: string;
            readonly nameEn?: string;
        };
        readonly CreateRuleRequest: {
            readonly code: string;
            readonly definition: components["schemas"]["VersionDefinitionRequest"];
        };
        readonly CreateTeamRequest: {
            readonly code: string;
            readonly displayName: string;
            readonly description?: string;
            /** Format: uuid */
            readonly managerUserId?: string;
            /** Format: uuid */
            readonly defaultQueueId?: string;
            /** Format: uuid */
            readonly defaultTerritoryId?: string;
        };
        readonly CreateTerritoryRequest: {
            readonly code: string;
            readonly displayName: string;
            /** Format: uuid */
            readonly parentId?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly ruleType: "GEOGRAPHIC" | "SEGMENT" | "CHANNEL" | "ACCOUNT_LIST";
            readonly ruleDefinition?: string;
            /** Format: int32 */
            readonly priority?: number;
        };
        readonly CreateTransferRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            readonly recordIds: readonly string[];
            /** @enum {string} */
            readonly proposedOwnerType: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly proposedOwnerId: string;
            /** @enum {string} */
            readonly transferType: "PERMANENT" | "TEMPORARY";
            /** Format: date-time */
            readonly temporaryEndDate?: string;
            readonly reason: string;
            /** @enum {string} */
            readonly policy: "SINGLE_APPROVER" | "MULTI_APPROVER" | "NO_APPROVAL_REQUIRED";
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
        readonly DecideTransferRequest: {
            /** @enum {string} */
            readonly decision: "APPROVED" | "REJECTED" | "EXPIRED" | "CANCELLED";
            readonly comment?: string;
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
        readonly ImportResult: {
            /** Format: uuid */
            readonly importId?: string;
            /** Format: int32 */
            readonly totalRows?: number;
            /** Format: int32 */
            readonly succeededRows?: number;
            /** Format: int32 */
            readonly failedRows?: number;
            readonly rows?: readonly components["schemas"]["RowResult"][];
        };
        readonly ImportRow: {
            readonly personKey?: string;
            /** Format: uuid */
            readonly contactId?: string;
            readonly legalName?: string;
            readonly preferredName?: string;
            readonly givenName?: string;
            readonly middleName?: string;
            readonly familyName?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly pronouns?: string;
            /** Format: uuid */
            readonly personOwnerUserId?: string;
            readonly source?: string;
            readonly consentSummary?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly customRoleId?: string;
            readonly primaryRelationship?: boolean;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            readonly jobTitle?: string;
            readonly department?: string;
            readonly decisionAuthority?: string;
            /** Format: uuid */
            readonly relationshipOwnerUserId?: string;
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
        readonly JsonNode: unknown;
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
        readonly ListResponseAddressHistoryResponse: {
            readonly data?: readonly components["schemas"]["AddressHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseAddressResponse: {
            readonly data?: readonly components["schemas"]["AddressResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseAddressSearchResponse: {
            readonly data?: readonly components["schemas"]["AddressSearchResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseCommunicationHistoryResponse: {
            readonly data?: readonly components["schemas"]["CommunicationHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseCommunicationMethodResponse: {
            readonly data?: readonly components["schemas"]["CommunicationMethodResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseCommunicationSearchResponse: {
            readonly data?: readonly components["schemas"]["CommunicationSearchResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseContactRelationshipResponse: {
            readonly data?: readonly components["schemas"]["ContactRelationshipResponse"][];
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
        readonly ListResponseOwnershipHistoryResponse: {
            readonly data?: readonly components["schemas"]["OwnershipHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponsePipelineResponse: {
            readonly data?: readonly components["schemas"]["PipelineResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseRelationshipHistoryResponse: {
            readonly data?: readonly components["schemas"]["RelationshipHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseRelationshipRoleResponse: {
            readonly data?: readonly components["schemas"]["RelationshipRoleResponse"][];
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
        readonly MyWorkResponse: {
            readonly workload?: components["schemas"]["WorkloadSummary"];
            /** Format: int32 */
            readonly activeQueueClaims?: number;
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
        readonly OwnershipHistory: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** @enum {string} */
            readonly recordType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: uuid */
            readonly recordId?: string;
            /** @enum {string} */
            readonly fromOwnerType?: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly fromOwnerUserId?: string;
            /** Format: uuid */
            readonly fromOwnerTeamId?: string;
            /** Format: uuid */
            readonly fromOwnerQueueId?: string;
            /** @enum {string} */
            readonly toOwnerType?: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly toOwnerUserId?: string;
            /** Format: uuid */
            readonly toOwnerTeamId?: string;
            /** Format: uuid */
            readonly toOwnerQueueId?: string;
            /** @enum {string} */
            readonly changeType?: "INITIAL" | "REASSIGN" | "TRANSFER" | "QUEUE_CLAIM" | "QUEUE_RELEASE" | "TEMPORARY" | "RESTORE" | "BULK";
            /** @enum {string} */
            readonly triggerSource?: "MANUAL" | "RULE" | "TRANSFER_REQUEST" | "WORKFLOW" | "ABSENCE_POLICY";
            /** Format: uuid */
            readonly triggerReferenceId?: string;
            /** Format: uuid */
            readonly actorUserId?: string;
            readonly reason?: string;
            /** Format: uuid */
            readonly correlationId?: string;
            /** Format: date-time */
            readonly effectiveAt?: string;
            /** Format: date-time */
            readonly recordedAt?: string;
        };
        readonly OwnershipHistoryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly previousOwnerUserId?: string;
            /** Format: uuid */
            readonly newOwnerUserId?: string;
            /** Format: uuid */
            readonly changedBy?: string;
            /** Format: date-time */
            readonly changedAt?: string;
            readonly reason?: string;
        };
        readonly OwnershipListResponseAssignmentRule: {
            readonly data?: readonly components["schemas"]["AssignmentRule"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseOwnershipHistory: {
            readonly data?: readonly components["schemas"]["OwnershipHistory"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseQueue: {
            readonly data?: readonly components["schemas"]["Queue"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseQueueItemSummary: {
            readonly data?: readonly components["schemas"]["QueueItemSummary"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseQueueMembership: {
            readonly data?: readonly components["schemas"]["QueueMembership"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseSalesTeam: {
            readonly data?: readonly components["schemas"]["SalesTeam"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseTeamMembership: {
            readonly data?: readonly components["schemas"]["TeamMembership"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseTerritory: {
            readonly data?: readonly components["schemas"]["Territory"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipListResponseTransferRequest: {
            readonly data?: readonly components["schemas"]["TransferRequest"][];
            readonly page?: components["schemas"]["OwnershipPage"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipMeta: {
            /** Format: uuid */
            readonly requestId?: string;
            /** Format: uuid */
            readonly correlationId?: string;
            /** Format: date-time */
            readonly timestamp?: string;
        };
        readonly OwnershipPage: {
            readonly nextCursor?: string;
            readonly hasMore?: boolean;
            /** Format: int32 */
            readonly limit?: number;
        };
        readonly OwnershipResponseAssignment: {
            readonly data?: components["schemas"]["Assignment"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseAssignmentDecision: {
            readonly data?: components["schemas"]["AssignmentDecision"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseAssignmentRule: {
            readonly data?: components["schemas"]["AssignmentRule"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseAssignmentRuleVersion: {
            readonly data?: components["schemas"]["AssignmentRuleVersion"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseBulkAssignmentResponse: {
            readonly data?: components["schemas"]["BulkAssignmentResponse"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseClaimResult: {
            readonly data?: components["schemas"]["ClaimResult"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseMyWorkResponse: {
            readonly data?: components["schemas"]["MyWorkResponse"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseQueue: {
            readonly data?: components["schemas"]["Queue"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseQueueDetail: {
            readonly data?: components["schemas"]["QueueDetail"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseRuleDetail: {
            readonly data?: components["schemas"]["RuleDetail"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseSalesTeam: {
            readonly data?: components["schemas"]["SalesTeam"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTeamDetail: {
            readonly data?: components["schemas"]["TeamDetail"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTeamMembership: {
            readonly data?: components["schemas"]["TeamMembership"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTerritory: {
            readonly data?: components["schemas"]["Territory"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTerritoryAssignment: {
            readonly data?: components["schemas"]["TerritoryAssignment"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTerritoryDetail: {
            readonly data?: components["schemas"]["TerritoryDetail"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
        };
        readonly OwnershipResponseTransferRequest: {
            readonly data?: components["schemas"]["TransferRequest"];
            readonly meta?: components["schemas"]["OwnershipMeta"];
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
        readonly Queue: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly code?: string;
            readonly displayName?: string;
            /** @enum {string} */
            readonly recordType?: "LEAD" | "OPPORTUNITY" | "TASK" | "ACTIVITY" | "ACCOUNT";
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "DRAINING" | "ARCHIVED";
            /** Format: int32 */
            readonly maxItemsPerUser?: number;
            /** Format: int32 */
            readonly slaMinutes?: number;
            /** Format: uuid */
            readonly escalationTargetQueueId?: string;
            /** Format: uuid */
            readonly defaultOwnerId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly claimable?: boolean;
        };
        readonly QueueDetail: {
            readonly queue?: components["schemas"]["Queue"];
            /** Format: int64 */
            readonly waitingCount?: number;
            readonly memberships?: readonly components["schemas"]["QueueMembership"][];
        };
        readonly QueueItemSummary: {
            /** Format: uuid */
            readonly assignmentId?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly queueId?: string;
            /** @enum {string} */
            readonly recordType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: uuid */
            readonly recordId?: string;
            /** Format: date-time */
            readonly queuedAt?: string;
            readonly reason?: string;
            /** Format: uuid */
            readonly correlationId?: string;
        };
        readonly QueueMembership: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly queueId?: string;
            /** Format: uuid */
            readonly userId?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "ENDED" | "REMOVED";
            /** Format: date-time */
            readonly addedAt?: string;
            /** Format: date-time */
            readonly removedAt?: string;
            readonly removedReason?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
        };
        readonly ReassignRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: uuid */
            readonly recordId: string;
            /** @enum {string} */
            readonly ownerType: "USER" | "TEAM" | "QUEUE";
            /** Format: uuid */
            readonly ownerId: string;
            readonly reason: string;
            /** Format: uuid */
            readonly expectedAssignmentId?: string;
            /** Format: uuid */
            readonly assignedByRuleId?: string;
        };
        readonly RelationshipCommandRequest: {
            /** Format: int64 */
            readonly expectedVersion?: number;
            readonly action?: string;
        };
        readonly RelationshipHistoryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly relationshipId?: string;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly eventType?: string;
            /** Format: int64 */
            readonly previousVersion?: number;
            /** Format: int64 */
            readonly newVersion?: number;
            readonly snapshot?: string;
            /** Format: uuid */
            readonly changedBy?: string;
            /** Format: date-time */
            readonly changedAt?: string;
        };
        readonly RelationshipRoleResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly code?: string;
            readonly nameAr?: string;
            readonly nameEn?: string;
            readonly standard?: boolean;
            readonly active?: boolean;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ReleaseQueueItemRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            readonly reason: string;
        };
        readonly RowResult: {
            /** Format: int32 */
            readonly rowNumber?: number;
            readonly personKey?: string;
            readonly status?: string;
            /** Format: uuid */
            readonly contactId?: string;
            /** Format: uuid */
            readonly relationshipId?: string;
            readonly errorCode?: string;
            readonly errorMessage?: string;
        };
        readonly RuleDetail: {
            readonly rule?: components["schemas"]["AssignmentRule"];
            readonly versions?: readonly components["schemas"]["AssignmentRuleVersion"][];
        };
        readonly SalesTeam: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly code?: string;
            readonly displayName?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
            /** Format: uuid */
            readonly managerUserId?: string;
            /** Format: uuid */
            readonly defaultQueueId?: string;
            /** Format: uuid */
            readonly defaultTerritoryId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
            readonly archived?: boolean;
        };
        readonly SimulateRuleRequest: {
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            readonly facts?: {
                readonly [key: string]: unknown;
            };
            readonly territoryIds?: readonly string[];
        };
        readonly SingleResponseAccountResponse: {
            readonly data?: components["schemas"]["AccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseActivityResponse: {
            readonly data?: components["schemas"]["ActivityResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseAddressResponse: {
            readonly data?: components["schemas"]["AddressResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseArchiveAccountResponse: {
            readonly data?: components["schemas"]["ArchiveAccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseCommunicationMethodResponse: {
            readonly data?: components["schemas"]["CommunicationMethodResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseCommunicationPolicy: {
            readonly data?: components["schemas"]["CommunicationPolicy"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseContactProfileResponse: {
            readonly data?: components["schemas"]["ContactProfileResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly SingleResponseContactRelationshipResponse: {
            readonly data?: components["schemas"]["ContactRelationshipResponse"];
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
        readonly SingleResponseImportResult: {
            readonly data?: components["schemas"]["ImportResult"];
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
        readonly SingleResponseRelationshipRoleResponse: {
            readonly data?: components["schemas"]["RelationshipRoleResponse"];
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
        readonly StoredRequest: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly integrationType?: string;
            readonly status?: string;
            /** Format: uuid */
            readonly externalReference?: string;
            readonly correlationId?: string;
            readonly idempotencyKey?: string;
            /** Format: date-time */
            readonly requestedAt?: string;
            /** Format: date-time */
            readonly expiresAt?: string;
            readonly errorCode?: string;
        };
        readonly SubmitTransferRequest: {
            readonly approverUserIds?: readonly string[];
        };
        readonly TeamDetail: {
            readonly team?: components["schemas"]["SalesTeam"];
            readonly memberships?: readonly components["schemas"]["TeamMembership"][];
        };
        readonly TeamMembership: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly teamId?: string;
            /** Format: uuid */
            readonly userId?: string;
            /** @enum {string} */
            readonly role?: "SALES_MANAGER" | "ACCOUNT_MANAGER" | "SALES_REPRESENTATIVE" | "LEAD_QUALIFIER" | "OPPORTUNITY_SPECIALIST" | "READONLY_CONTRIBUTOR";
            readonly isPrimary?: boolean;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "ENDED" | "REMOVED";
            /** Format: date-time */
            readonly joinedAt?: string;
            /** Format: date-time */
            readonly leftAt?: string;
            readonly leftReason?: string;
            /** Format: int32 */
            readonly capacityMax?: number;
            readonly metadata?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
        };
        readonly Territory: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly code?: string;
            readonly displayName?: string;
            /** Format: uuid */
            readonly parentId?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "ARCHIVED";
            /** @enum {string} */
            readonly ruleType?: "GEOGRAPHIC" | "SEGMENT" | "CHANNEL" | "ACCOUNT_LIST";
            readonly ruleDefinition?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
            readonly root?: boolean;
        };
        readonly TerritoryAssignment: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly territoryId?: string;
            /** @enum {string} */
            readonly assigneeType?: "USER" | "TEAM";
            /** Format: uuid */
            readonly assigneeId?: string;
            /** @enum {string} */
            readonly role?: "PRIMARY" | "BACKUP" | "OBSERVER";
            /** Format: int32 */
            readonly priority?: number;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE";
            /** Format: date-time */
            readonly effectiveFrom?: string;
            /** Format: date-time */
            readonly effectiveTo?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: uuid */
            readonly createdBy?: string;
            /** Format: uuid */
            readonly updatedBy?: string;
            readonly active?: boolean;
            readonly primary?: boolean;
        };
        readonly TerritoryDetail: {
            readonly territory?: components["schemas"]["Territory"];
            readonly children?: readonly components["schemas"]["Territory"][];
            readonly assignments?: readonly components["schemas"]["TerritoryAssignment"][];
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
        readonly TransferRequest: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** @enum {string} */
            readonly recordType?: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            readonly recordIds?: readonly string[];
            /** Format: uuid */
            readonly requesterUserId?: string;
            /** Format: uuid */
            readonly currentOwnerUserId?: string;
            /** Format: uuid */
            readonly proposedOwnerUserId?: string;
            /** Format: uuid */
            readonly proposedOwnerTeamId?: string;
            /** @enum {string} */
            readonly transferType?: "PERMANENT" | "TEMPORARY";
            /** Format: date-time */
            readonly temporaryEndDate?: string;
            readonly reason?: string;
            /** @enum {string} */
            readonly policy?: "SINGLE_APPROVER" | "MULTI_APPROVER" | "NO_APPROVAL_REQUIRED";
            /** @enum {string} */
            readonly state?: "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED" | "FAILED";
            /** Format: int32 */
            readonly currentApprovalStep?: number;
            /** Format: uuid */
            readonly workflowRunId?: string;
            /** Format: date-time */
            readonly executedAt?: string;
            /** Format: uuid */
            readonly executedByUserId?: string;
            readonly failureReason?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            readonly terminal?: boolean;
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
        readonly UpdateAddressRequest: {
            readonly addressType?: string;
            readonly label?: string;
            readonly rawFormattedAddress?: string;
            readonly line1?: string;
            readonly line2?: string;
            readonly line3?: string;
            readonly district?: string;
            readonly city?: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode?: string;
            readonly countryExtension?: components["schemas"]["JsonNode"];
            readonly latitude?: number;
            readonly longitude?: number;
            readonly verified?: boolean;
            readonly verificationSource?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
        };
        readonly UpdateCommunicationMethodRequest: {
            readonly rawValue?: string;
            readonly displayValue?: string;
            readonly label?: string;
            readonly privacyClassification?: string;
            readonly consentStateReference?: string;
            readonly usagePurpose?: string;
            readonly countryHint?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
        };
        readonly UpdateContactProfileRequest: {
            readonly legalName?: string;
            readonly preferredName?: string;
            readonly givenName?: string;
            readonly middleName?: string;
            readonly familyName?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly pronouns?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly source?: string;
            readonly ownerChangeReason?: string;
        };
        readonly UpdateContactRelationshipRequest: {
            readonly roleCode?: string;
            /** Format: uuid */
            readonly customRoleId?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            readonly jobTitle?: string;
            readonly department?: string;
            readonly decisionAuthority?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
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
        readonly UpdateQueueRequest: {
            readonly displayName?: string;
            readonly descriptionSet?: boolean;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "DRAINING" | "ARCHIVED";
            /** Format: int32 */
            readonly maxItemsPerUser?: number;
            readonly slaMinutesSet?: boolean;
            /** Format: int32 */
            readonly slaMinutes?: number;
            readonly escalationTargetQueueIdSet?: boolean;
            /** Format: uuid */
            readonly escalationTargetQueueId?: string;
            readonly defaultOwnerIdSet?: boolean;
            /** Format: uuid */
            readonly defaultOwnerId?: string;
        };
        readonly UpdateTeamMembershipRequest: {
            /** @enum {string} */
            readonly role: "SALES_MANAGER" | "ACCOUNT_MANAGER" | "SALES_REPRESENTATIVE" | "LEAD_QUALIFIER" | "OPPORTUNITY_SPECIALIST" | "READONLY_CONTRIBUTOR";
            readonly primary?: boolean;
            /** Format: int32 */
            readonly capacityMax?: number;
            readonly metadata?: string;
        };
        readonly UpdateTeamRequest: {
            readonly displayName: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
            /** Format: uuid */
            readonly managerUserId?: string;
            /** Format: uuid */
            readonly defaultQueueId?: string;
            /** Format: uuid */
            readonly defaultTerritoryId?: string;
        };
        readonly UpdateTerritoryRequest: {
            readonly displayName?: string;
            readonly parentIdSet?: boolean;
            /** Format: uuid */
            readonly parentId?: string;
            readonly descriptionSet?: boolean;
            readonly description?: string;
            /** @enum {string} */
            readonly ruleType?: "GEOGRAPHIC" | "SEGMENT" | "CHANNEL" | "ACCOUNT_LIST";
            readonly ruleDefinitionSet?: boolean;
            readonly ruleDefinition?: string;
            /** Format: int32 */
            readonly priority?: number;
        };
        readonly VerificationRequest: {
            readonly verificationStatus: string;
        };
        readonly VersionDefinitionRequest: {
            readonly displayName: string;
            readonly description?: string;
            /** @enum {string} */
            readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
            /** Format: int32 */
            readonly priority?: number;
            readonly matchConditions?: string;
            /** @enum {string} */
            readonly distributionMethod: "DIRECT_OWNER" | "TEAM_ASSIGNMENT" | "QUEUE_ASSIGNMENT" | "ROUND_ROBIN" | "LEAST_LOADED" | "WEIGHTED" | "TERRITORY_BASED" | "SKILL_BASED" | "RULE_CHAIN";
            /** Format: uuid */
            readonly targetOwnerId?: string;
            /** Format: uuid */
            readonly targetTeamId?: string;
            /** Format: uuid */
            readonly targetQueueId?: string;
            /** Format: uuid */
            readonly fallbackOwnerId?: string;
            /** Format: date-time */
            readonly effectiveFrom?: string;
            /** Format: date-time */
            readonly effectiveTo?: string;
        };
        readonly VersionedContactProfileRequest: {
            /** Format: int64 */
            readonly expectedVersion?: number;
            readonly legalName?: string;
            readonly preferredName?: string;
            readonly givenName?: string;
            readonly middleName?: string;
            readonly familyName?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly preferredLocale?: string;
            readonly timeZone?: string;
            readonly pronouns?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            readonly source?: string;
            readonly ownerChangeReason?: string;
        };
        readonly VersionedRelationshipRequest: {
            /** Format: int64 */
            readonly expectedVersion?: number;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly customRoleId?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
            readonly jobTitle?: string;
            readonly department?: string;
            readonly decisionAuthority?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
        };
        readonly WorkloadSummary: {
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly ownerId?: string;
            /** @enum {string} */
            readonly ownerType?: "USER" | "TEAM" | "QUEUE";
            /** Format: int64 */
            readonly activeAssignments?: number;
            /** Format: int64 */
            readonly activeQueueItems?: number;
            /** Format: int64 */
            readonly activeTeamMemberships?: number;
            /** Format: int64 */
            readonly overdueTasks?: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
        };
    };
    readonly accountAddresses: {
        readonly parameters: {
            readonly query?: {
                readonly includeArchived?: boolean;
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseAddressResponse"];
                };
            };
        };
    };
    readonly createAccountAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateAddressRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseArchiveAccountResponse"];
                };
            };
        };
    };
    readonly accountCommunicationMethods: {
        readonly parameters: {
            readonly query?: {
                readonly includeArchived?: boolean;
                readonly methodType?: string;
                readonly verificationStatus?: string;
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly createAccountCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateCommunicationMethodRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly relationshipsByAccount: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseContactRelationshipResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAccountResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseActivityResponse"];
                };
            };
        };
    };
    readonly exportAddresses: {
        readonly parameters: {
            readonly query?: {
                readonly q?: string;
                readonly ownerType?: string;
                readonly addressType?: string;
                readonly countryCode?: string;
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
                    readonly "text/csv": string;
                };
            };
        };
    };
    readonly importAddresses: {
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
                readonly "application/json": components["schemas"]["AddressImportRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportResult"];
                };
            };
        };
    };
    readonly searchAddresses: {
        readonly parameters: {
            readonly query?: {
                readonly q?: string;
                readonly ownerType?: string;
                readonly addressType?: string;
                readonly countryCode?: string;
                readonly status?: string;
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
                    readonly "application/json": components["schemas"]["ListResponseAddressSearchResponse"];
                };
            };
        };
    };
    readonly address: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly addressId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
        };
    };
    readonly updateAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly addressId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateAddressRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
        };
    };
    readonly archiveAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly addressId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
        };
    };
    readonly addressHistory: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly addressId: string;
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
                    readonly "application/json": components["schemas"]["ListResponseAddressHistoryResponse"];
                };
            };
        };
    };
    readonly setPrimaryAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly addressId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
        };
    };
    readonly reactivateAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly addressId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
        };
    };
    readonly listRules: {
        readonly parameters: {
            readonly query?: {
                readonly status?: "ACTIVE" | "INACTIVE" | "DEPRECATED";
                readonly pageSize?: number;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseAssignmentRule"];
                };
            };
        };
    };
    readonly createRule: {
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
                readonly "application/json": components["schemas"]["CreateRuleRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignmentRule"];
                };
            };
        };
    };
    readonly getRule: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly ruleId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseRuleDetail"];
                };
            };
        };
    };
    readonly simulateRule: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly ruleId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SimulateRuleRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignmentDecision"];
                };
            };
        };
    };
    readonly createRuleVersion: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly ruleId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["VersionDefinitionRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignmentRuleVersion"];
                };
            };
        };
    };
    readonly activateRuleVersion: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly ruleId: string;
                readonly version: number;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignmentRuleVersion"];
                };
            };
        };
    };
    readonly bulkReassign: {
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
                readonly "application/json": components["schemas"]["BulkReassignRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseBulkAssignmentResponse"];
                };
            };
        };
    };
    readonly reassign: {
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
                readonly "application/json": components["schemas"]["ReassignRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignment"];
                };
            };
        };
    };
    readonly getCurrentAssignment: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
                readonly recordId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignment"];
                };
            };
        };
    };
    readonly exportCommunicationMethods: {
        readonly parameters: {
            readonly query?: {
                readonly q?: string;
                readonly ownerType?: string;
                readonly methodType?: string;
                readonly verificationStatus?: string;
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
                    readonly "text/csv": string;
                };
            };
        };
    };
    readonly importCommunicationMethods: {
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
                readonly "application/json": components["schemas"]["CommunicationImportRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportResult"];
                };
            };
        };
    };
    readonly searchCommunicationMethods: {
        readonly parameters: {
            readonly query?: {
                readonly q?: string;
                readonly ownerType?: string;
                readonly methodType?: string;
                readonly verificationStatus?: string;
                readonly status?: string;
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
                    readonly "application/json": components["schemas"]["ListResponseCommunicationSearchResponse"];
                };
            };
        };
    };
    readonly communicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly communicationMethodId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly updateCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly communicationMethodId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateCommunicationMethodRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly archiveCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly communicationMethodId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly communicationHistory: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly communicationMethodId: string;
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
                    readonly "application/json": components["schemas"]["ListResponseCommunicationHistoryResponse"];
                };
            };
        };
    };
    readonly setPreferredCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly communicationMethodId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly reactivateCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly communicationMethodId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly changeVerification: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly communicationMethodId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["VerificationRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly policy: {
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
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationPolicy"];
                };
            };
        };
    };
    readonly update_2: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CommunicationPolicy"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationPolicy"];
                };
            };
        };
    };
    readonly importRows: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ContactRelationshipImportRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportResult"];
                };
            };
        };
    };
    readonly relationship: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly updateRelationship: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateContactRelationshipRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly activate: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly archive: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly command: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly relationshipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["RelationshipCommandRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly deactivate: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly relationshipHistory: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["ListResponseRelationshipHistoryResponse"];
                };
            };
        };
    };
    readonly setPrimary: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly reactivate: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly relationshipId: string;
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
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly updateRelationship_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly relationshipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["VersionedRelationshipRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
        };
    };
    readonly contactAddresses: {
        readonly parameters: {
            readonly query?: {
                readonly includeArchived?: boolean;
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseAddressResponse"];
                };
            };
        };
    };
    readonly createContactAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateAddressRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseAddressResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
        };
    };
    readonly contactCommunicationMethods: {
        readonly parameters: {
            readonly query?: {
                readonly includeArchived?: boolean;
                readonly methodType?: string;
                readonly verificationStatus?: string;
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly createContactCommunicationMethod: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateCommunicationMethodRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCommunicationMethodResponse"];
                };
            };
        };
    };
    readonly ownershipHistory: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseOwnershipHistoryResponse"];
                };
            };
        };
    };
    readonly profile: {
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactProfileResponse"];
                };
            };
        };
    };
    readonly updateProfile: {
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
                readonly "application/json": components["schemas"]["UpdateContactProfileRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactProfileResponse"];
                };
            };
        };
    };
    readonly updateProfile_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["VersionedContactProfileRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactProfileResponse"];
                };
            };
        };
    };
    readonly relationshipsByContact: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly cursor?: string;
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ListResponseContactRelationshipResponse"];
                };
            };
        };
    };
    readonly createRelationship: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly contactId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateContactRelationshipRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactRelationshipResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseContactResponse"];
                };
            };
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
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldValuesResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseCustomFieldResponse"];
                };
            };
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
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportJobResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseImportRunResponse"];
                };
            };
        };
    };
    readonly requestAi: {
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
                readonly "application/json": components["schemas"]["AiRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["StoredRequest"];
                };
            };
        };
    };
    readonly status: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly requestId: string;
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
                    readonly "application/json": components["schemas"]["StoredRequest"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadConversionResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseLeadResponse"];
                };
            };
        };
    };
    readonly myWork: {
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
                    readonly "application/json": components["schemas"]["OwnershipResponseMyWorkResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseOpportunityResponse"];
                };
            };
        };
    };
    readonly history: {
        readonly parameters: {
            readonly query?: {
                readonly cursor?: string;
                readonly pageSize?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly recordType: "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "ACTIVITY" | "TASK";
                readonly recordId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseOwnershipHistory"];
                };
            };
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
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponsePipelineResponse"];
                };
            };
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
    readonly listQueues: {
        readonly parameters: {
            readonly query?: {
                readonly status?: "ACTIVE" | "DRAINING" | "ARCHIVED";
                readonly pageSize?: number;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseQueue"];
                };
            };
        };
    };
    readonly createQueue: {
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
                readonly "application/json": components["schemas"]["CreateQueueRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseQueue"];
                };
            };
        };
    };
    readonly getQueue: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly queueId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseQueueDetail"];
                };
            };
        };
    };
    readonly updateQueue: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly queueId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateQueueRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseQueue"];
                };
            };
        };
    };
    readonly listQueueItems: {
        readonly parameters: {
            readonly query?: {
                readonly cursor?: string;
                readonly pageSize?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly queueId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseQueueItemSummary"];
                };
            };
        };
    };
    readonly claimQueueItem: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly queueId: string;
                readonly itemId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ClaimQueueItemRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseClaimResult"];
                };
            };
        };
    };
    readonly releaseQueueItem: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly queueId: string;
                readonly itemId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ReleaseQueueItemRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseAssignment"];
                };
            };
        };
    };
    readonly listQueueMemberships: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly queueId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseQueueMembership"];
                };
            };
        };
    };
    readonly roles: {
        readonly parameters: {
            readonly query?: {
                readonly includeInactive?: boolean;
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
                    readonly "application/json": components["schemas"]["ListResponseRelationshipRoleResponse"];
                };
            };
        };
    };
    readonly createRole: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateRelationshipRoleRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SingleResponseRelationshipRoleResponse"];
                };
            };
        };
    };
    readonly listTeams: {
        readonly parameters: {
            readonly query?: {
                readonly status?: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
                readonly pageSize?: number;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseSalesTeam"];
                };
            };
        };
    };
    readonly createTeam: {
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
                readonly "application/json": components["schemas"]["CreateTeamRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseSalesTeam"];
                };
            };
        };
    };
    readonly getTeam: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly teamId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseTeamDetail"];
                };
            };
        };
    };
    readonly updateTeam: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly teamId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateTeamRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseSalesTeam"];
                };
            };
        };
    };
    readonly listTeamMemberships: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly teamId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseTeamMembership"];
                };
            };
        };
    };
    readonly addTeamMembership: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly teamId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AddTeamMembershipRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTeamMembership"];
                };
            };
        };
    };
    readonly endTeamMembership: {
        readonly parameters: {
            readonly query: {
                readonly reason: string;
            };
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly teamId: string;
                readonly membershipId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseTeamMembership"];
                };
            };
        };
    };
    readonly updateTeamMembership: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly teamId: string;
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateTeamMembershipRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTeamMembership"];
                };
            };
        };
    };
    readonly listTerritories: {
        readonly parameters: {
            readonly query?: {
                readonly status?: "ACTIVE" | "ARCHIVED";
                readonly pageSize?: number;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseTerritory"];
                };
            };
        };
    };
    readonly createTerritory: {
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
                readonly "application/json": components["schemas"]["CreateTerritoryRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTerritory"];
                };
            };
        };
    };
    readonly getTerritory: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly territoryId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseTerritoryDetail"];
                };
            };
        };
    };
    readonly updateTerritory: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly territoryId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateTerritoryRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTerritory"];
                };
            };
        };
    };
    readonly assignTerritory: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly territoryId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AssignTerritoryRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTerritoryAssignment"];
                };
            };
        };
    };
    readonly removeTerritoryAssignment: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "If-Match": string;
            };
            readonly path: {
                readonly territoryId: string;
                readonly assignmentId: string;
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
                    readonly "application/json": components["schemas"]["OwnershipResponseTerritoryAssignment"];
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
    readonly listTransfers: {
        readonly parameters: {
            readonly query?: {
                readonly direction?: "INCOMING" | "OUTGOING" | "ALL";
                readonly state?: "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED" | "FAILED";
                readonly pageSize?: number;
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
                    readonly "application/json": components["schemas"]["OwnershipListResponseTransferRequest"];
                };
            };
        };
    };
    readonly createTransfer: {
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
                readonly "application/json": components["schemas"]["CreateTransferRequest"];
            };
        };
        readonly responses: {
            /** @description Created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTransferRequest"];
                };
            };
        };
    };
    readonly decideTransfer: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
                readonly "If-Match": string;
            };
            readonly path: {
                readonly transferId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["DecideTransferRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTransferRequest"];
                };
            };
        };
    };
    readonly cancelTransfer: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
                readonly "If-Match": string;
            };
            readonly path: {
                readonly transferId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CancelTransferRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTransferRequest"];
                };
            };
        };
    };
    readonly submitTransfer: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
                readonly "If-Match": string;
            };
            readonly path: {
                readonly transferId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SubmitTransferRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OwnershipResponseTransferRequest"];
                };
            };
        };
    };
    readonly confirmCrmAiRecommendation: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
                readonly "If-Match"?: string;
            };
            readonly path: {
                readonly requestId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": {
                    readonly expectedEntityVersion: number;
                };
            };
        };
        readonly responses: {
            /** @description Recommendation confirmed */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Unauthenticated */
            readonly 401: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Unauthorized — missing CRM.AI.CONFIRM capability */
            readonly 403: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Integration request not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Request is already in terminal state */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Recommendation has expired */
            readonly 410: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    readonly rejectCrmAiRecommendation: {
        readonly parameters: {
            readonly query?: never;
            readonly header: {
                readonly "Idempotency-Key": string;
            };
            readonly path: {
                readonly requestId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": {
                    readonly reason?: string;
                };
            };
        };
        readonly responses: {
            /** @description Recommendation rejected */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Unauthenticated */
            readonly 401: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Unauthorized — missing CRM.AI.CONFIRM capability */
            readonly 403: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Integration request not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Request is already in terminal state */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
