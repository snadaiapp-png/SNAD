package com.sanad.platform.crm.pagination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRM API Contract — Response Envelopes.
 * <p>
 * Single-item responses:
 * <pre>{@code
 * {
 *   "data": { ... },
 *   "meta": { "requestId": "uuid", "timestamp": "..." }
 * }
 * }</pre>
 * <p>
 * List responses (cursor-paginated):
 * <pre>{@code
 * {
 *   "data": [ ... ],
 *   "page": { "nextCursor": "opaque", "hasMore": true, "limit": 50 },
 *   "meta": { "requestId": "uuid", "timestamp": "..." }
 * }
 * }</pre>
 * <p>
 * The envelope is generic so the OpenAPI generator can produce a
 * parameterized schema for every response DTO. The frontend
 * {@code openapi-typescript} generator then produces matching TS types.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public final class CrmEnvelopes {

    private CrmEnvelopes() {}

    public record Meta(UUID requestId, Instant timestamp) {
        public static Meta of(UUID requestId) {
            return new Meta(requestId, Instant.now());
        }
    }

    public record Page(String nextCursor, boolean hasMore, int limit) {
        public static Page empty(int limit) {
            return new Page(null, false, limit);
        }
        public static Page of(String nextCursor, boolean hasMore, int limit) {
            return new Page(nextCursor, hasMore, limit);
        }
    }

    public record SingleResponse<T>(T data, Meta meta) {
        public static <T> SingleResponse<T> of(T data, UUID requestId) {
            return new SingleResponse<>(data, Meta.of(requestId));
        }
    }

    public record ListResponse<T>(List<T> data, Page page, Meta meta) {
        public static <T> ListResponse<T> of(List<T> data, Page page, UUID requestId) {
            return new ListResponse<>(data, page, Meta.of(requestId));
        }
    }
}
