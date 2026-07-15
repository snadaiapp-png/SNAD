package com.sanad.platform.crm.note.application;

import com.sanad.platform.crm.note.domain.NoteRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Note bounded context.
 * <p>
 * Branch: feature/crm-notes
 */
@Configuration
public class NoteModuleConfiguration {

    @Bean
    public NoteUseCases noteUseCases(NoteRepository repo) {
        return new NoteUseCases(repo);
    }
}
