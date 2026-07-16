package com.sanad.platform.crm.activity.application;

import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActivityModuleConfiguration {
    @Bean
    public ActivityUseCases activityUseCases(ActivityRepository repo,
                                             TimelineEventPort timelineEventPort) {
        return new ActivityUseCases(repo, timelineEventPort);
    }
}
