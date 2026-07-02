package com.sanad.platform.idempotency;

import com.sanad.platform.idempotency.service.IdempotencyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Stage 05A.1 §22 — Registers {@link IdempotencyCommandInterceptor} for the
 * POST /api/v1/organizations endpoint (and any other endpoint annotated with
 * {@link IdempotentOperation}).
 *
 * <p>The interceptor self-filters by checking for the {@code @IdempotentOperation}
 * annotation on the target handler method, so it is safe to register it
 * broadly across all {@code /api/**} paths.</p>
 *
 * <p>Conditional on {@link IdempotencyService} so that slice tests
 * (e.g. {@code @WebMvcTest}) which don't load the service bean also skip
 * the interceptor and avoid pulling in its dependencies.</p>
 */
@Configuration
@ConditionalOnBean(IdempotencyService.class)
public class IdempotencyWebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyCommandInterceptor idempotencyInterceptor;

    public IdempotencyWebMvcConfig(IdempotencyCommandInterceptor idempotencyInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/**");
    }
}
