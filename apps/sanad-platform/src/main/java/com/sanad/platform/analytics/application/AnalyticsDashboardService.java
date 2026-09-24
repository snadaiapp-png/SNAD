package com.sanad.platform.analytics.application;

import com.sanad.platform.analytics.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AnalyticsDashboardService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsDashboardService.class);
    private final AnalyticsDashboardRepository repo;

    public AnalyticsDashboardService(AnalyticsDashboardRepository repo) { this.repo = repo; }

    @Transactional public AnalyticsDashboard create(AnalyticsDashboard d) { var s = repo.save(d); log.info("AnalyticsDashboard created: {} code={}", s.tenantId(), s.code()); return s; }
    @Transactional(readOnly = true) public Optional<AnalyticsDashboard> findById(UUID t, UUID i) { return repo.findById(t, i); }
    @Transactional(readOnly = true) public List<AnalyticsDashboard> findByTenant(UUID t, int l) { return repo.findByTenant(t, l); }
    @Transactional public AnalyticsDashboard activate(UUID t, UUID i) { return repo.save(load(t, i).activate()); }
    @Transactional public AnalyticsDashboard deactivate(UUID t, UUID i) { return repo.save(load(t, i).deactivate()); }
    @Transactional public AnalyticsDashboard archive(UUID t, UUID i) { return repo.save(load(t, i).archive()); }
    private AnalyticsDashboard load(UUID t, UUID i) { return repo.findById(t, i).orElseThrow(() -> new IllegalArgumentException("Dashboard not found: " + i)); }
}
