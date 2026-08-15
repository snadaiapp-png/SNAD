package com.sanad.platform.finance.api;

import com.sanad.platform.finance.application.FinanceAccountService;
import com.sanad.platform.finance.application.FinanceInvoiceService;
import com.sanad.platform.finance.application.FinancePaymentService;
import com.sanad.platform.finance.domain.*;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.security.SecurityContextUtils.tenantId;
import static com.sanad.platform.security.SecurityContextUtils.userId;

/**
 * Finance Module REST API — accounts, invoices, payments.
 *
 * <p>All endpoints are tenant-scoped and require {@link RequireCapability FINANCE.*} capabilities.
 *
 * <p>Base path: {@code /api/v1/finance}
 */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final FinanceAccountService accountService;
    private final FinanceInvoiceService invoiceService;
    private final FinancePaymentService paymentService;

    public FinanceController(FinanceAccountService accountService,
                             FinanceInvoiceService invoiceService,
                             FinancePaymentService paymentService) {
        this.accountService = accountService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    // ===== Accounts =====

    @PostMapping("/accounts")
    @RequireCapability("FINANCE.WRITE")
    public ResponseEntity<Map<String, Object>> createAccount(
            Authentication auth, @RequestBody CreateAccountRequest req) {
        var account = FinanceAccount.create(
                tenantId(auth), req.code(), req.name(),
                FinanceAccount.AccountType.valueOf(req.accountType()),
                req.parentAccountId(), req.currency(), req.description()
        );
        var saved = accountService.create(account);
        return ResponseEntity.ok(toAccountMap(saved));
    }

    @GetMapping("/accounts")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listAccounts(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var accounts = accountService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(accounts.stream().map(this::toAccountMap).toList());
    }

    @GetMapping("/accounts/{id}")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<Map<String, Object>> getAccount(
            Authentication auth, @PathVariable UUID id) {
        return accountService.findById(tenantId(auth), id)
                .map(a -> ResponseEntity.ok(toAccountMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/accounts/{id}/deactivate")
    @RequireCapability("FINANCE.ADMIN")
    public ResponseEntity<Map<String, Object>> deactivateAccount(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAccountMap(
                accountService.deactivate(tenantId(auth), id)));
    }

    @PostMapping("/accounts/{id}/archive")
    @RequireCapability("FINANCE.ADMIN")
    public ResponseEntity<Map<String, Object>> archiveAccount(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toAccountMap(
                accountService.archive(tenantId(auth), id)));
    }

    // ===== Invoices =====

    @PostMapping("/invoices")
    @RequireCapability("FINANCE.WRITE")
    public ResponseEntity<Map<String, Object>> createInvoice(
            Authentication auth, @RequestBody CreateInvoiceRequest req) {
        var invoice = FinanceInvoice.create(
                tenantId(auth), req.invoiceNumber(), req.customerType(),
                req.customerId(), req.customerName(),
                req.issueDate() != null ? req.issueDate() : LocalDate.now(),
                req.dueDate(), req.currency(), req.notes()
        );
        var saved = invoiceService.create(invoice);
        return ResponseEntity.ok(toInvoiceMap(saved));
    }

    @GetMapping("/invoices")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listInvoices(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var invoices = invoiceService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(invoices.stream().map(this::toInvoiceMap).toList());
    }

    @GetMapping("/invoices/{id}")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<Map<String, Object>> getInvoice(
            Authentication auth, @PathVariable UUID id) {
        return invoiceService.findById(tenantId(auth), id)
                .map(i -> ResponseEntity.ok(toInvoiceMap(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/invoices/{id}/issue")
    @RequireCapability("FINANCE.WRITE")
    public ResponseEntity<Map<String, Object>> issueInvoice(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInvoiceMap(
                invoiceService.issue(tenantId(auth), id)));
    }

    @PostMapping("/invoices/{id}/cancel")
    @RequireCapability("FINANCE.ADMIN")
    public ResponseEntity<Map<String, Object>> cancelInvoice(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInvoiceMap(
                invoiceService.cancel(tenantId(auth), id)));
    }

    @PostMapping("/invoices/{id}/mark-paid")
    @RequireCapability("FINANCE.APPROVE")
    public ResponseEntity<Map<String, Object>> markInvoicePaid(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toInvoiceMap(
                invoiceService.markPaid(tenantId(auth), id)));
    }

    // ===== Payments =====

    @PostMapping("/payments")
    @RequireCapability("FINANCE.WRITE")
    public ResponseEntity<Map<String, Object>> createPayment(
            Authentication auth, @RequestBody CreatePaymentRequest req) {
        var payment = FinancePayment.create(
                tenantId(auth), req.paymentNumber(),
                req.paymentDate() != null ? req.paymentDate() : LocalDate.now(),
                FinancePayment.PaymentMethod.valueOf(req.paymentMethod()),
                req.amount(), req.currency(), req.invoiceId(), req.notes()
        );
        var saved = paymentService.create(payment);
        return ResponseEntity.ok(toPaymentMap(saved));
    }

    @GetMapping("/payments")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<List<Map<String, Object>>> listPayments(
            Authentication auth, @RequestParam(defaultValue = "50") int limit) {
        var payments = paymentService.findByTenant(tenantId(auth), limit);
        return ResponseEntity.ok(payments.stream().map(this::toPaymentMap).toList());
    }

    @GetMapping("/payments/{id}")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<Map<String, Object>> getPayment(
            Authentication auth, @PathVariable UUID id) {
        return paymentService.findById(tenantId(auth), id)
                .map(p -> ResponseEntity.ok(toPaymentMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/payments/{id}/complete")
    @RequireCapability("FINANCE.APPROVE")
    public ResponseEntity<Map<String, Object>> completePayment(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toPaymentMap(
                paymentService.complete(tenantId(auth), id)));
    }

    @PostMapping("/payments/{id}/fail")
    @RequireCapability("FINANCE.ADMIN")
    public ResponseEntity<Map<String, Object>> failPayment(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toPaymentMap(
                paymentService.fail(tenantId(auth), id)));
    }

    @PostMapping("/payments/{id}/refund")
    @RequireCapability("FINANCE.ADMIN")
    public ResponseEntity<Map<String, Object>> refundPayment(
            Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(toPaymentMap(
                paymentService.refund(tenantId(auth), id)));
    }

    @GetMapping("/quota")
    @RequireCapability("FINANCE.VIEW")
    public ResponseEntity<Map<String, Object>> getQuota(Authentication auth) {
        var used = paymentService.countCompletedThisMonth(tenantId(auth));
        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId(auth),
                "completedPaymentsThisMonth", used
        ));
    }

    // ===== Request DTOs =====

    public record CreateAccountRequest(
            String code, String name, String accountType,
            UUID parentAccountId, String currency, String description
    ) {}

    public record CreateInvoiceRequest(
            String invoiceNumber, String customerType, UUID customerId,
            String customerName, LocalDate issueDate, LocalDate dueDate,
            String currency, String notes
    ) {}

    public record CreatePaymentRequest(
            String paymentNumber, LocalDate paymentDate, String paymentMethod,
            BigDecimal amount, String currency, UUID invoiceId, String notes
    ) {}

    // ===== Response helpers =====

    private Map<String, Object> toAccountMap(FinanceAccount a) {
        return Map.of(
                "id", a.id(),
                "code", a.code(),
                "name", a.name(),
                "accountType", a.accountType().name(),
                "status", a.status().name(),
                "currency", a.currency(),
                "balance", a.balance(),
                "version", a.version()
        );
    }

    private Map<String, Object> toInvoiceMap(FinanceInvoice i) {
        var map = new java.util.HashMap<String, Object>();
        map.put("id", i.id());
        map.put("invoiceNumber", i.invoiceNumber());
        map.put("customerType", i.customerType());
        map.put("customerName", i.customerName() != null ? i.customerName() : "");
        map.put("status", i.status().name());
        map.put("currency", i.currency());
        map.put("totalAmount", i.totalAmount());
        map.put("paidAmount", i.paidAmount());
        map.put("issueDate", i.issueDate().toString());
        map.put("dueDate", i.dueDate() != null ? i.dueDate().toString() : "");
        map.put("version", i.version());
        return map;
    }

    private Map<String, Object> toPaymentMap(FinancePayment p) {
        return Map.of(
                "id", p.id(),
                "paymentNumber", p.paymentNumber(),
                "paymentMethod", p.paymentMethod().name(),
                "amount", p.amount(),
                "currency", p.currency(),
                "status", p.status().name(),
                "paymentDate", p.paymentDate().toString(),
                "version", p.version()
        );
    }
}
