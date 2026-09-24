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
public class AnalyticsDataSourceService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsDataSourceService.class);
    private final AnalyticsDataSourceRepository repo;

    public AnalyticsDataSourceService(AnalyticsDataSourceRepository repo) { this.repo = repo; }

    @Transactional public AnalyticsDataSource create(AnalyticsDataSource ds) { var s = repo.save(ds); log.info("AnalyticsDataSource created: {} code={}", s.tenantId(), s.code()); return s; }
    @Transactional(readOnly = true) public Optional<AnalyticsDataSource> findById(UUID t, UUID i) { return repo.findById(t, i); }
    @Transactional(readOnly = true) public List<AnalyticsDataSource> findByTenant(UUID t, int l) { return repo.findByTenant(t, l); }
    @Transactional public AnalyticsDataSource activate(UUID t, UUID i) { return repo.save(load(t, i).activate()); }
    @Transactional public AnalyticsDataSource deactivate(UUID t, UUID i) { return repo.save(load(t, i).deactivate()); }
    @Transactional public AnalyticsDataSource markError(UUID t, UUID i) { return repo.save(load(t, i).markError()); }
    private AnalyticsDataSource load(UUID t, UUID i) { return repo.findById(t, i).orElseThrow(() -> new IllegalArgumentException("DataSource not found: " + i)); }
}
