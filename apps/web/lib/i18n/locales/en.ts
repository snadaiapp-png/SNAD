/**
 * SNAD i18n — English (en) translation dictionary
 * ----------------------------------------------------------------------------
 * Every key here MUST match `ar.ts` exactly. The CI script
 * `check-i18n-keys.py` enforces key parity between the two dictionaries.
 */
import type { TranslationDictionary } from "../types";

export const en: TranslationDictionary = {
  // === Brand ===
  "brand.name": "SNAD",
  "brand.fullName": "SNAD Business Operating System",
  "brand.tagline": "Unified Business Intelligence Platform",

  // === Auth — Login ===
  "auth.login.title": "Sign in",
  "auth.login.subtitle": "Enter your credentials to access your workspace",
  "auth.login.email": "Email",
  "auth.login.emailPlaceholder": "name@company.com",
  "auth.login.password": "Password",
  "auth.login.passwordPlaceholder": "••••••••",
  "auth.login.submit": "Sign in",
  "auth.login.submitting": "Signing in…",
  "auth.login.forgotPassword": "Forgot password?",
  "auth.login.rememberMe": "Remember me",

  // === Auth — Tenant Picker ===
  "auth.tenant.title": "Select organization",
  "auth.tenant.subtitle": "You have access to multiple organizations. Choose one to continue.",
  "auth.tenant.select": "Select organization",
  "auth.tenant.continue": "Continue",
  "auth.tenant.cancel": "Cancel",

  // === Auth — Credential Rotation ===
  "auth.rotation.title": "Update credentials",
  "auth.rotation.subtitle": "Please set a new password to complete sign-in.",
  "auth.rotation.currentPassword": "Current password",
  "auth.rotation.newPassword": "New password",
  "auth.rotation.confirmPassword": "Confirm password",
  "auth.rotation.submit": "Update & sign in",
  "auth.rotation.cancel": "Cancel",

  // === Auth — Errors ===
  "auth.error.invalidCredentials": "Invalid email or password.",
  "auth.error.network": "Could not reach the server. Check your internet connection.",
  "auth.error.unknown": "An unexpected error occurred. Try again.",
  "auth.error.expired": "Your session has expired. Please sign in again.",
  "auth.error.rateLimited": "Too many attempts. Try again in a minute.",

  // === Auth — Loading ===
  "auth.loading.restoring": "Restoring session…",
  "auth.loading.redirecting": "Redirecting to workspace…",

  // === Navigation ===
  "nav.workspace": "Workspace",
  "nav.controlPlane": "Control Plane",
  "nav.crm": "CRM",
  "nav.settings": "Settings",
  "nav.profile": "Profile",
  "nav.logout": "Sign out",

  // === Workspace ===
  "workspace.title": "Workspace",
  "workspace.welcome": "Welcome, {name}",
  "workspace.overview": "Overview",
  "workspace.recentActivity": "Recent activity",
  "workspace.quickActions": "Quick actions",
  "workspace.stats.totalMembers": "Total members",
  "workspace.stats.activeTenants": "Active organizations",
  "workspace.stats.pendingTasks": "Pending tasks",

  // === Control Plane ===
  "controlPlane.title": "Control Plane",
  "controlPlane.tenants": "Organizations",
  "controlPlane.users": "Users",
  "controlPlane.roles": "Roles",
  "controlPlane.auditLog": "Audit log",
  "controlPlane.settings": "Settings",

  // === CRM ===
  "crm.title": "CRM",
  "crm.contacts": "Contacts",
  "crm.deals": "Deals",
  "crm.pipeline": "Pipeline",
  "crm.activities": "Activities",

  // === Forms — Labels ===
  "form.label.email": "Email",
  "form.label.password": "Password",
  "form.label.name": "Name",
  "form.label.phone": "Phone",
  "form.label.organization": "Organization",
  "form.label.role": "Role",
  "form.label.status": "Status",
  "form.label.createdAt": "Created at",
  "form.label.updatedAt": "Updated at",

  // === Forms — Actions ===
  "form.action.save": "Save",
  "form.action.cancel": "Cancel",
  "form.action.delete": "Delete",
  "form.action.edit": "Edit",
  "form.action.create": "Create",
  "form.action.update": "Update",
  "form.action.confirm": "Confirm",
  "form.action.back": "Back",
  "form.action.next": "Next",
  "form.action.previous": "Previous",

  // === Form Validation ===
  "form.validation.required": "This field is required.",
  "form.validation.email": "Enter a valid email address.",
  "form.validation.minLength": "Must be at least {min} characters.",
  "form.validation.maxLength": "Must not exceed {max} characters.",
  "form.validation.passwordMatch": "Passwords do not match.",
  "form.validation.phone": "Enter a valid phone number.",

  // === Loading States ===
  "loading.default": "Loading…",
  "loading.data": "Loading data…",
  "loading.saving": "Saving…",
  "loading.processing": "Processing…",

  // === Errors ===
  "error.title": "An error occurred",
  "error.generic": "An unexpected error occurred. Try again later.",
  "error.notFound": "Page not found.",
  "error.unauthorized": "You are not authorized to access this.",
  "error.forbidden": "You do not have sufficient permission.",
  "error.server": "Server error. Try again later.",
  "error.network": "Could not reach the server.",
  "error.retry": "Retry",

  // === Empty States ===
  "empty.default": "No data to display.",
  "empty.search": "No results match your search.",
  "empty.list": "The list is empty.",
  "empty.createFirst": "Start by creating your first item.",

  // === Toasts ===
  "toast.success": "Operation completed successfully.",
  "toast.error": "Operation failed.",
  "toast.saved": "Saved successfully.",
  "toast.deleted": "Deleted successfully.",
  "toast.updated": "Updated successfully.",
  "toast.created": "Created successfully.",

  // === Modals ===
  "modal.close": "Close",
  "modal.confirm": "Confirm",
  "modal.confirmDelete": "Confirm delete",
  "modal.deleteWarning": "This action cannot be undone. Are you sure?",
  "modal.cancel": "Cancel",

  // === Tables ===
  "table.actions": "Actions",
  "table.rowsPerPage": "Rows per page",
  "table.page": "Page",
  "table.of": "of",
  "table.empty": "No data.",
  "table.search": "Search…",
  "table.sortBy": "Sort by",
  "table.filter": "Filter",

  // === Filters ===
  "filter.title": "Filter",
  "filter.apply": "Apply",
  "filter.clear": "Clear",
  "filter.clearAll": "Clear all",
  "filter.active": "Active",
  "filter.inactive": "Inactive",
  "filter.all": "All",

  // === Theme & Language (switcher labels) ===
  "theme.label": "Theme",
  "theme.light": "Light",
  "theme.dark": "Dark",
  "theme.system": "System",
  "language.label": "Language",
  "language.arabic": "العربية",
  "language.english": "English",

  // === Common ===
  "common.yes": "Yes",
  "common.no": "No",
  "common.ok": "OK",
  "common.close": "Close",
  "common.back": "Back",
  "common.next": "Next",
  "common.previous": "Previous",
  "common.search": "Search",
  "common.actions": "Actions",
  "common.optional": "Optional",
  "common.required": "Required",
};
