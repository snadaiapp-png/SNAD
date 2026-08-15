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
public class AnalyticsReportService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsReportService.class);
    private final AnalyticsReportRepository repo;

    public AnalyticsReportService(AnalyticsReportRepository repo) { this.repo = repo; }

    @Transactional public AnalyticsReport create(AnalyticsReport r) { var s = repo.save(r); log.info("AnalyticsReport created: {} code={}", s.tenantId(), s.code()); return s; }
    @Transactional(readOnly = true) public Optional<AnalyticsReport> findById(UUID t, UUID i) { return repo.findById(t, i); }
    @Transactional(readOnly = true) public List<AnalyticsReport> findByTenant(UUID t, int l) { return repo.findByTenant(t, l); }
    @Transactional public AnalyticsReport activate(UUID t, UUID i) { return repo.save(load(t, i).activate()); }
    @Transactional public AnalyticsReport schedule(UUID t, UUID i, String cron) { return repo.save(load(t, i).schedule(cron)); }
    @Transactional public AnalyticsReport archive(UUID t, UUID i) { return repo.save(load(t, i).archive()); }
    @Transactional public AnalyticsReport execute(UUID t, UUID i) { var r = load(t, i); return repo.save(r.markExecuted("SUCCESS")); }
    private AnalyticsReport load(UUID t, UUID i) { return repo.findById(t, i).orElseThrow(() -> new IllegalArgumentException("Report not found: " + i)); }
}
