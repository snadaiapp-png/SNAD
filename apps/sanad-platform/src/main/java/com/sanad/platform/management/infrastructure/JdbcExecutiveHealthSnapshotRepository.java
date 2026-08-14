package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.ExecutiveHealthSnapshot;
import com.sanad.platform.management.domain.ExecutiveHealthSnapshotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExecutiveHealthSnapshotRepository implements ExecutiveHealthSnapshotRepository {

    private final JdbcTemplate jdbc;

    public JdbcExecutiveHealthSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ExecutiveHealthSnapshot> MAPPER = (rs, rowNum) -> new ExecutiveHealthSnapshot(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getInt("health_score"),
            rs.getInt("strategy_score"),
            rs.getInt("kpi_score"),
            rs.getInt("decision_score"),
            rs.getInt("risk_score"),
            rs.getInt("issue_score"),
            rs.getInt("escalation_score"),
            rs.getInt("total_objectives"),
            rs.getInt("active_objectives"),
            rs.getInt("at_risk_objectives"),
            rs.getInt("off_track_objectives"),
            rs.getInt("total_kpis"),
            rs.getInt("on_track_kpis"),
            rs.getInt("at_risk_kpis"),
            rs.getInt("off_track_kpis"),
            rs.getInt("pending_decisions"),
            rs.getInt("overdue_decisions"),
            rs.getInt("critical_risks"),
            rs.getInt("high_risks"),
            rs.getInt("open_issues"),
            rs.getInt("critical_issues"),
            rs.getInt("active_escalations"),
            rs.getInt("overdue_escalations"),
            rs.getInt("active_alerts"),
            rs.getTimestamp("snapshot_at").toInstant()
    );

    @Override
    public ExecutiveHealthSnapshot save(ExecutiveHealthSnapshot s) {
        jdbc.update("""
                INSERT INTO executive_health_snapshots
                    (id, tenant_id, health_score, strategy_score, kpi_score, decision_score,
                     risk_score, issue_score, escalation_score, total_objectives, active_objectives,
                     at_risk_objectives, off_track_objectives, total_kpis, on_track_kpis, at_risk_kpis,
                     off_track_kpis, pending_decisions, overdue_decisions, critical_risks, high_risks,
                     open_issues, critical_issues, active_escalations, overdue_escalations,
                     active_alerts, snapshot_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                s.id(), s.tenantId(), s.healthScore(), s.strategyScore(), s.kpiScore(),
                s.decisionScore(), s.riskScore(), s.issueScore(), s.escalationScore(),
                s.totalObjectives(), s.activeObjectives(), s.atRiskObjectives(), s.offTrackObjectives(),
                s.totalKpis(), s.onTrackKpis(), s.atRiskKpis(), s.offTrackKpis(),
                s.pendingDecisions(), s.overdueDecisions(), s.criticalRisks(), s.highRisks(),
                s.openIssues(), s.criticalIssues(), s.activeEscalations(), s.overdueEscalations(),
                s.activeAlerts(), Timestamp.from(s.snapshotAt())
        );
        return s;
    }

    @Override
    public Optional<ExecutiveHealthSnapshot> findLatest(UUID tenantId) {
        return jdbc.query("""
                SELECT * FROM executive_health_snapshots
                WHERE tenant_id = ? ORDER BY snapshot_at DESC LIMIT 1
                """, MAPPER, tenantId).stream().findFirst();
    }

    @Override
    public List<ExecutiveHealthSnapshot> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT * FROM executive_health_snapshots
                WHERE tenant_id = ? ORDER BY snapshot_at DESC LIMIT ?
                """, MAPPER, tenantId, limit);
    }
}
