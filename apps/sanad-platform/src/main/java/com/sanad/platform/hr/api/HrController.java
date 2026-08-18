package com.sanad.platform.hr.api;

import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

    private final HrEmployeeRepository employeeRepository;

    public HrController(HrEmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/employees")
    @RequireCapability("HR.EMPLOYEE.READ")
    public List<HrEmployee> listEmployees(
            Authentication auth,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String search) {
        UUID tenantId = getTenantId(auth);
        return employeeRepository.findAll(tenantId, limit, search);
    }

    @GetMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.READ")
    public ResponseEntity<HrEmployee> getEmployee(Authentication auth, @PathVariable UUID id) {
        UUID tenantId = getTenantId(auth);
        return employeeRepository.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/employees")
    @RequireCapability("HR.EMPLOYEE.WRITE")
    public ResponseEntity<HrEmployee> createEmployee(Authentication auth, @RequestBody Map<String, Object> body) {
        UUID tenantId = getTenantId(auth);
        HrEmployee emp = new HrEmployee(
            null, tenantId, null,
            (String) body.getOrDefault("employeeNumber", "EMP-" + System.currentTimeMillis()),
            (String) body.get("firstName"), (String) body.get("lastName"),
            (String) body.getOrDefault("displayName", body.get("firstName") + " " + body.get("lastName")),
            (String) body.get("email"), (String) body.get("phone"),
            body.containsKey("departmentId") ? UUID.fromString((String) body.get("departmentId")) : null,
            body.containsKey("positionId") ? UUID.fromString((String) body.get("positionId")) : null,
            null, (String) body.getOrDefault("employmentType", "FULL_TIME"),
            (String) body.getOrDefault("status", "ACTIVE"),
            body.containsKey("hireDate") ? LocalDate.parse((String) body.get("hireDate")) : LocalDate.now(),
            null
        );
        HrEmployee saved = employeeRepository.save(emp);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.WRITE")
    public ResponseEntity<HrEmployee> updateEmployee(Authentication auth, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID tenantId = getTenantId(auth);
        return employeeRepository.findById(tenantId, id)
                .map(existing -> {
                    HrEmployee updated = new HrEmployee(
                        existing.id(), existing.tenantId(), existing.userId(),
                        (String) body.getOrDefault("employeeNumber", existing.employeeNumber()),
                        (String) body.getOrDefault("firstName", existing.firstName()),
                        (String) body.getOrDefault("lastName", existing.lastName()),
                        (String) body.getOrDefault("displayName", existing.displayName()),
                        (String) body.getOrDefault("email", existing.email()),
                        (String) body.getOrDefault("phone", existing.phone()),
                        body.containsKey("departmentId") ? UUID.fromString((String) body.get("departmentId")) : existing.departmentId(),
                        body.containsKey("positionId") ? UUID.fromString((String) body.get("positionId")) : existing.positionId(),
                        existing.managerId(),
                        (String) body.getOrDefault("employmentType", existing.employmentType()),
                        (String) body.getOrDefault("status", existing.status()),
                        existing.hireDate(), existing.terminationDate()
                    );
                    return ResponseEntity.ok(employeeRepository.save(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/employees/{id}")
    @RequireCapability("HR.EMPLOYEE.ARCHIVE")
    public ResponseEntity<Void> deleteEmployee(Authentication auth, @PathVariable UUID id) {
        UUID tenantId = getTenantId(auth);
        employeeRepository.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID getTenantId(Authentication auth) {
        // Extract tenant_id from authentication principal
        if (auth.getPrincipal() instanceof Map<?, ?> principal) {
            Object tid = principal.get("tenantId");
            if (tid != null) return UUID.fromString(tid.toString());
        }
        throw new IllegalStateException("Tenant ID not found in authentication");
    }
}
