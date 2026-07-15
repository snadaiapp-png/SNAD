package com.sanad.platform.crm.query.application;

import com.sanad.platform.crm.query.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryModuleConfiguration {
    @Bean
    public QueryUseCases queryUseCases(TimelineProjectionRepository timelineRepo, DashboardQueryPort dashboardPort, Customer360QueryPort customer360Port) {
        return new QueryUseCases(timelineRepo, dashboardPort, customer360Port);
    }
}
