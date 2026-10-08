package com.sanad.platform.hr.payroll.application;

import com.sanad.platform.hr.audit.HrAuthenticatedContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * G4-T8 application facade for the governed payroll HTTP API.
 *
 * <p>Reads and run creation remain HR-owned. Lifecycle mutation delegates to
 * G4-T5/T6 so optimistic concurrency, authorization, idempotency, audit and
 * outbox semantics are preserved. Accounting export delegates only through
 * the G4-T7 port and fails closed when no adapter is installed.</p>
 */
@Service
public class PayrollApiService {
    private final DataSource dataSource;
    private final PayrollLifecycleService lifecycleService;
    private final ObjectProvider<PayrollAccountingExportPort> accountingExportPort;

    public PayrollApiService(DataSource dataSource,
                             PayrollLifecycleService lifecycleService,
                             ObjectProvider<PayrollAccountingExportPort> accountingExportPort) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.accountingExportPort = Objects.requireNonNull(accountingExportPort);
    }

    public RunView createRun(UUID tenantId, UUID actorUserId, CreateRun command) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(command);
        if (command.periodEnd().isBefore(command.periodStart())) {
            throw new IllegalArgumentException("HRM_VALIDATION_FAILED: periodEnd must not precede periodStart");
        }
        if (!command.currencyCode().matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("HRM_VALIDATION_FAILED: currencyCode must be ISO-4217 alpha-3");
        }
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setTenant(c, tenantId);
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO hr_payroll_runs
                      (id, tenant_id, legal_entity_id, period_start, period_end,
                       currency_code, status, source_cutoff_at, version, created_by,
                       created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, 0, ?, NOW(), NOW())
                    """)) {
                    ps.setObject(1,id); ps.setObject(2,tenantId); ps.setObject(3,command.legalEntityId());
                    ps.setObject(4,command.periodStart()); ps.setObject(5,command.periodEnd());
                    ps.setString(6,command.currencyCode()); ps.setTimestamp(7,Timestamp.from(command.sourceCutoffAt()));
                    ps.setObject(8,actorUserId); ps.executeUpdate();
                }
                c.commit();
                return getRun(tenantId,id);
            } catch (Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_PAYROLL_RUN_CREATE_FAILED: " + e.getMessage(), e);
        }
    }

    public List<RunView> listRuns(UUID tenantId) {
        try (Connection c=dataSource.getConnection()) {
            setTenant(c,tenantId);
            try (PreparedStatement ps=c.prepareStatement("""
                SELECT id, legal_entity_id, period_start, period_end, currency_code,
                       status, source_cutoff_at, version
                  FROM hr_payroll_runs WHERE tenant_id=?
                 ORDER BY period_end DESC, id
                """)) {
                ps.setObject(1,tenantId);
                try(ResultSet rs=ps.executeQuery()){
                    List<RunView> out=new ArrayList<>();
                    while(rs.next()) out.add(run(rs));
                    return List.copyOf(out);
                }
            }
        } catch(SQLException e){ throw new IllegalStateException("HRM_PAYROLL_READ_FAILED: "+e.getMessage(),e); }
    }

    public RunView getRun(UUID tenantId, UUID runId) {
        try(Connection c=dataSource.getConnection()){
            setTenant(c,tenantId);
            try(PreparedStatement ps=c.prepareStatement("""
                SELECT id, legal_entity_id, period_start, period_end, currency_code,
                       status, source_cutoff_at, version
                  FROM hr_payroll_runs WHERE tenant_id=? AND id=?
                """)){
                ps.setObject(1,tenantId); ps.setObject(2,runId);
                try(ResultSet rs=ps.executeQuery()){
                    if(!rs.next()) throw new IllegalStateException("HRM_PAYROLL_RUN_NOT_FOUND: "+runId);
                    return run(rs);
                }
            }
        }catch(SQLException e){throw new IllegalStateException("HRM_PAYROLL_READ_FAILED: "+e.getMessage(),e);}
    }

    public List<ItemView> listItems(UUID tenantId, UUID runId) {
        getRun(tenantId,runId);
        try(Connection c=dataSource.getConnection()){
            setTenant(c,tenantId);
            try(PreparedStatement ps=c.prepareStatement("""
                SELECT id, employment_id, compensation_package_id, timesheet_id,
                       base_amount, gross_amount, deduction_total, net_amount,
                       status, exception_code, version
                  FROM hr_payroll_items
                 WHERE tenant_id=? AND payroll_run_id=? ORDER BY employment_id,id
                """)){
                ps.setObject(1,tenantId); ps.setObject(2,runId);
                try(ResultSet rs=ps.executeQuery()){
                    List<ItemView> out=new ArrayList<>();
                    while(rs.next()) out.add(item(rs));
                    return List.copyOf(out);
                }
            }
        }catch(SQLException e){throw new IllegalStateException("HRM_PAYROLL_READ_FAILED: "+e.getMessage(),e);}
    }

    public ItemView getItem(UUID tenantId, UUID runId, UUID itemId) {
        getRun(tenantId,runId);
        try(Connection c=dataSource.getConnection()){
            setTenant(c,tenantId);
            try(PreparedStatement ps=c.prepareStatement("""
                SELECT id, employment_id, compensation_package_id, timesheet_id,
                       base_amount, gross_amount, deduction_total, net_amount,
                       status, exception_code, version
                  FROM hr_payroll_items
                 WHERE tenant_id=? AND payroll_run_id=? AND id=?
                """)){
                ps.setObject(1,tenantId); ps.setObject(2,runId); ps.setObject(3,itemId);
                try(ResultSet rs=ps.executeQuery()){
                    if(!rs.next()) throw new IllegalStateException("HRM_PAYROLL_ITEM_NOT_FOUND: "+itemId);
                    return item(rs);
                }
            }
        }catch(SQLException e){throw new IllegalStateException("HRM_PAYROLL_READ_FAILED: "+e.getMessage(),e);}
    }

    public PayrollLifecycleService.TransitionResult transition(
            HrAuthenticatedContext actor, UUID runId, PayrollLifecycle target,
            long expectedVersion, String reason, String idempotencyKey, String fingerprint) {
        return lifecycleService.transition(actor,runId,target,expectedVersion,reason,idempotencyKey,fingerprint);
    }

    public PayrollLifecycleService.TransitionResult recalculate(
            HrAuthenticatedContext actor, UUID runId, long expectedVersion,
            String reason, String idempotencyKey, String fingerprint) {
        return lifecycleService.recalculate(actor,runId,expectedVersion,reason,idempotencyKey,fingerprint);
    }

    public PayrollAccountingExportPort.ExportReceipt export(
            HrAuthenticatedContext actor, UUID runId, long expectedVersion,
            String idempotencyKey) {
        RunView run=getRun(actor.tenantId(),runId);
        if(run.status()!=PayrollLifecycle.APPROVED)
            throw new IllegalStateException("HRM_PAYROLL_LIFECYCLE_INVALID: export requires APPROVED");
        if(run.version()!=expectedVersion)
            throw new IllegalStateException("HRM_PAYROLL_VERSION_CONFLICT: expected="+expectedVersion+", actual="+run.version());

        PayrollAccountingExportPort port=accountingExportPort.getIfAvailable();
        if(port==null) throw new IllegalStateException(
                "HRM_PAYROLL_ACCOUNTING_EXPORT_UNAVAILABLE: Accounting adapter is not configured");

        return port.requestExport(new PayrollAccountingExportPort.ExportRequest(
                actor.tenantId(),runId,run.legalEntityId(),run.periodStart(),run.periodEnd(),
                run.currencyCode(),run.version(),
                Objects.requireNonNullElseGet(actor.correlationId(),UUID::randomUUID),
                Objects.requireNonNullElseGet(actor.requestId(),UUID::randomUUID),
                idempotencyKey));
    }

    private static RunView run(ResultSet rs)throws SQLException{
        return new RunView(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("legal_entity_id")),
                rs.getObject("period_start",LocalDate.class),rs.getObject("period_end",LocalDate.class),
                rs.getString("currency_code"),PayrollLifecycle.valueOf(rs.getString("status")),
                rs.getTimestamp("source_cutoff_at").toInstant(),rs.getLong("version"));
    }
    private static ItemView item(ResultSet rs)throws SQLException{
        return new ItemView(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("employment_id")),
                UUID.fromString(rs.getString("compensation_package_id")),UUID.fromString(rs.getString("timesheet_id")),
                rs.getBigDecimal("base_amount"),rs.getBigDecimal("gross_amount"),rs.getBigDecimal("deduction_total"),
                rs.getBigDecimal("net_amount"),rs.getString("status"),rs.getString("exception_code"),rs.getLong("version"));
    }
    private static void setTenant(Connection c,UUID tenantId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")){
            ps.setString(1,tenantId.toString()); ps.execute();
        }
    }

    public record CreateRun(UUID legalEntityId, LocalDate periodStart, LocalDate periodEnd,
                            String currencyCode, Instant sourceCutoffAt) {
        public CreateRun { Objects.requireNonNull(legalEntityId); Objects.requireNonNull(periodStart);
            Objects.requireNonNull(periodEnd); Objects.requireNonNull(currencyCode); Objects.requireNonNull(sourceCutoffAt); }
    }
    public record RunView(UUID id, UUID legalEntityId, LocalDate periodStart, LocalDate periodEnd,
                          String currencyCode, PayrollLifecycle status, Instant sourceCutoffAt, long version) {}
    public record ItemView(UUID id, UUID employmentId, UUID compensationPackageId, UUID timesheetId,
                           java.math.BigDecimal baseAmount, java.math.BigDecimal grossAmount,
                           java.math.BigDecimal deductionTotal, java.math.BigDecimal netAmount,
                           String status, String exceptionCode, long version) {}
}
