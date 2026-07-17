package com.sanad.platform.businessprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BusinessProcessService {

    public static final String SALES_ORDER_TO_CASH = "SALES-ORDER-TO-CASH";
    public static final String PROCURE_TO_PAY = "PROCUREMENT-PROCURE-TO-PAY";
    public static final String HIRE_TO_PAY = "HR-HIRE-TO-PAY";
    public static final String ORDER_TO_REFUND = "COMMERCE-ORDER-TO-REFUND";

    private static final Set<String> SUPPORTED = Set.of(
            SALES_ORDER_TO_CASH, PROCURE_TO_PAY, HIRE_TO_PAY, ORDER_TO_REFUND);

    private final JdbcTemplate jdbc;
    private final PlatformAuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public BusinessProcessService(JdbcTemplate jdbc, PlatformAuditWriter auditWriter, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExecutionResult execute(UUID tenantId, UUID actorId, String requestedProcessCode, ExecuteCommand command) {
        String processCode = normalizeProcessCode(requestedProcessCode);
        ExecuteCommand normalized = normalize(command, processCode);

        List<UUID> existing = jdbc.query(
                "SELECT id FROM bp_process_runs WHERE tenant_id=? AND process_code=? AND external_reference=?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId, processCode, normalized.externalReference());
        if (!existing.isEmpty()) {
            return load(tenantId, existing.get(0), true);
        }

        UUID runId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        BigDecimal settlement = money(normalized.grossAmount().add(normalized.taxAmount()));
        BigDecimal startingInventory = null;

        if (requiresInventory(processCode)) {
            BigDecimal seed = SALES_ORDER_TO_CASH.equals(processCode)
                    ? new BigDecimal("100")
                    : ORDER_TO_REFUND.equals(processCode)
                    ? new BigDecimal("50")
                    : BigDecimal.ZERO;
            ensureInventory(tenantId, normalized.sku(), seed);
            startingInventory = balance(tenantId, normalized.sku()).onHand();
        }

        jdbc.update("""
                INSERT INTO bp_process_runs
                (id,tenant_id,process_code,external_reference,status,currency_code,gross_amount,tax_amount,
                 settlement_amount,quantity,sku,starting_inventory,created_by,created_at)
                VALUES (?,?,?,?, 'RUNNING', ?,?,?,?,?,?,?,?,?)
                """,
                runId, tenantId, processCode, normalized.externalReference(), normalized.currencyCode(),
                normalized.grossAmount(), normalized.taxAmount(), settlement, normalized.quantity(),
                normalized.sku(), startingInventory, actorId, Timestamp.from(startedAt));

        switch (processCode) {
            case SALES_ORDER_TO_CASH -> executeSalesOrderToCash(tenantId, actorId, runId, normalized, settlement);
            case PROCURE_TO_PAY -> executeProcureToPay(tenantId, actorId, runId, normalized, settlement);
            case HIRE_TO_PAY -> executeHireToPay(tenantId, actorId, runId, normalized, settlement);
            case ORDER_TO_REFUND -> executeOrderToRefund(tenantId, actorId, runId, normalized, settlement);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported process code");
        }

        BigDecimal debitTotal = sum("SELECT COALESCE(SUM(debit_amount),0) FROM bp_ledger_entries WHERE tenant_id=? AND run_id=?",
                tenantId, runId);
        BigDecimal creditTotal = sum("SELECT COALESCE(SUM(credit_amount),0) FROM bp_ledger_entries WHERE tenant_id=? AND run_id=?",
                tenantId, runId);
        BigDecimal paymentNet = sum("""
                SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0)
                FROM bp_payment_events WHERE tenant_id=? AND run_id=?
                """, tenantId, runId);

        BigDecimal endingInventory = requiresInventory(processCode)
                ? balance(tenantId, normalized.sku()).onHand()
                : null;

        boolean financialReconciled = debitTotal.compareTo(creditTotal) == 0
                && expectedPaymentNet(processCode, settlement).compareTo(paymentNet) == 0;
        boolean inventoryReconciled = inventoryReconciled(
                processCode, startingInventory, endingInventory, normalized.quantity());

        if (!financialReconciled) {
            throw new IllegalStateException("Financial reconciliation failed for " + processCode);
        }
        if (!inventoryReconciled) {
            throw new IllegalStateException("Inventory reconciliation failed for " + processCode);
        }

        writeAnalytics(tenantId, runId, debitTotal, creditTotal, paymentNet,
                startingInventory, endingInventory);
        boolean analyticsConsistent = analyticsConsistent(tenantId, runId, debitTotal, creditTotal, paymentNet);
        if (!analyticsConsistent) {
            throw new IllegalStateException("Analytics reconciliation failed for " + processCode);
        }

        int analyticsSequence = stepCount(tenantId, runId) + 1;
        recordStep(tenantId, actorId, runId, analyticsSequence, "Analytics", "ANALYTICS",
                "RECONCILED", null, null,
                Map.of("financialReconciled", true, "inventoryReconciled", true,
                        "analyticsConsistent", true));
        failIfRequested(normalized.failAtStep(), "Analytics");

        Instant completedAt = Instant.now();
        jdbc.update("""
                UPDATE bp_process_runs
                SET status='COMPLETED', ending_inventory=?, debit_total=?, credit_total=?, payment_net=?,
                    financial_reconciled=?, inventory_reconciled=?, analytics_consistent=?, completed_at=?
                WHERE tenant_id=? AND id=?
                """,
                endingInventory, debitTotal, creditTotal, paymentNet,
                true, true, true, Timestamp.from(completedAt), tenantId, runId);

        ObjectNode after = objectMapper.createObjectNode();
        after.put("processCode", processCode);
        after.put("externalReference", normalized.externalReference());
        after.put("status", "COMPLETED");
        after.put("debitTotal", debitTotal);
        after.put("creditTotal", creditTotal);
        after.put("paymentNet", paymentNet);
        after.put("financialReconciled", true);
        after.put("inventoryReconciled", true);
        after.put("analyticsConsistent", true);
        auditWriter.writeSuccess(tenantId, actorId, tenantId,
                "EXECUTE", "BUSINESS_PROCESS", runId.toString(),
                "REM-P1-007 governed business process execution", null, after,
                correlationId(runId), completedAt);

        return load(tenantId, runId, false);
    }

    @Transactional(readOnly = true)
    public ExecutionResult get(UUID tenantId, UUID runId) {
        return load(tenantId, runId, false);
    }

    private void executeSalesOrderToCash(UUID tenantId, UUID actorId, UUID runId,
                                         ExecuteCommand command, BigDecimal settlement) {
        int sequence = 1;
        sequence = complete(tenantId, actorId, runId, sequence, "Lead", "CRM", command, null, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Qualification", "CRM", command, null, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Account and Contact", "CRM", command, null, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Opportunity", "SALES", command,
                command.grossAmount(), null);
        sequence = complete(tenantId, actorId, runId, sequence, "Quotation", "SALES", command,
                settlement, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Sales Order", "SALES", command,
                settlement, null);

        reserve(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Inventory Reservation", "INVENTORY", command,
                null, command.quantity().negate());

        ship(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Delivery", "INVENTORY", command,
                null, command.quantity().negate());

        addInvoiceJournal(tenantId, runId, "SALES_INVOICE", "ACCOUNTS_RECEIVABLE",
                "SALES_REVENUE", "OUTPUT_TAX", command.grossAmount(), command.taxAmount());
        sequence = complete(tenantId, actorId, runId, sequence, "Invoice", "ACCOUNTING", command,
                settlement, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Ledger Posting", "ACCOUNTING", command,
                settlement, null);

        addPayment(tenantId, runId, "CUSTOMER_COLLECTION", "IN", settlement, "SETTLED");
        addJournal(tenantId, runId, "CUSTOMER_COLLECTION", List.of(
                new Entry("CASH", settlement, BigDecimal.ZERO),
                new Entry("ACCOUNTS_RECEIVABLE", BigDecimal.ZERO, settlement)));
        complete(tenantId, actorId, runId, sequence, "Collection", "PAYMENTS", command,
                settlement, null);
    }

    private void executeProcureToPay(UUID tenantId, UUID actorId, UUID runId,
                                     ExecuteCommand command, BigDecimal settlement) {
        int sequence = 1;
        sequence = complete(tenantId, actorId, runId, sequence, "Purchase Request", "PURCHASING", command,
                settlement, null);
        approve(tenantId, actorId, runId, "PURCHASE_REQUEST_APPROVAL");
        sequence = completeApproved(tenantId, actorId, runId, sequence, "Approval", "WORKFLOW", command,
                settlement);
        sequence = complete(tenantId, actorId, runId, sequence, "Purchase Order", "PURCHASING", command,
                settlement, null);

        receive(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Goods Receipt", "INVENTORY", command,
                settlement, command.quantity());

        addJournal(tenantId, runId, "SUPPLIER_INVOICE", List.of(
                new Entry("INVENTORY_ASSET", settlement, BigDecimal.ZERO),
                new Entry("ACCOUNTS_PAYABLE", BigDecimal.ZERO, settlement)));
        sequence = complete(tenantId, actorId, runId, sequence, "Supplier Invoice", "ACCOUNTING", command,
                settlement, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Ledger Posting", "ACCOUNTING", command,
                settlement, null);

        addPayment(tenantId, runId, "SUPPLIER_PAYMENT", "OUT", settlement, "SETTLED");
        addJournal(tenantId, runId, "SUPPLIER_PAYMENT", List.of(
                new Entry("ACCOUNTS_PAYABLE", settlement, BigDecimal.ZERO),
                new Entry("CASH", BigDecimal.ZERO, settlement)));
        sequence = complete(tenantId, actorId, runId, sequence, "Payment", "PAYMENTS", command,
                settlement, null);
        complete(tenantId, actorId, runId, sequence, "Reconciliation", "ANALYTICS", command,
                settlement, null);
    }

    private void executeHireToPay(UUID tenantId, UUID actorId, UUID runId,
                                  ExecuteCommand command, BigDecimal settlement) {
        int sequence = 1;
        sequence = complete(tenantId, actorId, runId, sequence, "Employee", "HR", command, null, null);
        approve(tenantId, actorId, runId, "EMPLOYMENT_CONTRACT_APPROVAL");
        sequence = completeApproved(tenantId, actorId, runId, sequence, "Contract", "WORKFLOW", command,
                settlement);
        sequence = complete(tenantId, actorId, runId, sequence, "Attendance", "HR", command, null, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Leave", "HR", command, null, null);

        addJournal(tenantId, runId, "PAYROLL_ACCRUAL", List.of(
                new Entry("PAYROLL_EXPENSE", settlement, BigDecimal.ZERO),
                new Entry("PAYROLL_PAYABLE", BigDecimal.ZERO, settlement)));
        sequence = complete(tenantId, actorId, runId, sequence, "Payroll", "PAYROLL", command,
                settlement, null);
        sequence = complete(tenantId, actorId, runId, sequence, "Ledger Posting", "ACCOUNTING", command,
                settlement, null);

        addPayment(tenantId, runId, "PAYROLL_PAYMENT", "OUT", settlement, "SETTLED");
        addJournal(tenantId, runId, "PAYROLL_PAYMENT", List.of(
                new Entry("PAYROLL_PAYABLE", settlement, BigDecimal.ZERO),
                new Entry("CASH", BigDecimal.ZERO, settlement)));
        complete(tenantId, actorId, runId, sequence, "Payment", "PAYMENTS", command,
                settlement, null);
    }

    private void executeOrderToRefund(UUID tenantId, UUID actorId, UUID runId,
                                      ExecuteCommand command, BigDecimal settlement) {
        int sequence = 1;
        sequence = complete(tenantId, actorId, runId, sequence, "Customer Order", "ECOMMERCE", command,
                settlement, null);

        addPayment(tenantId, runId, "PAYMENT_AUTHORIZATION", "IN", settlement, "AUTHORIZED");
        sequence = complete(tenantId, actorId, runId, sequence, "Payment Authorization", "PAYMENTS", command,
                settlement, null);

        reserve(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Inventory Reservation", "INVENTORY", command,
                null, command.quantity().negate());

        ship(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Shipment", "INVENTORY", command,
                null, command.quantity().negate());

        addInvoiceJournal(tenantId, runId, "COMMERCE_INVOICE", "CASH",
                "COMMERCE_REVENUE", "OUTPUT_TAX", command.grossAmount(), command.taxAmount());
        sequence = complete(tenantId, actorId, runId, sequence, "Invoice", "ACCOUNTING", command,
                settlement, null);

        returnInventory(tenantId, runId, command.sku(), command.quantity());
        sequence = complete(tenantId, actorId, runId, sequence, "Return", "RETURNS", command,
                command.grossAmount(), command.quantity());

        addPayment(tenantId, runId, "CUSTOMER_REFUND", "OUT", settlement, "REFUNDED");
        List<Entry> refund = new ArrayList<>();
        refund.add(new Entry("SALES_RETURNS", command.grossAmount(), BigDecimal.ZERO));
        if (command.taxAmount().signum() > 0) {
            refund.add(new Entry("OUTPUT_TAX", command.taxAmount(), BigDecimal.ZERO));
        }
        refund.add(new Entry("CASH", BigDecimal.ZERO, settlement));
        addJournal(tenantId, runId, "CUSTOMER_REFUND", refund);
        sequence = complete(tenantId, actorId, runId, sequence, "Refund", "PAYMENTS", command,
                settlement, null);
        complete(tenantId, actorId, runId, sequence, "Ledger Reconciliation", "ACCOUNTING", command,
                settlement, null);
    }

    private int complete(UUID tenantId, UUID actorId, UUID runId, int sequence,
                         String step, String domain, ExecuteCommand command,
                         BigDecimal amount, BigDecimal quantityDelta) {
        recordStep(tenantId, actorId, runId, sequence, step, domain, "COMPLETED", amount, quantityDelta,
                Map.of("externalReference", command.externalReference(), "processEvidence", true));
        failIfRequested(command.failAtStep(), step);
        return sequence + 1;
    }

    private int completeApproved(UUID tenantId, UUID actorId, UUID runId, int sequence,
                                 String step, String domain, ExecuteCommand command, BigDecimal amount) {
        recordStep(tenantId, actorId, runId, sequence, step, domain, "APPROVED", amount, null,
                Map.of("externalReference", command.externalReference(), "approvedBy", actorId.toString()));
        failIfRequested(command.failAtStep(), step);
        return sequence + 1;
    }

    private void recordStep(UUID tenantId, UUID actorId, UUID runId, int sequence,
                            String step, String domain, String status, BigDecimal amount,
                            BigDecimal quantityDelta, Map<String, ?> evidence) {
        UUID stepId = UUID.randomUUID();
        Instant now = Instant.now();
        ObjectNode evidenceNode = objectMapper.valueToTree(evidence);
        evidenceNode.put("step", step);
        evidenceNode.put("domain", domain);
        evidenceNode.put("sequence", sequence);
        jdbc.update("""
                INSERT INTO bp_process_steps
                (id,tenant_id,run_id,sequence_no,step_code,domain_code,status,amount,quantity_delta,
                 evidence_json,created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                stepId, tenantId, runId, sequence, step, domain, status, amount, quantityDelta,
                evidenceNode.toString(), actorId, Timestamp.from(now));
        auditWriter.writeSuccess(tenantId, actorId, tenantId,
                "EXECUTE_STEP", "BUSINESS_PROCESS_STEP", stepId.toString(), null,
                null, evidenceNode, correlationId(runId), now);
    }

    private void addInvoiceJournal(UUID tenantId, UUID runId, String group,
                                   String debitAccount, String revenueAccount, String taxAccount,
                                   BigDecimal grossAmount, BigDecimal taxAmount) {
        BigDecimal settlement = money(grossAmount.add(taxAmount));
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(debitAccount, settlement, BigDecimal.ZERO));
        entries.add(new Entry(revenueAccount, BigDecimal.ZERO, grossAmount));
        if (taxAmount.signum() > 0) {
            entries.add(new Entry(taxAccount, BigDecimal.ZERO, taxAmount));
        }
        addJournal(tenantId, runId, group, entries);
    }

    private void addJournal(UUID tenantId, UUID runId, String group, List<Entry> entries) {
        BigDecimal debits = entries.stream().map(Entry::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream().map(Entry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (money(debits).compareTo(money(credits)) != 0) {
            throw new IllegalArgumentException("Unbalanced journal group " + group);
        }
        Instant now = Instant.now();
        for (Entry entry : entries) {
            jdbc.update("""
                    INSERT INTO bp_ledger_entries
                    (id,tenant_id,run_id,account_code,debit_amount,credit_amount,entry_group,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(), tenantId, runId, entry.accountCode(), money(entry.debit()),
                    money(entry.credit()), group, Timestamp.from(now));
        }
    }

    private void addPayment(UUID tenantId, UUID runId, String type, String direction,
                            BigDecimal amount, String status) {
        jdbc.update("""
                INSERT INTO bp_payment_events
                (id,tenant_id,run_id,payment_type,direction,amount,status,created_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), tenantId, runId, type, direction, money(amount), status,
                Timestamp.from(Instant.now()));
    }

    private void approve(UUID tenantId, UUID actorId, UUID runId, String approvalCode) {
        jdbc.update("""
                INSERT INTO bp_workflow_approvals
                (id,tenant_id,run_id,approval_code,status,approved_by,approved_at)
                VALUES (?,?,?,?, 'APPROVED', ?,?)
                """,
                UUID.randomUUID(), tenantId, runId, approvalCode, actorId,
                Timestamp.from(Instant.now()));
    }

    private void ensureInventory(UUID tenantId, String sku, BigDecimal initialOnHand) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bp_inventory_balances WHERE tenant_id=? AND sku=?",
                Integer.class, tenantId, sku);
        if (count != null && count == 0) {
            jdbc.update("""
                    INSERT INTO bp_inventory_balances (tenant_id,sku,on_hand,reserved,updated_at)
                    VALUES (?,?,?,0,?)
                    """, tenantId, sku, initialOnHand, Timestamp.from(Instant.now()));
        }
    }

    private void reserve(UUID tenantId, UUID runId, String sku, BigDecimal quantity) {
        int changed = jdbc.update("""
                UPDATE bp_inventory_balances
                SET reserved=reserved+?, updated_at=?
                WHERE tenant_id=? AND sku=? AND (on_hand-reserved)>=?
                """, quantity, Timestamp.from(Instant.now()), tenantId, sku, quantity);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available inventory");
        }
        recordMovement(tenantId, runId, sku, "RESERVE", quantity);
    }

    private void ship(UUID tenantId, UUID runId, String sku, BigDecimal quantity) {
        int changed = jdbc.update("""
                UPDATE bp_inventory_balances
                SET on_hand=on_hand-?, reserved=reserved-?, updated_at=?
                WHERE tenant_id=? AND sku=? AND reserved>=? AND on_hand>=?
                """, quantity, quantity, Timestamp.from(Instant.now()), tenantId, sku, quantity, quantity);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved inventory cannot be shipped");
        }
        recordMovement(tenantId, runId, sku, "SHIP", quantity);
    }

    private void receive(UUID tenantId, UUID runId, String sku, BigDecimal quantity) {
        jdbc.update("""
                UPDATE bp_inventory_balances SET on_hand=on_hand+?, updated_at=?
                WHERE tenant_id=? AND sku=?
                """, quantity, Timestamp.from(Instant.now()), tenantId, sku);
        recordMovement(tenantId, runId, sku, "RECEIVE", quantity);
    }

    private void returnInventory(UUID tenantId, UUID runId, String sku, BigDecimal quantity) {
        jdbc.update("""
                UPDATE bp_inventory_balances SET on_hand=on_hand+?, updated_at=?
                WHERE tenant_id=? AND sku=?
                """, quantity, Timestamp.from(Instant.now()), tenantId, sku);
        recordMovement(tenantId, runId, sku, "RETURN", quantity);
    }

    private void recordMovement(UUID tenantId, UUID runId, String sku, String type, BigDecimal quantity) {
        InventoryBalance balance = balance(tenantId, sku);
        jdbc.update("""
                INSERT INTO bp_inventory_movements
                (id,tenant_id,run_id,sku,movement_type,quantity,on_hand_after,reserved_after,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), tenantId, runId, sku, type, quantity,
                balance.onHand(), balance.reserved(), Timestamp.from(Instant.now()));
    }

    private InventoryBalance balance(UUID tenantId, String sku) {
        List<InventoryBalance> rows = jdbc.query(
                "SELECT on_hand,reserved FROM bp_inventory_balances WHERE tenant_id=? AND sku=?",
                (rs, rowNum) -> new InventoryBalance(rs.getBigDecimal("on_hand"), rs.getBigDecimal("reserved")),
                tenantId, sku);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory balance not found");
        }
        return rows.get(0);
    }

    private void writeAnalytics(UUID tenantId, UUID runId, BigDecimal debitTotal,
                                BigDecimal creditTotal, BigDecimal paymentNet,
                                BigDecimal startingInventory, BigDecimal endingInventory) {
        insertMetric(tenantId, runId, "PROCESS_COMPLETED", BigDecimal.ONE);
        insertMetric(tenantId, runId, "DEBIT_TOTAL", debitTotal);
        insertMetric(tenantId, runId, "CREDIT_TOTAL", creditTotal);
        insertMetric(tenantId, runId, "PAYMENT_NET", paymentNet);
        BigDecimal delta = startingInventory == null || endingInventory == null
                ? BigDecimal.ZERO : endingInventory.subtract(startingInventory);
        insertMetric(tenantId, runId, "INVENTORY_DELTA", delta);
    }

    private void insertMetric(UUID tenantId, UUID runId, String metric, BigDecimal value) {
        jdbc.update("""
                INSERT INTO bp_analytics_snapshots
                (id,tenant_id,run_id,metric_code,metric_value,created_at)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), tenantId, runId, metric, value,
                Timestamp.from(Instant.now()));
    }

    private boolean analyticsConsistent(UUID tenantId, UUID runId, BigDecimal debitTotal,
                                        BigDecimal creditTotal, BigDecimal paymentNet) {
        return metric(tenantId, runId, "PROCESS_COMPLETED").compareTo(BigDecimal.ONE) == 0
                && metric(tenantId, runId, "DEBIT_TOTAL").compareTo(debitTotal) == 0
                && metric(tenantId, runId, "CREDIT_TOTAL").compareTo(creditTotal) == 0
                && metric(tenantId, runId, "PAYMENT_NET").compareTo(paymentNet) == 0;
    }

    private BigDecimal metric(UUID tenantId, UUID runId, String code) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT metric_value FROM bp_analytics_snapshots
                WHERE tenant_id=? AND run_id=? AND metric_code=?
                """, BigDecimal.class, tenantId, runId, code);
        return value == null ? BigDecimal.ZERO : value;
    }

    private ExecutionResult load(UUID tenantId, UUID runId, boolean idempotent) {
        List<RunRow> rows = jdbc.query("""
                SELECT * FROM bp_process_runs WHERE tenant_id=? AND id=?
                """, (rs, rowNum) -> new RunRow(
                rs.getObject("id", UUID.class),
                rs.getString("process_code"),
                rs.getString("external_reference"),
                rs.getString("status"),
                rs.getString("currency_code"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("settlement_amount"),
                rs.getBigDecimal("quantity"),
                rs.getString("sku"),
                rs.getBigDecimal("starting_inventory"),
                rs.getBigDecimal("ending_inventory"),
                rs.getBigDecimal("debit_total"),
                rs.getBigDecimal("credit_total"),
                rs.getBigDecimal("payment_net"),
                rs.getBoolean("financial_reconciled"),
                rs.getBoolean("inventory_reconciled"),
                rs.getBoolean("analytics_consistent"),
                toInstant(rs.getObject("created_at")),
                toInstant(rs.getObject("completed_at"))),
                tenantId, runId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Business process run not found");
        }
        RunRow row = rows.get(0);
        List<String> steps = jdbc.query("""
                SELECT step_code FROM bp_process_steps
                WHERE tenant_id=? AND run_id=? ORDER BY sequence_no
                """, (rs, rowNum) -> rs.getString("step_code"), tenantId, runId);
        int auditCount = count("""
                SELECT COUNT(*) FROM platform_audit_logs
                WHERE target_tenant_id=? AND correlation_id=?
                """, tenantId, correlationId(runId));
        int workflowApprovals = count("SELECT COUNT(*) FROM bp_workflow_approvals WHERE tenant_id=? AND run_id=?",
                tenantId, runId);
        int ledgerEntries = count("SELECT COUNT(*) FROM bp_ledger_entries WHERE tenant_id=? AND run_id=?",
                tenantId, runId);
        int paymentEvents = count("SELECT COUNT(*) FROM bp_payment_events WHERE tenant_id=? AND run_id=?",
                tenantId, runId);
        int inventoryMovements = count("SELECT COUNT(*) FROM bp_inventory_movements WHERE tenant_id=? AND run_id=?",
                tenantId, runId);

        return new ExecutionResult(row.id(), row.processCode(), row.externalReference(), row.status(),
                row.currencyCode(), row.grossAmount(), row.taxAmount(), row.settlementAmount(),
                row.quantity(), row.sku(), row.startingInventory(), row.endingInventory(),
                row.debitTotal(), row.creditTotal(), row.paymentNet(), row.financialReconciled(),
                row.inventoryReconciled(), row.analyticsConsistent(), idempotent, steps, List.of(),
                auditCount, workflowApprovals, ledgerEntries, paymentEvents, inventoryMovements,
                row.createdAt(), row.completedAt());
    }

    private ExecuteCommand normalize(ExecuteCommand command, String processCode) {
        if (command == null || command.externalReference() == null || command.externalReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalReference is required");
        }
        BigDecimal gross = command.grossAmount() == null ? new BigDecimal("1000") : money(command.grossAmount());
        BigDecimal tax = command.taxAmount() == null ? BigDecimal.ZERO : money(command.taxAmount());
        BigDecimal quantity = command.quantity() == null ? BigDecimal.ONE : command.quantity();
        if (gross.signum() <= 0 || tax.signum() < 0 || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amounts and quantity are invalid");
        }
        String currency = command.currencyCode() == null ? "SAR" : command.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode must contain three characters");
        }
        String sku = command.sku();
        if (requiresInventory(processCode) && (sku == null || sku.isBlank())) {
            sku = switch (processCode) {
                case SALES_ORDER_TO_CASH -> "SALES-E2E-SKU";
                case PROCURE_TO_PAY -> "PROCUREMENT-E2E-SKU";
                case ORDER_TO_REFUND -> "COMMERCE-E2E-SKU";
                default -> null;
            };
        }
        return new ExecuteCommand(command.externalReference().trim(), gross, tax, quantity,
                currency, sku == null ? null : sku.trim(), command.failAtStep());
    }

    private String normalizeProcessCode(String requested) {
        String processCode = requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(processCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported business process code");
        }
        return processCode;
    }

    private boolean requiresInventory(String processCode) {
        return SALES_ORDER_TO_CASH.equals(processCode)
                || PROCURE_TO_PAY.equals(processCode)
                || ORDER_TO_REFUND.equals(processCode);
    }

    private boolean inventoryReconciled(String processCode, BigDecimal starting,
                                        BigDecimal ending, BigDecimal quantity) {
        if (HIRE_TO_PAY.equals(processCode)) {
            return starting == null && ending == null;
        }
        if (starting == null || ending == null) {
            return false;
        }
        BigDecimal expected = switch (processCode) {
            case SALES_ORDER_TO_CASH -> starting.subtract(quantity);
            case PROCURE_TO_PAY -> starting.add(quantity);
            case ORDER_TO_REFUND -> starting;
            default -> starting;
        };
        return expected.compareTo(ending) == 0;
    }

    private BigDecimal expectedPaymentNet(String processCode, BigDecimal settlement) {
        return switch (processCode) {
            case SALES_ORDER_TO_CASH -> settlement;
            case PROCURE_TO_PAY, HIRE_TO_PAY -> settlement.negate();
            case ORDER_TO_REFUND -> BigDecimal.ZERO;
            default -> throw new IllegalArgumentException("Unsupported process code");
        };
    }

    private void failIfRequested(String requested, String currentStep) {
        if (requested != null && requested.trim().equalsIgnoreCase(currentStep)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Controlled failure at step " + currentStep);
        }
    }

    private int stepCount(UUID tenantId, UUID runId) {
        return count("SELECT COUNT(*) FROM bp_process_steps WHERE tenant_id=? AND run_id=?", tenantId, runId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String correlationId(UUID runId) {
        return "bp-e2e-" + runId;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.ZonedDateTime zonedDateTime) return zonedDateTime.toInstant();
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unsupported timestamp type: " + value.getClass().getName());
    }

    public record ExecuteCommand(
            String externalReference,
            BigDecimal grossAmount,
            BigDecimal taxAmount,
            BigDecimal quantity,
            String currencyCode,
            String sku,
            String failAtStep
    ) {}

    public record ExecutionResult(
            UUID runId,
            String processCode,
            String externalReference,
            String status,
            String currencyCode,
            BigDecimal grossAmount,
            BigDecimal taxAmount,
            BigDecimal settlementAmount,
            BigDecimal quantity,
            String sku,
            BigDecimal startingInventory,
            BigDecimal endingInventory,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal paymentNet,
            boolean financialReconciled,
            boolean inventoryReconciled,
            boolean analyticsConsistent,
            boolean idempotent,
            List<String> verifiedSteps,
            List<String> blockedSteps,
            int auditCount,
            int workflowApprovalCount,
            int ledgerEntryCount,
            int paymentEventCount,
            int inventoryMovementCount,
            Instant createdAt,
            Instant completedAt
    ) {}

    private record Entry(String accountCode, BigDecimal debit, BigDecimal credit) {}
    private record InventoryBalance(BigDecimal onHand, BigDecimal reserved) {}
    private record RunRow(
            UUID id, String processCode, String externalReference, String status,
            String currencyCode, BigDecimal grossAmount, BigDecimal taxAmount,
            BigDecimal settlementAmount, BigDecimal quantity, String sku,
            BigDecimal startingInventory, BigDecimal endingInventory,
            BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal paymentNet,
            boolean financialReconciled, boolean inventoryReconciled,
            boolean analyticsConsistent, Instant createdAt, Instant completedAt
    ) {}
}
