# SNAD API Versioning Policy

## Current Version
- **v1** (URI path: `/api/v1/`)

## Versioning Strategy
- **URI path versioning only** — no header, query param, or media type versioning

## Non-Breaking Changes (Allowed)
- Optional field additions to request/response
- New endpoints
- New optional query parameters
- Adding enum values

## Breaking Changes (Require v2)
- Field removal from response
- Field rename
- Type change
- Required field addition to request
- Enum value removal
- Status code change for existing scenario

## Deprecation Policy
- Deprecated endpoints remain functional for at least 2 minor versions
- `Deprecation` and `Sunset` headers added to deprecated responses
