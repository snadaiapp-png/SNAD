package com.sanad.platform.crm.product.web;

import com.sanad.platform.crm.product.application.ProductUseCases;
import com.sanad.platform.crm.product.domain.ProductRepository.*;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/crm/products")
public class ProductController {

    private final ProductUseCases products;

    public ProductController(ProductUseCases products) { this.products = products; }

    @RequireCapability("CRM.PRODUCT.READ")
    @GetMapping
    public List<Map<String, Object>> list(@org.springframework.security.core.annotation.AuthenticationPrincipal Authentication auth,
                                           @RequestParam(defaultValue = "50") int limit,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(required = false) Boolean activeOnly,
                                           @RequestParam(required = false) String category) {
        UUID t = tenantId(auth);
        return products.list(t, Math.max(1, Math.min(limit, 200)), search, activeOnly, category).stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.PRODUCT.READ")
    @GetMapping("/{id}")
    public Map<String, Object> get(@org.springframework.security.core.annotation.AuthenticationPrincipal Authentication auth, @PathVariable UUID id) {
        return toRow(products.getById(tenantId(auth), id));
    }

    @RequireCapability("CRM.PRODUCT.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@org.springframework.security.core.annotation.AuthenticationPrincipal Authentication auth,
                                                       @Valid @RequestBody CreateProductRequest req) {
        ProductRecord created = products.create(tenantId(auth), userId(auth), new CreateProductCommand(
                req.name(), req.sku(), req.description(), req.productType(), req.category(),
                req.unitPrice(), req.currencyCode(), req.taxRate(), req.unit()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.PRODUCT.WRITE")
    @PatchMapping("/{id}")
    public Map<String, Object> update(@org.springframework.security.core.annotation.AuthenticationPrincipal Authentication auth,
                                       @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest req) {
        ProductRecord current = products.getById(tenantId(auth), id);
        return toRow(products.update(tenantId(auth), userId(auth), id,
                new UpdateProductCommand(req.name(), req.sku(), req.description(), req.productType(), req.category(),
                        req.unitPrice(), req.currencyCode(), req.taxRate(), req.unit(), req.active()),
                current.version()));
    }

    @RequireCapability("CRM.PRODUCT.WRITE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@org.springframework.security.core.annotation.AuthenticationPrincipal Authentication auth, @PathVariable UUID id) {
        products.delete(tenantId(auth), userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(ProductRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id()); m.put("version", r.version());
        m.put("name", r.name()); m.put("sku", r.sku()); m.put("description", r.description());
        m.put("product_type", r.productType()); m.put("category", r.category());
        m.put("unit_price", r.unitPrice().toPlainString()); m.put("currency_code", r.currencyCode());
        m.put("tax_rate", r.taxRate().toPlainString()); m.put("unit", r.unit()); m.put("active", r.active());
        m.put("created_at", toIso(r.createdAt())); m.put("updated_at", toIso(r.updatedAt()));
        return m;
    }
    private static String toIso(Instant v) { return v == null ? null : v.toString(); }
    private static UUID tenantId(Authentication auth) { return ctx(auth, "tenant_id"); }
    private static UUID userId(Authentication auth) { return ctx(auth, "user_id"); }
    private static UUID ctx(Authentication auth, String key) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Map<?,?> d) || d.get(key) == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        try { return UUID.fromString(d.get(key).toString()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid CRM context", e); }
    }
}

record CreateProductRequest(
        @NotBlank @Size(max=240) String name,
        @Size(max=80) String sku,
        @Size(max=4000) String description,
        @Pattern(regexp="PRODUCT|SERVICE", flags=Pattern.Flag.CASE_INSENSITIVE) String productType,
        @Size(max=120) String category,
        BigDecimal unitPrice,
        @Pattern(regexp="[A-Za-z]{3}") String currencyCode,
        BigDecimal taxRate,
        @Size(max=40) String unit) {}

record UpdateProductRequest(
        @Size(max=240) String name,
        @Size(max=80) String sku,
        @Size(max=4000) String description,
        @Pattern(regexp="PRODUCT|SERVICE", flags=Pattern.Flag.CASE_INSENSITIVE) String productType,
        @Size(max=120) String category,
        BigDecimal unitPrice,
        @Pattern(regexp="[A-Za-z]{3}") String currencyCode,
        BigDecimal taxRate,
        @Size(max=40) String unit,
        Boolean active) {}
