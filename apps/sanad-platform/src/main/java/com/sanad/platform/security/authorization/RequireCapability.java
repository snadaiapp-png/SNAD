package com.sanad.platform.security.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated endpoint requires the caller to possess
 * a specific capability within the current tenant scope.
 *
 * <p>The {@link CapabilityAuthorizationAspect} intercepts methods bearing
 * this annotation and delegates to {@link com.sanad.platform.access.evaluation.CapabilityEvaluationService}
 * to decide whether the authenticated user holds the required capability.
 * If the evaluation returns DENY, the request is rejected with HTTP 403.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;RequireCapability("USER.CREATE")
 * &#64;PostMapping
 * public ResponseEntity&lt;UserResponse&gt; createUser(...) { ... }
 * </pre>
 *
 * @see CapabilityAuthorizationAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireCapability {

    /**
     * The capability code that the caller must possess
     * (e.g., {@code "USER.READ"}, {@code "ORGANIZATION.WRITE"}).
     */
    String value();
}
