package com.sanad.platform.hr.recruitment.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 T8.3 — G0 authority preservation: the hire-conversion code (G1) must
 * never touch canonical G0 HR tables with its own SQL. Every canonical write
 * flows through the G0 connection-scoped service/repository variants.
 *
 * <p>DIRECT_CANONICAL_SQL_FROM_G1 = 0 is enforced by scanning every G1
 * recruitment + onboarding source file for canonical table names in SQL
 * contexts. Read-side exceptions: none for the identity/employment/
 * assignment/contract/compensation stores — lookups go through G0 repos.</p>
 */
class HrHireConversionArchitectureBoundaryTest {

    private static final Pattern CANONICAL_TABLE = Pattern.compile(
            "\\b(hr_people|hr_person_private|hr_person_identifiers|hr_employees|"
                    + "hr_employee_assignments|hr_employment_status_periods|"
                    + "hr_employment_contracts|hr_employment_contract_versions|"
                    + "hr_compensation_packages|hr_compensation_components)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_STRING = Pattern.compile(
            "(\"[^\"]*\\b(SELECT|INSERT|UPDATE|DELETE)\\b[^\"]*\")",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Path MODULE_ROOT = Paths.get(
            "src/main/java/com/sanad/platform/hr/recruitment");

    @Test
    void directCanonicalSqlFromG1_isZero() throws IOException {
        assertThat(MODULE_ROOT).as("G1 recruitment module must exist").exists();
        List<String> violations = new java.util.ArrayList<>();
        try (Stream<Path> sources = Files.walk(MODULE_ROOT)) {
            sources.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String source = read(p);
                        java.util.regex.Matcher sql = SQL_STRING.matcher(source);
                        while (sql.find()) {
                            String literal = sql.group();
                            java.util.regex.Matcher table = CANONICAL_TABLE.matcher(literal);
                            if (table.find()) {
                                violations.add(p.getFileName() + " → " + literal.substring(0,
                                        Math.min(120, literal.length())));
                            }
                        }
                    });
        }
        assertThat(violations)
                .as("T8.3: DIRECT_CANONICAL_SQL_FROM_G1 = 0 — canonical tables are written "
                        + "and read ONLY through G0 services (violations: %s)", violations)
                .isEmpty();
    }

    private String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
