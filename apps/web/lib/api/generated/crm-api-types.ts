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
    readonly "/api/v2/crm/custom-fields/values/{entityType}/{entityId}": {
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
    readonly "/api/v1/users/{userId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /**
         * Get a user
         * @description Returns a user only when it belongs to the requested tenant.
         */
        readonly get: operations["getUser"];
        /**
         * Update a user
         * @description Updates email and display name within the tenant scope.
         */
        readonly put: operations["updateUser"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /**
         * Get an Organization by ID
         * @description Fetch a single Organization scoped to a specific Tenant. The tenantId query parameter is required. If the (tenantId, id) pair does not match any Organization, a 404 is returned.
         */
        readonly get: operations["getOrganization"];
        /**
         * Update an Organization's name and description
         * @description Updates the mutable fields of an Organization. The tenant relationship and status are NOT changed. If the new name conflicts with another organization under the same tenant, a 409 Conflict is returned.
         */
        readonly put: operations["updateOrganization"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/custom-fields/values/{entityType}/{entityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["readCustomFieldValues_1"];
        readonly put: operations["upsertCustomFieldValues_1"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/organizations/{organizationId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["updateOrganization_1"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/plans/{planId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["plan"];
        readonly put: operations["updatePlan"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["get"];
        readonly put: operations["update"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/capabilities/{capabilityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["get_1"];
        readonly put: operations["update_1"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v2/crm/relationship-roles": {
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
    readonly "/api/v2/crm/opportunities": {
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
    readonly "/api/v2/crm/leads": {
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
    readonly "/api/v2/crm/leads/{leadId}/convert": {
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
    readonly "/api/v2/crm/imports/{jobId}/run": {
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
    readonly "/api/v2/crm/imports/{jobId}/cancel": {
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
    readonly "/api/v2/crm/imports/upload": {
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
    readonly "/api/v2/crm/custom-fields": {
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
    readonly "/api/v2/crm/contacts": {
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
    readonly "/api/v2/crm/contacts/{contactId}/relationships": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/commands": {
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
    readonly "/api/v2/crm/contact-relationship-imports": {
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
    readonly "/api/v2/crm/activities": {
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
    readonly "/api/v2/crm/accounts": {
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
    readonly "/api/v1/users": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /**
         * List users
         * @description Lists users belonging only to the requested tenant.
         */
        readonly get: operations["listUsers"];
        readonly put?: never;
        /**
         * Create a user
         * @description Creates a tenant-scoped user. Default status is INVITED.
         */
        readonly post: operations["createUser"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/organizations": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /**
         * List Organizations for a Tenant
         * @description Returns all Organizations belonging to the specified Tenant. The tenantId query parameter is required. If the tenant has no organizations, an empty array is returned.
         */
        readonly get: operations["listOrganizations"];
        readonly put?: never;
        /**
         * Create a new Organization
         * @description Registers a new Organization under an existing Tenant. The Organization is created with status ACTIVE. The (tenantId, name) pair must be unique; otherwise a 409 is returned.
         */
        readonly post: operations["createOrganization"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /** List all memberships for an organization */
        readonly get: operations["listMemberships"];
        readonly put?: never;
        /**
         * Invite a member to an organization
         * @description Creates a new membership with status INVITED. The (tenantId, organizationId, email) tuple must be unique.
         */
        readonly post: operations["inviteMember"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/internal/control-plane/bootstrap-admin": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["bootstrapAdmin"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tasks": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTasks"];
        readonly put?: never;
        readonly post: operations["createTask"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tags": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTags"];
        readonly put?: never;
        readonly post: operations["createTag"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tags/{tagId}/assignments": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listAssignmentsByTag"];
        readonly put?: never;
        readonly post: operations["assignTag"];
        readonly delete: operations["unassignTag"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/pipelines": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listPipelines"];
        readonly put?: never;
        readonly post: operations["createPipeline"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/opportunities": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listOpportunities_1"];
        readonly put?: never;
        readonly post: operations["createOpportunity_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/notes": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listNotes"];
        readonly put?: never;
        readonly post: operations["createNote"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/leads": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listLeads_1"];
        readonly put?: never;
        readonly post: operations["createLead_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/leads/{leadId}/convert": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["convertLead_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/{jobId}/run": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["runImport_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/{jobId}/cancel": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["cancelImport_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/upload": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["uploadImport_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/custom-fields": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listCustomFields_1"];
        readonly put?: never;
        readonly post: operations["createCustomField_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/contacts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listContacts_1"];
        readonly put?: never;
        readonly post: operations["createContact_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/activities": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listActivities_1"];
        readonly put?: never;
        readonly post: operations["createActivity_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listAccounts_1"];
        readonly put?: never;
        readonly post: operations["createAccount_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{sourceAccountId}/merge/{targetAccountId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["merge"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/relationships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["relationships"];
        readonly put?: never;
        readonly post: operations["addRelationship"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/identifiers": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["identifiers"];
        readonly put?: never;
        readonly post: operations["addIdentifier"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/addresses": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["addresses"];
        readonly put?: never;
        readonly post: operations["addAddress"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listTenants"];
        readonly put?: never;
        readonly post: operations["createTenant"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/organizations": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["organizations"];
        readonly put?: never;
        readonly post: operations["createOrganization_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/organizations/{organizationId}/memberships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["memberships"];
        readonly put?: never;
        readonly post: operations["createMembership"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["subscriptions"];
        readonly put?: never;
        readonly post: operations["createSubscription"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/renew": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["renewSubscription"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/plans": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["plans"];
        readonly put?: never;
        readonly post: operations["createPlan"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/health/actions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["execute"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/billing/invoices/{invoiceId}/mark-paid": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["markInvoicePaid"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/reset-password": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Set a new password using a valid single-use reset token */
        readonly post: operations["resetPassword"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/register": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Create an account with regional mobile contact and send a password setup link */
        readonly post: operations["register"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/refresh": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Rotate a BFF-held refresh token */
        readonly post: operations["refresh"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/logout": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Revoke all active refresh tokens for the authenticated user */
        readonly post: operations["logout"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/login": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Authenticate and return access data to the trusted BFF */
        readonly post: operations["login"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/forgot-password": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Request a single-use reset link. Always returns 200 to prevent account enumeration. */
        readonly post: operations["forgotPassword"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/change-credential": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Rotate the authenticated account credential and terminate refresh sessions */
        readonly post: operations["changeCredential"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/admin-reset-password/{userId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        /** Send an administrator-issued, single-use set-password link */
        readonly post: operations["adminResetPassword"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/users/{userId}/role-links/{roleId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["grant"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list"];
        readonly put?: never;
        readonly post: operations["create"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}/access-items/{capabilityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["attach"];
        readonly delete: operations["detach"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/capabilities": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_1"];
        readonly put?: never;
        readonly post: operations["create_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v2/crm/pipelines/{pipelineId}": {
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
    readonly "/api/v2/crm/opportunities/{opportunityId}": {
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
    readonly "/api/v2/crm/opportunities/{opportunityId}/stage": {
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
    readonly "/api/v2/crm/leads/{leadId}/status": {
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
    readonly "/api/v2/crm/custom-fields/{customFieldId}": {
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
    readonly "/api/v2/crm/contacts/{contactId}": {
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
    readonly "/api/v2/crm/contacts/{contactId}/restore": {
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
    readonly "/api/v2/crm/contacts/{contactId}/profile": {
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
    readonly "/api/v2/crm/contacts/{contactId}/profile-versioned": {
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
    readonly "/api/v2/crm/contacts/{contactId}/archive": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/versioned": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/reactivate": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/primary": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/deactivate": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/archive": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/activate": {
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
    readonly "/api/v2/crm/activities/{activityId}": {
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
    readonly "/api/v2/crm/activities/{activityId}/complete": {
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
    readonly "/api/v2/crm/accounts/{accountId}": {
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
    readonly "/api/v2/crm/accounts/{accountId}/restore": {
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
    readonly "/api/v2/crm/accounts/{accountId}/archive": {
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
    readonly "/api/v1/users/{userId}/suspend": {
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
        /**
         * Suspend a user
         * @description Sets the user status to SUSPENDED.
         */
        readonly patch: operations["suspendUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/users/{userId}/deactivate": {
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
        /**
         * Deactivate a user
         * @description Sets the user status to INACTIVE.
         */
        readonly patch: operations["deactivateUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/users/{userId}/archive": {
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
        /**
         * Archive a user
         * @description Sets the user status to ARCHIVED.
         */
        readonly patch: operations["archiveUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/users/{userId}/activate": {
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
        /**
         * Activate a user
         * @description Sets the user status to ACTIVE.
         */
        readonly patch: operations["activateUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}/unassign": {
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
        readonly patch: operations["unassignUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}/remove": {
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
        /** Remove a membership (soft delete, status = REMOVED) */
        readonly patch: operations["removeMembership"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}/deactivate": {
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
        /** Deactivate a membership (status = INACTIVE) */
        readonly patch: operations["deactivateMembership"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}": {
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
        readonly patch: operations["assignUser"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}/activate": {
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
        /** Activate a membership (status = ACTIVE) */
        readonly patch: operations["activateMembership"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{id}/deactivate": {
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
        /**
         * Deactivate an Organization
         * @description Sets the Organization status to INACTIVE. Idempotent: deactivating an already-INACTIVE organization is a no-op that returns the current state.
         */
        readonly patch: operations["deactivateOrganization"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{id}/archive": {
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
        /**
         * Archive an Organization (soft delete)
         * @description Marks the Organization as ARCHIVED without removing it from the database. Idempotent: archiving an already-archived organization is a no-op that returns the current state. Use this instead of a DELETE endpoint to preserve audit history.
         */
        readonly patch: operations["archiveOrganization"];
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{id}/activate": {
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
        /**
         * Activate an Organization
         * @description Sets the Organization status to ACTIVE. Idempotent: activating an already-ACTIVE organization is a no-op that returns the current state.
         */
        readonly patch: operations["activateOrganization"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tasks/{taskId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getTask"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateTask"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tasks/{taskId}/start": {
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
        readonly patch: operations["startTask"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tasks/{taskId}/complete": {
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
        readonly patch: operations["completeTask"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tasks/{taskId}/cancel": {
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
        readonly patch: operations["cancelTask"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tags/{tagId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getTag"];
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["deleteTag"];
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateTag"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/opportunities/{opportunityId}/stage": {
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
        readonly patch: operations["moveOpportunity"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/notes/{noteId}/archive": {
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
        readonly patch: operations["archiveNote"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/leads/{leadId}/status": {
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
        readonly patch: operations["changeLeadStatus_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/contacts/{contactId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getContact_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateContact_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/contacts/{contactId}/restore": {
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
        readonly patch: operations["restoreContact_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/contacts/{contactId}/archive": {
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
        readonly patch: operations["archiveContact_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/activities/{activityId}/complete": {
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
        readonly patch: operations["completeActivity_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getAccount_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateAccount_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/restore": {
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
        readonly patch: operations["restoreAccount_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/master": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getMaster"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch: operations["updateMaster"];
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/archive": {
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
        readonly patch: operations["archiveAccount_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/status": {
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
        readonly patch: operations["changeTenantStatus"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/organizations/{organizationId}/status": {
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
        readonly patch: operations["changeOrganizationStatus"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}/organizations/{organizationId}/memberships/{membershipId}": {
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
        readonly patch: operations["updateMembership"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/systems/{serviceId}/status": {
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
        readonly patch: operations["updateSystemStatus"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/seats": {
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
        readonly patch: operations["changeSeats"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/resume": {
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
        readonly patch: operations["resumeSubscription"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/change-plan": {
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
        readonly patch: operations["changeSubscriptionPlan"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/cancel": {
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
        readonly patch: operations["cancelSubscription"];
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/plans/{planId}/status": {
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
        readonly patch: operations["changePlanStatus"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/users/role-links/{grantId}/revoke": {
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
        readonly patch: operations["revoke"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}/deactivate": {
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
        readonly patch: operations["deactivate_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}/archive": {
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
        readonly patch: operations["archive_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}/activate": {
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
        readonly patch: operations["activate_1"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/capabilities/{capabilityId}/deactivate": {
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
        readonly patch: operations["deactivate_2"];
        readonly trace?: never;
    };
    readonly "/api/v1/access/capabilities/{capabilityId}/activate": {
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
        readonly patch: operations["activate_2"];
        readonly trace?: never;
    };
    readonly "/api/v2/crm/timeline/{subjectType}/{subjectId}": {
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
    readonly "/api/v2/crm/pipelines": {
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
    readonly "/api/v2/crm/pipelines/{pipelineId}/stages": {
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
    readonly "/api/v2/crm/leads/{leadId}": {
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
    readonly "/api/v2/crm/imports": {
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
    readonly "/api/v2/crm/imports/{jobId}": {
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
    readonly "/api/v2/crm/imports/{jobId}/errors": {
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
    readonly "/api/v2/crm/imports/{jobId}/errors.csv": {
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
    readonly "/api/v2/crm/custom-fields/search": {
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
    readonly "/api/v2/crm/contacts/{contactId}/ownership-history": {
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
    readonly "/api/v2/crm/contact-relationships/{relationshipId}/history": {
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
    readonly "/api/v2/crm/accounts/{accountId}/customer-360": {
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
    readonly "/api/v2/crm/accounts/{accountId}/contact-relationships": {
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
    readonly "/api/v1/users/{userId}/memberships": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listMemberships_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/organizations/{organizationId}/memberships/{membershipId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /** Get a membership by ID */
        readonly get: operations["getMembership"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/timeline/{subjectType}/{subjectId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["timeline_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/tags/assignments/by-subject": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listAssignmentsBySubject"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/search": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["search"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/reports/sales-pipeline": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["salesPipeline"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/reports/lead-conversion": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["leadConversion"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/reports/dashboard": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["dashboard"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/reports/activity-summary": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["activitySummary"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/reports/account-growth": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["accountGrowth"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/pipelines/{pipelineId}/stages": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listPipelineStages_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/opportunities/{opportunityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getOpportunity_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/notes/{noteId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getNote"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/leads/{leadId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getLead_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listImportJobs_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/{jobId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getImportJob_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/{jobId}/errors": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listImportErrors_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/imports/{jobId}/errors.csv": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["downloadImportErrors_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/export/leads": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["exportLeads"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/export/contacts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["exportContacts"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/export/accounts": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["exportAccounts"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/dashboard": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["dashboard_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/custom-fields/values/{entityType}/{entityId}/sensitive": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["readSensitiveCustomFieldValues"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/custom-fields/search": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["searchCustomFieldValues_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/activities/{activityId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getActivity_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/duplicates": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["duplicates"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/customer-360": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["customer360_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/tenants/{tenantId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["getTenant"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/systems": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["systems"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["subscription"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/events": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["subscriptionEvents"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/subscriptions/{subscriptionId}/entitlements": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["subscriptionEntitlements"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/health": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["health"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/dashboard": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["dashboard_2"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/billing/invoices": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["invoices"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/audit": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["audit"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/control-plane/access-check": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["accessCheck"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/auth/me": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        /** Get the authenticated user's identity, memberships, and role grants */
        readonly get: operations["me"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/users/{userId}/role-links": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_2"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/roles/{roleId}/access-items": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_3"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/access/evaluation": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["evaluate"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/crm/accounts/{accountId}/addresses/{addressId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["deactivateAddress"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        readonly UpdateCustomFieldValuesRequest: {
            readonly values: {
                readonly [key: string]: unknown;
            };
        };
        readonly CustomFieldValuesResponse: {
            readonly entityType?: string;
            /** Format: uuid */
            readonly entityId?: string;
            readonly values?: {
                readonly [key: string]: unknown;
            };
        };
        readonly Meta: {
            /** Format: uuid */
            readonly requestId?: string;
            /** Format: date-time */
            readonly timestamp?: string;
        };
        readonly SingleResponseCustomFieldValuesResponse: {
            readonly data?: components["schemas"]["CustomFieldValuesResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly UpdateUserRequest: {
            /** Format: email */
            readonly email: string;
            readonly displayName?: string;
        };
        readonly UserResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly email?: string;
            readonly displayName?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "INVITED" | "SUSPENDED" | "ARCHIVED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
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
        readonly UpdateOrganizationRequest: {
            readonly name: string;
            readonly description?: string;
        };
        readonly OrganizationResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly name?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "ARCHIVED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly UpdateOrganizationAdminRequest: {
            readonly name: string;
            readonly description?: string;
        };
        readonly OrganizationAdminResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly name?: string;
            readonly description?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly EntitlementRequest: {
            readonly featureCode: string;
            readonly enabled?: boolean;
            /** Format: int64 */
            readonly limitValue?: number;
        };
        readonly UpdatePlanRequest: {
            readonly name: string;
            readonly description?: string;
            readonly currencyCode: string;
            /** Format: int64 */
            readonly monthlyPriceMinor?: number;
            /** Format: int64 */
            readonly annualPriceMinor?: number;
            /** Format: int32 */
            readonly trialDays?: number;
            /** Format: int32 */
            readonly maxUsers?: number;
            /** Format: int32 */
            readonly maxOrganizations?: number;
            /** Format: int64 */
            readonly storageMb?: number;
            readonly entitlements?: readonly components["schemas"]["EntitlementRequest"][];
        };
        readonly EntitlementResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly featureCode?: string;
            readonly enabled?: boolean;
            /** Format: int64 */
            readonly limitValue?: number;
        };
        readonly PlanResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly code?: string;
            readonly name?: string;
            readonly description?: string;
            readonly status?: string;
            readonly currencyCode?: string;
            /** Format: int64 */
            readonly monthlyPriceMinor?: number;
            /** Format: int64 */
            readonly annualPriceMinor?: number;
            /** Format: int32 */
            readonly trialDays?: number;
            /** Format: int32 */
            readonly maxUsers?: number;
            /** Format: int32 */
            readonly maxOrganizations?: number;
            /** Format: int64 */
            readonly storageMb?: number;
            readonly entitlements?: readonly components["schemas"]["EntitlementResponse"][];
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly UpdateRoleRequest: {
            readonly name: string;
            readonly description?: string;
        };
        readonly RoleResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly code?: string;
            readonly name?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "ARCHIVED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CapabilityResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly code?: string;
            readonly name?: string;
            readonly description?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateRelationshipRoleRequest: {
            readonly code?: string;
            readonly nameAr?: string;
            readonly nameEn?: string;
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
        readonly SingleResponseRelationshipRoleResponse: {
            readonly data?: components["schemas"]["RelationshipRoleResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseOpportunityResponse: {
            readonly data?: components["schemas"]["OpportunityResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseLeadResponse: {
            readonly data?: components["schemas"]["LeadResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly LeadConversionResponse: {
            readonly lead?: components["schemas"]["LeadResponse"];
            readonly account?: components["schemas"]["AccountResponse"];
            readonly contact?: components["schemas"]["ContactResponse"];
            readonly opportunity?: components["schemas"]["OpportunityResponse"];
            readonly idempotent?: boolean;
        };
        readonly SingleResponseLeadConversionResponse: {
            readonly data?: components["schemas"]["LeadConversionResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseImportRunResponse: {
            readonly data?: components["schemas"]["ImportRunResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseImportJobResponse: {
            readonly data?: components["schemas"]["ImportJobResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseCustomFieldResponse: {
            readonly data?: components["schemas"]["CustomFieldResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseContactResponse: {
            readonly data?: components["schemas"]["ContactResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseContactRelationshipResponse: {
            readonly data?: components["schemas"]["ContactRelationshipResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly RelationshipCommandRequest: {
            /** Format: int64 */
            readonly expectedVersion?: number;
            readonly action?: string;
        };
        readonly ContactRelationshipImportRequest: {
            /** Format: uuid */
            readonly importId?: string;
            readonly rows?: readonly components["schemas"]["ImportRow"][];
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
        readonly SingleResponseImportResult: {
            readonly data?: components["schemas"]["ImportResult"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseActivityResponse: {
            readonly data?: components["schemas"]["ActivityResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseAccountResponse: {
            readonly data?: components["schemas"]["AccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly CreateUserRequest: {
            /** Format: email */
            readonly email: string;
            readonly displayName?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "INVITED" | "SUSPENDED" | "ARCHIVED";
        };
        readonly CreateOrganizationRequest: {
            /** Format: uuid */
            readonly tenantId: string;
            readonly name: string;
            readonly description?: string;
        };
        readonly InviteOrganizationMemberRequest: {
            /** Format: uuid */
            readonly tenantId: string;
            /** Format: uuid */
            readonly organizationId: string;
            /** Format: email */
            readonly email: string;
            readonly displayName?: string;
        };
        readonly OrganizationMembershipResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            /** Format: uuid */
            readonly userId?: string;
            readonly email?: string;
            readonly displayName?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "INACTIVE" | "INVITED" | "REMOVED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateTaskRequest: {
            readonly title: string;
            readonly description?: string;
            readonly relatedType?: string;
            /** Format: uuid */
            readonly relatedId?: string;
            /** Format: uuid */
            readonly assigneeUserId?: string;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly startAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
        };
        readonly CreateTagRequest: {
            readonly name: string;
            readonly color?: string;
        };
        readonly AssignTagRequest: {
            readonly subjectType: string;
            /** Format: uuid */
            readonly subjectId: string;
        };
        readonly CreatePipelineRequest: {
            readonly name: string;
            readonly currencyCode?: string;
            readonly stages?: readonly string[];
        };
        readonly CreateNoteRequest: {
            readonly subjectType: string;
            /** Format: uuid */
            readonly subjectId: string;
            readonly body: string;
            /** Format: uuid */
            readonly authorUserId?: string;
        };
        readonly MergeRequest: {
            /** Format: int64 */
            readonly expectedSourceVersion?: number;
            /** Format: int64 */
            readonly expectedTargetVersion?: number;
            readonly reason?: string;
        };
        readonly MergeResult: {
            /** Format: uuid */
            readonly sourceAccountId?: string;
            /** Format: uuid */
            readonly targetAccountId?: string;
            /** Format: int64 */
            readonly sourceVersion?: number;
            /** Format: int64 */
            readonly targetVersion?: number;
            /** Format: int32 */
            readonly contactsMoved?: number;
            /** Format: int32 */
            readonly opportunitiesMoved?: number;
            /** Format: int32 */
            readonly activitiesMoved?: number;
            /** Format: int32 */
            readonly addressesMoved?: number;
            /** Format: int32 */
            readonly identifiersMoved?: number;
            /** Format: int32 */
            readonly relationshipsMoved?: number;
            /** Format: date-time */
            readonly mergedAt?: string;
        };
        readonly CreateRelationshipRequest: {
            /** Format: uuid */
            readonly targetAccountId: string;
            readonly relationshipType: string;
            /** Format: date */
            readonly effectiveFrom?: string;
            /** Format: date */
            readonly effectiveTo?: string;
            readonly notes?: string;
        };
        readonly AccountRelationship: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly sourceAccountId?: string;
            /** Format: uuid */
            readonly targetAccountId?: string;
            readonly relationshipType?: string;
            readonly status?: string;
            /** Format: date */
            readonly effectiveFrom?: string;
            /** Format: date */
            readonly effectiveTo?: string;
            readonly notes?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateIdentifierRequest: {
            readonly identifierType: string;
            readonly identifierValue: string;
            readonly issuerCountryCode?: string;
            readonly primaryIdentifier?: boolean;
            readonly verified?: boolean;
        };
        readonly AccountIdentifier: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly accountId?: string;
            readonly identifierType?: string;
            readonly identifierValue?: string;
            readonly issuerCountryCode?: string;
            readonly primaryIdentifier?: boolean;
            readonly verified?: boolean;
            readonly active?: boolean;
            /** Format: date-time */
            readonly createdAt?: string;
        };
        readonly CreateAddressRequest: {
            readonly addressType: string;
            readonly label?: string;
            readonly line1: string;
            readonly line2?: string;
            readonly city: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode: string;
            readonly primaryAddress?: boolean;
        };
        readonly AccountAddress: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            /** Format: uuid */
            readonly accountId?: string;
            readonly addressType?: string;
            readonly label?: string;
            readonly line1?: string;
            readonly line2?: string;
            readonly city?: string;
            readonly stateRegion?: string;
            readonly postalCode?: string;
            readonly countryCode?: string;
            readonly primaryAddress?: boolean;
            readonly active?: boolean;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateTenantRequest: {
            readonly name: string;
            readonly legalName?: string;
            readonly subdomain: string;
            /** Format: email */
            readonly billingEmail?: string;
            /** Format: email */
            readonly adminEmail: string;
            readonly adminDisplayName: string;
            readonly countryCode?: string;
            readonly locale?: string;
            readonly timezone?: string;
            readonly currencyCode?: string;
            /** Format: int32 */
            readonly trialDays?: number;
            readonly planCode?: string;
            /** Format: uuid */
            readonly planId?: string;
            readonly billingCycle?: string;
            /** Format: int32 */
            readonly seatQuantity?: number;
            readonly createDefaultOrganization?: boolean;
        };
        readonly TenantResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly name?: string;
            readonly legalName?: string;
            readonly subdomain?: string;
            readonly status?: string;
            readonly billingEmail?: string;
            readonly countryCode?: string;
            readonly locale?: string;
            readonly timezone?: string;
            readonly currencyCode?: string;
            /** Format: date-time */
            readonly trialEndsAt?: string;
            readonly suspensionReason?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateOrganizationAdminRequest: {
            readonly name: string;
            readonly description?: string;
        };
        readonly CreateMembershipAdminRequest: {
            /** Format: email */
            readonly email: string;
            readonly displayName?: string;
            readonly roleCode: string;
        };
        readonly MembershipAdminResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            /** Format: uuid */
            readonly userId?: string;
            readonly email?: string;
            readonly displayName?: string;
            readonly roleCode?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateSubscriptionRequest: {
            /** Format: uuid */
            readonly tenantId: string;
            /** Format: uuid */
            readonly planId: string;
            readonly billingCycle: string;
            /** Format: int32 */
            readonly seatQuantity?: number;
            /** Format: int32 */
            readonly trialDays?: number;
        };
        readonly SubscriptionResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly tenantName?: string;
            /** Format: uuid */
            readonly planId?: string;
            readonly planCode?: string;
            readonly planName?: string;
            /** Format: uuid */
            readonly pendingPlanId?: string;
            readonly pendingPlanCode?: string;
            readonly status?: string;
            readonly billingCycle?: string;
            readonly pendingBillingCycle?: string;
            /** Format: int32 */
            readonly seatQuantity?: number;
            /** Format: int64 */
            readonly creditBalanceMinor?: number;
            readonly currencyCode?: string;
            /** Format: date-time */
            readonly startedAt?: string;
            /** Format: date-time */
            readonly trialEndsAt?: string;
            /** Format: date-time */
            readonly currentPeriodStart?: string;
            /** Format: date-time */
            readonly currentPeriodEnd?: string;
            readonly cancelAtPeriodEnd?: boolean;
            /** Format: date-time */
            readonly cancelledAt?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreatePlanRequest: {
            readonly code: string;
            readonly name: string;
            readonly description?: string;
            readonly currencyCode: string;
            /** Format: int64 */
            readonly monthlyPriceMinor?: number;
            /** Format: int64 */
            readonly annualPriceMinor?: number;
            /** Format: int32 */
            readonly trialDays?: number;
            /** Format: int32 */
            readonly maxUsers?: number;
            /** Format: int32 */
            readonly maxOrganizations?: number;
            /** Format: int64 */
            readonly storageMb?: number;
            readonly entitlements?: readonly components["schemas"]["EntitlementRequest"][];
        };
        readonly HealthActionRequest: {
            readonly scope: string;
            /** Format: uuid */
            readonly targetId?: string;
            readonly action: string;
            readonly reason: string;
        };
        readonly CollectionError: {
            readonly component?: string;
            readonly code?: string;
            readonly message?: string;
            readonly correlationId?: string;
            /** Format: date-time */
            readonly timestamp?: string;
        };
        readonly DataPressureResponse: {
            /** Format: int32 */
            readonly pressureScore?: number;
            readonly status?: string;
            /** Format: int64 */
            readonly trackedRows?: number;
            /** Format: int64 */
            readonly auditEventsLastHour?: number;
            /** Format: int64 */
            readonly failedAuditEventsLastHour?: number;
            /** Format: int64 */
            readonly openInvoices?: number;
            /** Format: int64 */
            readonly activeUsers?: number;
            readonly message?: string;
        };
        readonly HealthActionDescriptor: {
            readonly code?: string;
            readonly scope?: string;
            readonly title?: string;
            readonly description?: string;
            readonly requiresTarget?: boolean;
        };
        readonly HealthActionResult: {
            readonly action?: string;
            readonly scope?: string;
            /** Format: uuid */
            readonly targetId?: string;
            readonly status?: string;
            readonly message?: string;
            /** Format: date-time */
            readonly executedAt?: string;
            readonly snapshot?: components["schemas"]["PlatformHealthResponse"];
        };
        readonly PlatformHealthResponse: {
            /** Format: date-time */
            readonly generatedAt?: string;
            readonly overallStatus?: string;
            /** Format: int32 */
            readonly healthScore?: number;
            readonly riskLevel?: string;
            readonly predictionSummary?: string;
            readonly partial?: boolean;
            /** Format: int32 */
            readonly dataCompletenessScore?: number;
            readonly degradedComponents?: readonly string[];
            readonly collectionErrors?: readonly components["schemas"]["CollectionError"][];
            readonly runtime?: components["schemas"]["RuntimeMetricsResponse"];
            readonly dataPressure?: components["schemas"]["DataPressureResponse"];
            readonly services?: readonly components["schemas"]["ServiceHealthResponse"][];
            readonly tenants?: readonly components["schemas"]["TenantHealthResponse"][];
            readonly forecast?: readonly components["schemas"]["RiskForecastPoint"][];
            readonly availableActions?: readonly components["schemas"]["HealthActionDescriptor"][];
        };
        readonly RiskForecastPoint: {
            /** Format: int32 */
            readonly horizonMinutes?: number;
            /** Format: int32 */
            readonly riskScore?: number;
            readonly riskLevel?: string;
            readonly label?: string;
        };
        readonly RuntimeMetricsResponse: {
            /** Format: double */
            readonly cpuLoadPercent?: number;
            /** Format: double */
            readonly memoryUsagePercent?: number;
            /** Format: int64 */
            readonly memoryUsedMb?: number;
            /** Format: int64 */
            readonly memoryMaxMb?: number;
            /** Format: int64 */
            readonly uptimeSeconds?: number;
            /** Format: int32 */
            readonly availableProcessors?: number;
        };
        readonly ServiceHealthResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly code?: string;
            readonly name?: string;
            readonly environment?: string;
            readonly status?: string;
            readonly criticality?: string;
            /** Format: int32 */
            readonly healthScore?: number;
            /** Format: int32 */
            readonly pressureScore?: number;
            readonly riskLevel?: string;
            /** Format: int64 */
            readonly latencyMs?: number;
            readonly lastMessage?: string;
            /** Format: date-time */
            readonly lastCheckedAt?: string;
            readonly predictedStatus?: string;
        };
        readonly TenantHealthResponse: {
            /** Format: uuid */
            readonly tenantId?: string;
            readonly tenantName?: string;
            readonly tenantStatus?: string;
            /** Format: int32 */
            readonly healthScore?: number;
            /** Format: int32 */
            readonly pressureScore?: number;
            readonly riskLevel?: string;
            /** Format: int64 */
            readonly users?: number;
            /** Format: int64 */
            readonly organizations?: number;
            /** Format: int64 */
            readonly memberships?: number;
            /** Format: int64 */
            readonly invoices?: number;
            /** Format: int64 */
            readonly openInvoices?: number;
            /** Format: int64 */
            readonly seatCapacity?: number;
            /** Format: int32 */
            readonly seatUtilizationPercent?: number;
            /** Format: int64 */
            readonly trackedRecords?: number;
            readonly prediction?: string;
        };
        readonly MarkInvoicePaidRequest: {
            readonly paymentReference: string;
            readonly reason: string;
        };
        readonly InvoiceResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly tenantName?: string;
            /** Format: uuid */
            readonly subscriptionId?: string;
            readonly invoiceNumber?: string;
            readonly status?: string;
            readonly currencyCode?: string;
            /** Format: int64 */
            readonly subtotalMinor?: number;
            /** Format: int64 */
            readonly creditAppliedMinor?: number;
            /** Format: int64 */
            readonly taxMinor?: number;
            /** Format: int64 */
            readonly totalMinor?: number;
            /** Format: int64 */
            readonly amountPaidMinor?: number;
            readonly description?: string;
            /** Format: date-time */
            readonly periodStart?: string;
            /** Format: date-time */
            readonly periodEnd?: string;
            /** Format: date-time */
            readonly dueAt?: string;
            /** Format: date-time */
            readonly paidAt?: string;
            readonly paymentReference?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ResetPasswordRequest: {
            readonly token: string;
            readonly newPassword: string;
        };
        readonly SelfRegistrationRequest: {
            readonly displayName: string;
            /** Format: email */
            readonly email: string;
            readonly organizationName: string;
            readonly regionCode: string;
            readonly countryCode: string;
            readonly mobileNumber: string;
            readonly acceptTerms?: boolean;
        };
        readonly SelfRegistrationResponse: {
            readonly message?: string;
            readonly subdomain?: string;
            readonly passwordSetupRequired?: boolean;
        };
        readonly RefreshRequest: {
            readonly refreshToken: string;
        };
        readonly AuthResponse: {
            readonly accessToken?: string;
            /** Format: date-time */
            readonly expiresAt?: string;
            readonly user?: components["schemas"]["AuthUser"];
        };
        readonly AuthUser: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly email?: string;
            readonly displayName?: string;
            readonly status?: string;
        };
        readonly LoginRequest: {
            /** Format: email */
            readonly email: string;
            readonly password: string;
            /** Format: uuid */
            readonly tenantId?: string;
        };
        readonly ForgotPasswordRequest: {
            /** Format: email */
            readonly email: string;
        };
        readonly ChangeCredentialRequest: {
            readonly currentCredential: string;
            readonly newCredential: string;
        };
        readonly AdminResetPasswordRequest: {
            readonly locale?: string;
            /** @deprecated */
            readonly newPassword?: string;
            /** @deprecated */
            readonly forceChange?: boolean;
        };
        readonly UserAccessResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly userId?: string;
            /** Format: uuid */
            readonly roleId?: string;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            /** @enum {string} */
            readonly status?: "ACTIVE" | "REVOKED";
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly CreateRoleRequest: {
            readonly code: string;
            readonly name: string;
            readonly description?: string;
        };
        readonly RoleAccessResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly roleId?: string;
            /** Format: uuid */
            readonly capabilityId?: string;
            readonly capabilityCode?: string;
            /** Format: date-time */
            readonly createdAt?: string;
        };
        readonly UpdatePipelineRequest: {
            readonly name?: string;
            readonly currencyCode?: string;
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
        readonly UpdateOpportunityRequest: {
            readonly name?: string;
            readonly amount?: number;
            /** Format: uuid */
            readonly ownerUserId?: string;
            /** Format: date */
            readonly expectedCloseDate?: string;
        };
        readonly MoveOpportunityRequest: {
            /** Format: uuid */
            readonly stageId: string;
            readonly status?: string;
            readonly reason?: string;
        };
        readonly UpdateLeadStatusRequest: {
            readonly status: string;
            readonly reason?: string;
        };
        readonly UpdateCustomFieldRequest: {
            readonly labelAr?: string;
            readonly labelEn?: string;
            readonly sensitive?: boolean;
            readonly searchable?: boolean;
            readonly required?: boolean;
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
        readonly SingleResponseContactProfileResponse: {
            readonly data?: components["schemas"]["ContactProfileResponse"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly CompleteActivityRequest: {
            readonly result?: string;
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
        readonly ArchiveAccountResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly lifecycleStatus?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly SingleResponseArchiveAccountResponse: {
            readonly data?: components["schemas"]["ArchiveAccountResponse"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly UpdateTaskRequest: {
            readonly title?: string;
            readonly description?: string;
            /** Format: uuid */
            readonly assigneeUserId?: string;
            /** Format: int32 */
            readonly priority?: number;
            /** Format: date-time */
            readonly startAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
        };
        readonly CompleteTaskRequest: {
            readonly result?: string;
        };
        readonly CancelTaskRequest: {
            readonly reason?: string;
        };
        readonly UpdateTagRequest: {
            readonly name?: string;
            readonly color?: string;
        };
        readonly UpdateMasterRequest: {
            /** Format: int64 */
            readonly expectedVersion?: number;
            readonly legalName?: string;
            readonly tradingName?: string;
            readonly registrationNumber?: string;
            readonly taxNumber?: string;
            readonly industryCode?: string;
            readonly customerSegment?: string;
            readonly customerTier?: string;
            readonly website?: string;
            /** Format: email */
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly countryCode?: string;
            readonly riskRating?: string;
            readonly creditLimit?: number;
            /** Format: int32 */
            readonly paymentTermsDays?: number;
        };
        readonly CustomerMasterProfile: {
            /** Format: uuid */
            readonly accountId?: string;
            /** Format: int64 */
            readonly version?: number;
            readonly displayName?: string;
            readonly accountType?: string;
            readonly lifecycleStatus?: string;
            readonly legalName?: string;
            readonly tradingName?: string;
            readonly registrationNumber?: string;
            readonly taxNumber?: string;
            readonly industryCode?: string;
            readonly customerSegment?: string;
            readonly customerTier?: string;
            readonly website?: string;
            readonly primaryEmail?: string;
            readonly primaryPhone?: string;
            readonly countryCode?: string;
            readonly riskRating?: string;
            readonly creditLimit?: number;
            /** Format: int32 */
            readonly paymentTermsDays?: number;
            /** Format: int32 */
            readonly dataQualityScore?: number;
            /** Format: uuid */
            readonly mergedIntoAccountId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ChangeTenantStatusRequest: {
            readonly status: string;
            readonly reason: string;
        };
        readonly ChangeStatusRequest: {
            readonly status: string;
            readonly reason: string;
        };
        readonly UpdateMembershipAdminRequest: {
            readonly status: string;
            readonly roleCode: string;
            readonly reason: string;
        };
        readonly UpdateSystemStatusRequest: {
            readonly status: string;
            readonly reason: string;
            /** Format: int64 */
            readonly latencyMs?: number;
            readonly message?: string;
        };
        readonly SystemServiceResponse: {
            /** Format: uuid */
            readonly id?: string;
            readonly code?: string;
            readonly name?: string;
            readonly description?: string;
            readonly version?: string;
            readonly environment?: string;
            readonly status?: string;
            readonly healthUrl?: string;
            readonly ownerName?: string;
            readonly criticality?: string;
            readonly dependencies?: string;
            /** Format: date-time */
            readonly lastCheckedAt?: string;
            /** Format: int64 */
            readonly lastLatencyMs?: number;
            readonly lastMessage?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
        };
        readonly ChangeSeatsRequest: {
            /** Format: int32 */
            readonly seatQuantity?: number;
            readonly reason: string;
        };
        readonly ChangeSubscriptionPlanRequest: {
            /** Format: uuid */
            readonly planId: string;
            readonly billingCycle: string;
            readonly effectiveMode: string;
            readonly reason: string;
        };
        readonly CancelSubscriptionRequest: {
            readonly immediate?: boolean;
            readonly reason: string;
        };
        readonly ListResponseTimelineEventResponse: {
            readonly data?: readonly components["schemas"]["TimelineEventResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly Page: {
            readonly nextCursor?: string;
            readonly hasMore?: boolean;
            /** Format: int32 */
            readonly limit?: number;
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
        readonly ListResponseRelationshipRoleResponse: {
            readonly data?: readonly components["schemas"]["RelationshipRoleResponse"][];
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
        readonly ListResponseOpportunityResponse: {
            readonly data?: readonly components["schemas"]["OpportunityResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseLeadResponse: {
            readonly data?: readonly components["schemas"]["LeadResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseImportJobResponse: {
            readonly data?: readonly components["schemas"]["ImportJobResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly ListResponseImportErrorResponse: {
            readonly data?: readonly components["schemas"]["ImportErrorResponse"][];
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
        readonly ListResponseContactResponse: {
            readonly data?: readonly components["schemas"]["ContactResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseContactRelationshipResponse: {
            readonly data?: readonly components["schemas"]["ContactRelationshipResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseOwnershipHistoryResponse: {
            readonly data?: readonly components["schemas"]["OwnershipHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly ListResponseRelationshipHistoryResponse: {
            readonly data?: readonly components["schemas"]["RelationshipHistoryResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly ListResponseActivityResponse: {
            readonly data?: readonly components["schemas"]["ActivityResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly ListResponseAccountResponse: {
            readonly data?: readonly components["schemas"]["AccountResponse"][];
            readonly page?: components["schemas"]["Page"];
            readonly meta?: components["schemas"]["Meta"];
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
        readonly SingleResponseCustomer360Response: {
            readonly data?: components["schemas"]["Customer360Response"];
            readonly meta?: components["schemas"]["Meta"];
        };
        readonly DuplicateCandidate: {
            /** Format: uuid */
            readonly accountId?: string;
            readonly displayName?: string;
            readonly legalName?: string;
            readonly registrationNumber?: string;
            readonly taxNumber?: string;
            readonly primaryEmail?: string;
            /** Format: int32 */
            readonly confidenceScore?: number;
            readonly matchedFields?: readonly string[];
        };
        readonly SubscriptionEventResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly subscriptionId?: string;
            readonly action?: string;
            /** Format: uuid */
            readonly oldPlanId?: string;
            /** Format: uuid */
            readonly newPlanId?: string;
            readonly effectiveMode?: string;
            /** Format: int64 */
            readonly adjustmentMinor?: number;
            readonly reason?: string;
            /** Format: date-time */
            readonly effectiveAt?: string;
            /** Format: date-time */
            readonly createdAt?: string;
        };
        readonly AuditEntryResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly actorTenantId?: string;
            /** Format: uuid */
            readonly actorUserId?: string;
            /** Format: uuid */
            readonly targetTenantId?: string;
            readonly action?: string;
            readonly resourceType?: string;
            readonly resourceId?: string;
            readonly reason?: string;
            readonly result?: string;
            readonly correlationId?: string;
            /** Format: date-time */
            readonly createdAt?: string;
        };
        readonly DashboardResponse: {
            /** Format: int64 */
            readonly totalTenants?: number;
            /** Format: int64 */
            readonly activeTenants?: number;
            /** Format: int64 */
            readonly trialTenants?: number;
            /** Format: int64 */
            readonly suspendedTenants?: number;
            /** Format: int64 */
            readonly totalUsers?: number;
            /** Format: int64 */
            readonly activeUsers?: number;
            /** Format: int64 */
            readonly operationalServices?: number;
            /** Format: int64 */
            readonly degradedServices?: number;
            readonly recentActivity?: readonly components["schemas"]["AuditEntryResponse"][];
        };
        readonly MeResponse: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly tenantId?: string;
            readonly email?: string;
            readonly displayName?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly lastLoginAt?: string;
            readonly credentialRotationRequired?: boolean;
            readonly memberships?: readonly components["schemas"]["MembershipSummary"][];
            readonly roleGrants?: readonly components["schemas"]["RoleGrantSummary"][];
        };
        readonly MembershipSummary: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            readonly status?: string;
        };
        readonly RoleGrantSummary: {
            /** Format: uuid */
            readonly id?: string;
            /** Format: uuid */
            readonly roleId?: string;
            readonly roleCode?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            readonly status?: string;
        };
        readonly AccessDecisionResponse: {
            /** Format: uuid */
            readonly tenantId?: string;
            /** Format: uuid */
            readonly userId?: string;
            /** Format: uuid */
            readonly organizationId?: string;
            readonly capabilityCode?: string;
            readonly allowed?: boolean;
            readonly reason?: string;
            /** Format: uuid */
            readonly matchedRoleId?: string;
            readonly matchedRoleCode?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
    readonly getUser: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description User UUID */
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User found */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description Missing or invalid identifier */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly updateUser: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description User UUID */
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateUserRequest"];
            };
        };
        readonly responses: {
            /** @description User updated */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description Validation failed */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Email already exists in tenant */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly getOrganization: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly id: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Organization found */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - missing required tenantId query parameter */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Not Found - no Organization with the given id exists under the given tenant */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly updateOrganization: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly id: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateOrganizationRequest"];
            };
        };
        readonly responses: {
            /** @description Organization updated successfully */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - request body failed validation or tenantId missing */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Not Found - no Organization with the given id exists under this tenant */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Already Exists - another organization under the same tenant already uses the new name */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly readCustomFieldValues_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly upsertCustomFieldValues_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly updateOrganization_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
                readonly organizationId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateOrganizationAdminRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationAdminResponse"];
                };
            };
        };
    };
    readonly plan: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly planId: string;
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
                    readonly "application/json": components["schemas"]["PlanResponse"];
                };
            };
        };
    };
    readonly updatePlan: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly planId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdatePlanRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["PlanResponse"];
                };
            };
        };
    };
    readonly get: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
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
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly update: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateRoleRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly get_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly capabilityId: string;
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
                    readonly "application/json": components["schemas"]["CapabilityResponse"];
                };
            };
        };
    };
    readonly update_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly capabilityId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": {
                    readonly [key: string]: string;
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
                    readonly "application/json": components["schemas"]["CapabilityResponse"];
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
    readonly runImport: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
    readonly uploadImport: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
            readonly header?: {
                readonly "Idempotency-Key"?: string;
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
    readonly listUsers: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User list */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": readonly components["schemas"]["UserResponse"][];
                };
            };
            /** @description Missing or invalid tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly createUser: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateUserRequest"];
            };
        };
        readonly responses: {
            /** @description User created */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description Validation failed */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Tenant not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Email already exists in tenant */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly listOrganizations: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Organization list (possibly empty) */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": readonly components["schemas"]["OrganizationResponse"][];
                };
            };
            /** @description Bad Request - missing required tenantId query parameter */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly createOrganization: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateOrganizationRequest"];
            };
        };
        readonly responses: {
            /** @description Organization created successfully */
            readonly 201: {
                headers: {
                    /** @description URI of the newly created Organization resource */
                    readonly Location?: string;
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - request body failed validation (missing tenantId, blank name, name or description too long) */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Tenant Not Found - the referenced tenantId does not exist */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Already Exists - an Organization with the same name already exists under the same Tenant */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly listMemberships: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly organizationId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Membership list (possibly empty) */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": readonly components["schemas"]["OrganizationMembershipResponse"][];
                };
            };
            /** @description Missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly inviteMember: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly organizationId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["InviteOrganizationMemberRequest"];
            };
        };
        readonly responses: {
            /** @description Member invited successfully */
            readonly 201: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
            /** @description Bad Request - validation failed or missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Tenant or Organization not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Membership already exists for this email */
            readonly 409: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly bootstrapAdmin: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "X-Control-Plane-Bootstrap-Token"?: string;
            };
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listTasks: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly status?: string;
                readonly assigneeId?: string;
                readonly relatedId?: string;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateTaskRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listTags: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createTag: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateTagRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listAssignmentsByTag: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly assignTag: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AssignTagRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly unassignTag: {
        readonly parameters: {
            readonly query: {
                readonly subjectType: string;
                readonly subjectId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
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
                content?: never;
            };
        };
    };
    readonly listPipelines: {
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createPipeline: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreatePipelineRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listOpportunities_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createOpportunity_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateOpportunityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listNotes: {
        readonly parameters: {
            readonly query: {
                readonly subjectId: string;
                readonly subjectType: string;
                readonly limit?: number;
                readonly includeArchived?: boolean;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createNote: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateNoteRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listLeads_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createLead_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateLeadRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly convertLead_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly runImport_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly cancelImport_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly uploadImport_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listCustomFields_1: {
        readonly parameters: {
            readonly query?: {
                readonly entityType?: string;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createCustomField_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listContacts_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createContact_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateContactRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listActivities_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createActivity_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateActivityRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listAccounts_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly createAccount_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateAccountRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly merge: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly sourceAccountId: string;
                readonly targetAccountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["MergeRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["MergeResult"];
                };
            };
        };
    };
    readonly relationships: {
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
                    readonly "application/json": readonly components["schemas"]["AccountRelationship"][];
                };
            };
        };
    };
    readonly addRelationship: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateRelationshipRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["AccountRelationship"];
                };
            };
        };
    };
    readonly identifiers: {
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
                    readonly "application/json": readonly components["schemas"]["AccountIdentifier"][];
                };
            };
        };
    };
    readonly addIdentifier: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateIdentifierRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["AccountIdentifier"];
                };
            };
        };
    };
    readonly addresses: {
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
                    readonly "application/json": readonly components["schemas"]["AccountAddress"][];
                };
            };
        };
    };
    readonly addAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["AccountAddress"];
                };
            };
        };
    };
    readonly listTenants: {
        readonly parameters: {
            readonly query?: {
                readonly search?: string;
                readonly status?: string;
                readonly limit?: number;
                readonly offset?: number;
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
                    readonly "application/json": readonly components["schemas"]["TenantResponse"][];
                };
            };
        };
    };
    readonly createTenant: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateTenantRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["TenantResponse"];
                };
            };
        };
    };
    readonly organizations: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
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
                    readonly "application/json": readonly components["schemas"]["OrganizationAdminResponse"][];
                };
            };
        };
    };
    readonly createOrganization_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateOrganizationAdminRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationAdminResponse"];
                };
            };
        };
    };
    readonly memberships: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
                readonly organizationId: string;
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
                    readonly "application/json": readonly components["schemas"]["MembershipAdminResponse"][];
                };
            };
        };
    };
    readonly createMembership: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
                readonly organizationId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateMembershipAdminRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["MembershipAdminResponse"];
                };
            };
        };
    };
    readonly subscriptions: {
        readonly parameters: {
            readonly query?: {
                readonly tenantId?: string;
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
                    readonly "application/json": readonly components["schemas"]["SubscriptionResponse"][];
                };
            };
        };
    };
    readonly createSubscription: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateSubscriptionRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly renewSubscription: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
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
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly plans: {
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
                    readonly "application/json": readonly components["schemas"]["PlanResponse"][];
                };
            };
        };
    };
    readonly createPlan: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreatePlanRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["PlanResponse"];
                };
            };
        };
    };
    readonly execute: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["HealthActionRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["HealthActionResult"];
                };
            };
        };
    };
    readonly markInvoicePaid: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly invoiceId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["MarkInvoicePaidRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["InvoiceResponse"];
                };
            };
        };
    };
    readonly resetPassword: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ResetPasswordRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly register: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SelfRegistrationRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SelfRegistrationResponse"];
                };
            };
        };
    };
    readonly refresh: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "application/json": components["schemas"]["RefreshRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["AuthResponse"];
                };
            };
        };
    };
    readonly logout: {
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
                content?: never;
            };
        };
    };
    readonly login: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["LoginRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["AuthResponse"];
                };
            };
        };
    };
    readonly forgotPassword: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ForgotPasswordRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly changeCredential: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeCredentialRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    readonly adminResetPassword: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AdminResetPasswordRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly grant: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
                readonly organizationId?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
                readonly roleId: string;
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
                    readonly "application/json": components["schemas"]["UserAccessResponse"];
                };
            };
        };
    };
    readonly list: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
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
                    readonly "application/json": readonly components["schemas"]["RoleResponse"][];
                };
            };
        };
    };
    readonly create: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateRoleRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly attach: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
                readonly capabilityId: string;
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
                    readonly "application/json": components["schemas"]["RoleAccessResponse"];
                };
            };
        };
    };
    readonly detach: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
                readonly capabilityId: string;
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
                content?: never;
            };
        };
    };
    readonly list_1: {
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
                    readonly "application/json": readonly components["schemas"]["CapabilityResponse"][];
                };
            };
        };
    };
    readonly create_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": {
                    readonly [key: string]: string;
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
                    readonly "application/json": components["schemas"]["CapabilityResponse"];
                };
            };
        };
    };
    readonly updatePipeline: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly changeLeadStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly updateCustomField: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly restoreContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly archiveContact: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly reactivate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly setPrimary: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly deactivate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly activate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly restoreAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly archiveAccount: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: {
                readonly "If-Match"?: string;
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
    readonly suspendUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User suspended */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly deactivateUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User deactivated */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly archiveUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User archived */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly activateUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description User activated */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["UserResponse"];
                };
            };
            /** @description User not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly unassignUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly organizationId: string;
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
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
        };
    };
    readonly removeMembership: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly organizationId: string;
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Membership removed (soft delete) */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
            /** @description Missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Membership not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly deactivateMembership: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly organizationId: string;
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Membership deactivated */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
            /** @description Missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Membership not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly assignUser: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly organizationId: string;
                readonly membershipId: string;
                readonly userId: string;
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
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
        };
    };
    readonly activateMembership: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly organizationId: string;
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Membership activated */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
            /** @description Missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Membership not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly deactivateOrganization: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly id: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Organization deactivated successfully */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - missing required tenantId query parameter */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Not Found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly archiveOrganization: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly id: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Organization archived successfully (or was already archived) */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - missing required tenantId query parameter */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Not Found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly activateOrganization: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly id: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Organization activated successfully */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationResponse"];
                };
            };
            /** @description Bad Request - missing required tenantId query parameter */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Organization Not Found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly getTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly updateTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateTaskRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly startTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly completeTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "application/json": components["schemas"]["CompleteTaskRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly cancelTask: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "application/json": components["schemas"]["CancelTaskRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getTag: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly deleteTag: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
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
                content?: never;
            };
        };
    };
    readonly updateTag: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tagId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateTagRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly moveOpportunity: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly archiveNote: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly noteId: string;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly changeLeadStatus_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getContact_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly updateContact_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly restoreContact_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly archiveContact_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly completeActivity_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getAccount_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly updateAccount_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly restoreAccount_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getMaster: {
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
                    readonly "application/json": components["schemas"]["CustomerMasterProfile"];
                };
            };
        };
    };
    readonly updateMaster: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateMasterRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["CustomerMasterProfile"];
                };
            };
        };
    };
    readonly archiveAccount_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly changeTenantStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeTenantStatusRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["TenantResponse"];
                };
            };
        };
    };
    readonly changeOrganizationStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
                readonly organizationId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeStatusRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationAdminResponse"];
                };
            };
        };
    };
    readonly updateMembership: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
                readonly organizationId: string;
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateMembershipAdminRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["MembershipAdminResponse"];
                };
            };
        };
    };
    readonly updateSystemStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly serviceId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateSystemStatusRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SystemServiceResponse"];
                };
            };
        };
    };
    readonly changeSeats: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeSeatsRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly resumeSubscription: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
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
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly changeSubscriptionPlan: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeSubscriptionPlanRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly cancelSubscription: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CancelSubscriptionRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly changePlanStatus: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly planId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ChangeStatusRequest"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["PlanResponse"];
                };
            };
        };
    };
    readonly revoke: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly grantId: string;
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
                    readonly "application/json": components["schemas"]["UserAccessResponse"];
                };
            };
        };
    };
    readonly deactivate_1: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
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
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly archive_1: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
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
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly activate_1: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
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
                    readonly "application/json": components["schemas"]["RoleResponse"];
                };
            };
        };
    };
    readonly deactivate_2: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly capabilityId: string;
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
                    readonly "application/json": components["schemas"]["CapabilityResponse"];
                };
            };
        };
    };
    readonly activate_2: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly capabilityId: string;
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
                    readonly "application/json": components["schemas"]["CapabilityResponse"];
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
    readonly listMemberships_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly userId: string;
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
                    readonly "application/json": readonly components["schemas"]["OrganizationMembershipResponse"][];
                };
            };
        };
    };
    readonly getMembership: {
        readonly parameters: {
            readonly query: {
                /** @description Tenant UUID (scope) */
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                /** @description Organization UUID */
                readonly organizationId: string;
                /** @description Membership UUID */
                readonly membershipId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description Membership found */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["OrganizationMembershipResponse"];
                };
            };
            /** @description Missing tenantId */
            readonly 400: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
            /** @description Membership not found */
            readonly 404: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "application/json": components["schemas"]["ApiErrorResponse"];
                };
            };
        };
    };
    readonly timeline_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly listAssignmentsBySubject: {
        readonly parameters: {
            readonly query: {
                readonly subjectType: string;
                readonly subjectId: string;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly search: {
        readonly parameters: {
            readonly query: {
                readonly q: string;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly salesPipeline: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly leadConversion: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly dashboard: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly activitySummary: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly accountGrowth: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listPipelineStages_1: {
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly getOpportunity_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getNote: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly noteId: string;
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getLead_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listImportJobs_1: {
        readonly parameters: {
            readonly query?: {
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly getImportJob_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly listImportErrors_1: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly downloadImportErrors_1: {
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
    readonly exportLeads: {
        readonly parameters: {
            readonly query?: {
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
                content?: never;
            };
        };
    };
    readonly exportContacts: {
        readonly parameters: {
            readonly query?: {
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
                content?: never;
            };
        };
    };
    readonly exportAccounts: {
        readonly parameters: {
            readonly query?: {
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
                content?: never;
            };
        };
    };
    readonly dashboard_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly readSensitiveCustomFieldValues: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly searchCustomFieldValues_1: {
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
                    readonly "application/json": readonly {
                        readonly [key: string]: unknown;
                    }[];
                };
            };
        };
    };
    readonly getActivity_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly duplicates: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
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
                    readonly "application/json": readonly components["schemas"]["DuplicateCandidate"][];
                };
            };
        };
    };
    readonly customer360_1: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly getTenant: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly tenantId: string;
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
                    readonly "application/json": components["schemas"]["TenantResponse"];
                };
            };
        };
    };
    readonly systems: {
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
                    readonly "application/json": readonly components["schemas"]["SystemServiceResponse"][];
                };
            };
        };
    };
    readonly subscription: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
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
                    readonly "application/json": components["schemas"]["SubscriptionResponse"];
                };
            };
        };
    };
    readonly subscriptionEvents: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
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
                    readonly "application/json": readonly components["schemas"]["SubscriptionEventResponse"][];
                };
            };
        };
    };
    readonly subscriptionEntitlements: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly subscriptionId: string;
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
                    readonly "application/json": readonly components["schemas"]["EntitlementResponse"][];
                };
            };
        };
    };
    readonly health: {
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
                    readonly "application/json": components["schemas"]["PlatformHealthResponse"];
                };
            };
        };
    };
    readonly dashboard_2: {
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
                    readonly "application/json": components["schemas"]["DashboardResponse"];
                };
            };
        };
    };
    readonly invoices: {
        readonly parameters: {
            readonly query?: {
                readonly tenantId?: string;
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
                    readonly "application/json": readonly components["schemas"]["InvoiceResponse"][];
                };
            };
        };
    };
    readonly audit: {
        readonly parameters: {
            readonly query?: {
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
                    readonly "application/json": readonly components["schemas"]["AuditEntryResponse"][];
                };
            };
        };
    };
    readonly accessCheck: {
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
                    readonly "application/json": {
                        readonly [key: string]: unknown;
                    };
                };
            };
        };
    };
    readonly me: {
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
                    readonly "application/json": components["schemas"]["MeResponse"];
                };
            };
        };
    };
    readonly list_2: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
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
                    readonly "application/json": readonly components["schemas"]["UserAccessResponse"][];
                };
            };
        };
    };
    readonly list_3: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roleId: string;
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
                    readonly "application/json": readonly components["schemas"]["RoleAccessResponse"][];
                };
            };
        };
    };
    readonly evaluate: {
        readonly parameters: {
            readonly query: {
                readonly tenantId: string;
                readonly userId: string;
                readonly capabilityCode: string;
                readonly organizationId?: string;
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
                    readonly "application/json": components["schemas"]["AccessDecisionResponse"];
                };
            };
        };
    };
    readonly deactivateAddress: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly accountId: string;
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
                content?: never;
            };
        };
    };
}
