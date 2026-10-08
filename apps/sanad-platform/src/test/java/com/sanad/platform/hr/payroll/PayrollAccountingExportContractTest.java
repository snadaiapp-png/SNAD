package com.sanad.platform.hr.payroll;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4-T7 RED-first contract for the HRM -> Accounting export boundary.
 *
 * <p>The contract must remain inside the HR payroll application boundary and
 * carry identifiers/correlation metadata only. Accounting owns journal/GL
 * posting. Raw payroll monetary detail must not leak through this boundary.</p>
 */
class PayrollAccountingExportContractTest {

    private static final String PORT =
            "com.sanad.platform.hr.payroll.application.PayrollAccountingExportPort";

    @Test
    void dedicatedAccountingExportPortMustExistInsideHrApplicationBoundary() {
        Class<?> port = load(PORT);

        assertThat(port)
                .as("G4-T7 requires an explicit HR-owned Accounting export application port")
                .isNotNull();
        assertThat(port.isInterface()).isTrue();
        assertThat(port.getPackageName())
                .isEqualTo("com.sanad.platform.hr.payroll.application");
    }

    @Test
    void exportRequestMustBeTenantScopedIdempotentAndCorrelationAware() throws Exception {
        Class<?> request = nested("ExportRequest");
        assertThat(request.isRecord()).isTrue();

        Set<String> components = Arrays.stream(request.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(components).containsExactlyInAnyOrder(
                "tenantId",
                "payrollRunId",
                "legalEntityId",
                "periodStart",
                "periodEnd",
                "currencyCode",
                "approvedVersion",
                "correlationId",
                "requestId",
                "idempotencyKey");

        assertThat(componentType(request, "tenantId")).isEqualTo(UUID.class);
        assertThat(componentType(request, "payrollRunId")).isEqualTo(UUID.class);
        assertThat(componentType(request, "legalEntityId")).isEqualTo(UUID.class);
        assertThat(componentType(request, "periodStart")).isEqualTo(LocalDate.class);
        assertThat(componentType(request, "periodEnd")).isEqualTo(LocalDate.class);
        assertThat(componentType(request, "correlationId")).isEqualTo(UUID.class);
        assertThat(componentType(request, "requestId")).isEqualTo(UUID.class);
        assertThat(componentType(request, "idempotencyKey")).isEqualTo(String.class);
    }

    @Test
    void exportBoundaryMustNotExposePayrollAmountsOrPostingInstructions() {
        Class<?> request = nested("ExportRequest");

        Set<String> names = Arrays.stream(request.getRecordComponents())
                .map(c -> c.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertThat(names).noneMatch(name ->
                name.contains("amount")
                        || name.contains("gross")
                        || name.contains("net")
                        || name.contains("deduction")
                        || name.contains("earning")
                        || name.contains("debit")
                        || name.contains("credit")
                        || name.contains("journal")
                        || name.contains("ledger")
                        || name.contains("account"));
    }

    @Test
    void portMustExposeSingleIdempotentExportRequestOperation() throws Exception {
        Class<?> port = loadRequired(PORT);
        Class<?> request = nested("ExportRequest");
        Class<?> receipt = nested("ExportReceipt");

        Method method = port.getDeclaredMethod("requestExport", request);

        assertThat(method.getReturnType()).isEqualTo(receipt);
        assertThat(Arrays.stream(port.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet()))
                .containsExactly("requestExport");
    }

    @Test
    void receiptMustCarryCorrelationAndStatusEvidenceOnly() {
        Class<?> receipt = nested("ExportReceipt");
        assertThat(receipt.isRecord()).isTrue();

        Set<String> components = Arrays.stream(receipt.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(components).containsExactlyInAnyOrder(
                "correlationId",
                "externalReference",
                "status");

        Set<String> names = components.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        assertThat(names).noneMatch(name ->
                name.contains("journal")
                        || name.contains("ledger")
                        || name.contains("posting")
                        || name.contains("amount")
                        || name.contains("debit")
                        || name.contains("credit"));
    }

    private static Class<?> nested(String simpleName) {
        Class<?> port = loadRequired(PORT);
        return Arrays.stream(port.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "G4-T7 requires nested contract type " + simpleName));
    }

    private static Class<?> componentType(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getName().equals(name))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing required export component: " + name));
    }

    private static Class<?> loadRequired(String name) {
        Class<?> loaded = load(name);
        assertThat(loaded)
                .as("Missing G4-T7 contract type: " + name)
                .isNotNull();
        return loaded;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }
}
