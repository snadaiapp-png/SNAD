package com.sanad.platform.crm.activity.application;

import com.sanad.platform.crm.activity.domain.ActivityRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActivityModuleConfiguration {
    @Bean
    public ActivityUseCases activityUseCases(ActivityRepository repo) { return new ActivityUseCases(repo); }
}
