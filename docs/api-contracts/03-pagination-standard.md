# SNAD Pagination Standard

## Offset Pagination (Default)

### Parameters
| Parameter | Type | Default | Min | Max |
|---|---|---|---|---|
| page | Integer | 0 | 0 | — |
| size | Integer | 20 | 1 | 100 |
| sort | String | — | — | — |

### Sort Format
```
sort=field,asc
sort=field,desc
sort=name,asc&sort=createdAt,desc
```

### Response Structure
```json
{
  "content": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "first": true,
    "last": false,
    "hasNext": true,
    "hasPrevious": false,
    "sort": [{"field": "createdAt", "direction": "desc"}]
  }
}
```

## Cursor Pagination (Future)
Reserved for: audit streams, activity feeds, event histories, large transaction feeds.
Parameters: `cursor`, `limit`, `nextCursor`, `hasMore`.

## Tenant Isolation
Filtering order: Tenant → Authorization → Business → Sort → Pagination
