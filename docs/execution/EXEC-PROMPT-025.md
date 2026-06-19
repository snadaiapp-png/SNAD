# EXEC-PROMPT-025 — Access Evaluation Engine

Status: IMPLEMENTED / PENDING FINAL CI

The evaluation engine defaults to denial. It considers only active user links, active roles, active catalog items, and matching tenant or organization scope. The API returns the result, reason, and matched role when allowed.

Login, JWT, and request interception remain out of scope.
