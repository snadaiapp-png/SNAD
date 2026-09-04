package com.sanad.platform.hr.assignment;

import com.sanad.platform.hr.assignment.infrastructure.JdbcHrAssignmentRepository;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * HRM-G0 / WS5 Task 4 — Spring wiring for the canonical Assignment v2 slice.
 *
 * <p>The certified WS2 repository remains annotation-free; this
 * configuration exposes it to the production context. The two-arg variant
 * (transactional evidence writer) is chosen so assignment create/end/
 * change-manager/transfer keep appending atomic audit + outbox evidence
 * exactly as the WS2/WS4 atomicity suites proved.
 */
@Configuration
public class HrAssignmentBeanConfiguration {

    @Bean
    public JdbcHrAssignmentRepository hrAssignmentRepository(DataSource dataSource,
                                                             HrTransactionalEvidenceWriter evidenceWriter) {
        return new JdbcHrAssignmentRepository(dataSource, evidenceWriter);
    }
}
