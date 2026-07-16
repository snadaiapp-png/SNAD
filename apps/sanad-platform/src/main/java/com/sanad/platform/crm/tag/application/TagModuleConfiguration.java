package com.sanad.platform.crm.tag.application;

import com.sanad.platform.crm.tag.domain.TagRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Tag bounded context.
 * <p>
 * Branch: feature/crm-tags
 */
@Configuration
public class TagModuleConfiguration {

    @Bean
    public TagUseCases tagUseCases(TagRepository repo) {
        return new TagUseCases(repo);
    }
}
