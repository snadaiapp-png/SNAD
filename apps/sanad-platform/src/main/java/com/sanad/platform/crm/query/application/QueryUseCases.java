package com.sanad.platform.crm.query.application;

import com.sanad.platform.crm.query.domain.Customer360QueryPort;
import com.sanad.platform.crm.query.domain.DashboardQueryPort;
import com.sanad.platform.crm.query.domain.TimelineProjectionRepository;
import com.sanad.platform.crm.query.domain.TimelineProjectionRepository.TimelineEvent;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only query use cases.
 * Never performs writes — only composes read models.
 */
@Service
public class QueryUseCases {
    private final TimelineProjectionRepository timelineRepo;
    private final DashboardQueryPort dashboardPort;
    private final Customer360QueryPort customer360Port;

    public QueryUseCases(TimelineProjectionRepository timelineRepo, DashboardQueryPort dashboardPort, Customer360QueryPort customer360Port) {
        this.timelineRepo = timelineRepo;
        this.dashboardPort = dashboardPort;
        this.customer360Port = customer360Port;
    }

    public List<TimelineEvent> getTimeline(UUID tenantId, String subjectType, UUID subjectId, int limit) {
        return timelineRepo.findBySubject(tenantId, subjectType, subjectId, limit);
    }

    public Map<String, Object> getDashboard(UUID tenantId) { return dashboardPort.getDashboardKpis(tenantId); }
    public Map<String, Object> getCustomer360(UUID tenantId, UUID accountId) { return customer360Port.getCustomer360(tenantId, accountId); }
}
