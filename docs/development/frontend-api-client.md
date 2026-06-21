# SANAD Frontend API Client

## Purpose

`apps/web/lib/api` is the single frontend integration boundary for SANAD backend calls. It provides typed requests, URL/query construction, timeouts, cancellation, normalized errors, and a stable health-check contract.

## Usage

```ts
import { apiClient } from "@/lib/api";

const organizations = await apiClient.get<OrganizationResponse[]>(
  "/api/v1/organizations",
  { query: { tenantId } }
);
```

```ts
const organization = await apiClient.post<OrganizationResponse, CreateOrganizationRequest>(
  "/api/v1/organizations",
  request,
  { query: { tenantId } }
);
```

## Backend contracts

- Base path: `/api/v1`
- Current tenant scope: required `tenantId` query parameter, not a header
- Health: `GET /actuator/health`
- Error body when handled: `timestamp`, `status`, `error`, `message`, `path`
- No automatic authentication injection in EXEC-PROMPT-029
- No automatic tenant resolution in EXEC-PROMPT-029

## Error handling

Use the exported classes/type guards:

- `ApiConfigurationError`
- `ApiTimeoutError`
- `ApiNetworkError`
- `ApiHttpError`
- `ApiRequestSerializationError`
- `ApiResponseParseError`
- `ApiClientCancellation`

Never show raw stack traces, response bodies, cookies, or authorization data to end users.

## Cancellation

Pass an `AbortSignal` through request options. Internal timeouts and external cancellation are deliberately classified separately.

## Security

- `Authorization`, `Host`, `Origin`, `Connection`, and `Content-Length` cannot be supplied through custom caller headers.
- Non-local HTTP base URLs are rejected.
- Base URLs containing credentials are rejected.
- No secrets belong in `NEXT_PUBLIC_*` variables.

## Testing

From `apps/web`:

```bash
npm ci
npm run lint
npm test
npm run build
```
