package com.sanad.platform.hr.api;

import com.sanad.platform.hr.compatibility.LegacyHrCompatibilityService;
import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HR Employee REST controller — legacy v1 compatibility adapter (WS5 Task 7).
 *
 * <p>Since the HRM-G0 canonical v2 surface is authoritative, this controller
 * no longer contains business logic: reads remain compatible (CANONICAL
 * tenants are served through the canonical projection), writes delegate to
 * {@link LegacyHrCompatibilityService} which enforces the migration state
 * machine (write freeze during MIGRATING/BLOCKED, unambiguous-employer
 * contract for create, profile-only patch scope), and the unsafe physical
 * delete is retired — DELETE always answers 409 HRM_MIGRATION_REQUIRED.
 *
 * <p>Tenant identity is derived from the authenticated server-side security
 * context via {@link SecurityContextUtils#tenantId(Authentication)} — the
 * canonical platform pattern. Cross-tenant access is additionally prevented
 * at the database layer by fail-closed FORCE RLS on every HR table.
 */
@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

    private final LegacyHrCompatibilityService compatibility;

    public HrController(LegacyHrCompatibilityService compatibility) {
        this.compatibility = compatibility;
    }

    @GetMapping("/employees")
    @RequireCapability("HR.EMPLOYEE.READ")
    public List<HrEmployee> listEmployees(
            Authentication auth,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String search) {
        return compatibility.listEmployees(SecurityContextUtils.tenantId(auth), limit, search);
    }

    @GetMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.READ")
    public ResponseEntity<HrEmployee> getEmployee(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(compatibility.getEmployee(SecurityContextUtils.tenantId(auth), id));
    }

    @PostMapping("/employees")
    @RequireCapability("HR.EMPLOYEE.WRITE")
    public ResponseEntity<?> createEmployee(Authentication auth, @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(auth);
        try {
            HrEmployee saved = compatibility.createEmployee(tenantId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalStateException e) {
            return migrationRequiredResponse(e);
        }
    }

    @PatchMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.WRITE")
    public ResponseEntity<?> updateEmployee(Authentication auth, @PathVariable UUID id,
                                            @RequestBody Map<String, Object> body) {
        UUID tenantId = SecurityContextUtils.tenantId(auth);
        try {
            return ResponseEntity.ok(compatibility.patchEmployee(tenantId, id, body));
        } catch (IllegalStateException e) {
            return migrationRequiredResponse(e);
        }
    }

    @DeleteMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.ARCHIVE")
    public ResponseEntity<Map<String, Object>> deleteEmployee(Authentication auth, @PathVariable UUID id) {
        // v1 unsafe delete is retired (WS5 Task 7): employment records are
        // lifecycle-managed exclusively through the canonical v2 APIs.
        UUID tenantId = SecurityContextUtils.tenantId(auth);
        try {
            compatibility.deleteEmployee(tenantId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return migrationRequiredResponse(e);
        }
    }

    private ResponseEntity<Map<String, Object>> migrationRequiredResponse(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", "HRM_MIGRATION_REQUIRED",
                        "message", String.valueOf(e.getMessage())));
    }
}
