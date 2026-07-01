package com.sanad.platform.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stage 05A.1 §22 — Marks a controller method as an idempotent operation.
 *
 * <p>When a {@code @PostMapping} (or other mutating HTTP method) is annotated
 * with {@code @IdempotentOperation}, the {@link IdempotencyCommandInterceptor}
 * intercepts the request BEFORE the controller method executes and:</p>
 *
 * <ol>
 *   <li>Requires the {@code Idempotency-Key} HTTP request header (else 400
 *       {@code SANAD-IDEMP-001}).</li>
 *   <li>Calls {@link com.sanad.platform.idempotency.service.IdempotencyService#reserveOrReplay}
 *       with the key, the {@link #operation()} identifier, the request route,
 *       the HTTP method, and the request body.</li>
 *   <li>If the reservation is {@code NEW}: allows the controller method to
 *       execute, then calls {@code complete()} with the resulting status,
 *       headers, and body.</li>
 *   <li>If the reservation is {@code REPLAY}: short-circuits the controller,
 *       returning the stored response with an {@code Idempotency-Replayed: true}
 *       header.</li>
 *   <li>If the reservation is {@code CONFLICT}: returns 409
 *       {@code SANAD-IDEMP-002} (same key, different payload).</li>
 *   <li>If the reservation is {@code IN_PROGRESS}: returns 409
 *       {@code SANAD-IDEMP-003} (another request is still processing).</li>
 * </ol>
 *
 * <p>The annotation's {@code operation} value is a stable, versioned identifier
 * for the business operation (e.g. {@code "ORGANIZATION.CREATE"}). It is part
 * of the idempotency unique key, so changing it invalidates any in-flight
 * idempotency records.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @PostMapping
 * @RequireCapability("ORGANIZATION.CREATE")
 * @IdempotentOperation(operation = "ORGANIZATION.CREATE")
 * public ResponseEntity<OrganizationResponse> createOrganization(
 *         @Valid @RequestBody CreateOrganizationRequest request) {
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentOperation {

    /**
     * The stable, versioned operation identifier (e.g. {@code "ORGANIZATION.CREATE"}).
     * Included in the idempotency unique key and the request fingerprint.
     */
    String operation();

    /**
     * The resource type for the idempotency record (e.g. {@code "Organization"}).
     * Optional — defaults to empty string.
     */
    String resourceType() default "";
}
