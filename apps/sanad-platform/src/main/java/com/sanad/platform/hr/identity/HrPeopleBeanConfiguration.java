package com.sanad.platform.hr.identity;

import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — Spring wiring for the People v2 slice.
 *
 * <p>The certified WS2 identity classes remain annotation-free (plain,
 * final, constructor-injected); this configuration exposes
 * {@link HrPersonService} and its normalizer to the production context.
 * {@link HrPersonV2Service} and {@code HrPeopleController} are
 * component-scanned; the {@link HrPersonRepository} bean is provided by
 * {@code HrEmploymentBeanConfiguration}.
 */
@Configuration
public class HrPeopleBeanConfiguration {

    @Bean
    public IdentifierNormalizer hrIdentifierNormalizer() {
        return new IdentifierNormalizer();
    }

    @Bean
    public HrPersonService hrPersonService(HrPersonRepository repository,
                                           PlatformCryptographyService cryptographyService,
                                           IdentifierNormalizer normalizer) {
        return new HrPersonService(repository, cryptographyService, normalizer);
    }
}
