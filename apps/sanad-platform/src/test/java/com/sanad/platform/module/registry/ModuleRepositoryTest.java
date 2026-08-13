package com.sanad.platform.module.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModuleRepository}.
 *
 * <p>Uses Mockito mocks for {@link JdbcTemplate} — no database required.
 * Integration tests with PostgreSQL Direct are in
 * {@code ModuleRepositoryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleRepository — unit tests")
class ModuleRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;
    private ModuleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ModuleRepository(jdbc);
    }

    @Test
    @DisplayName("findAll returns all modules ordered by display_order")
    void findAll_returnsModules() {
        ModuleEntity m1 = createModule("CRM", "CRM", 10);
        ModuleEntity m2 = createModule("AI", "AI", 20);
        when(jdbc.query(any(String.class), any(RowMapper.class)))
                .thenReturn(List.of(m1, m2));

        List<ModuleEntity> result = repository.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("CRM");
        assertThat(result.get(1).getCode()).isEqualTo("AI");
    }

    @Test
    @DisplayName("findAllEnabled returns only ACTIVE + enabled modules")
    void findAllEnabled_returnsOnlyEnabled() {
        ModuleEntity m1 = createModule("CRM", "CRM", 10);
        when(jdbc.query(any(String.class), any(RowMapper.class)))
                .thenReturn(List.of(m1));

        List<ModuleEntity> result = repository.findAllEnabled();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("CRM");
    }

    @Test
    @DisplayName("findByCode returns module when found")
    void findByCode_returnsModule() {
        UUID id = UUID.randomUUID();
        ModuleEntity m = createModule("CRM", "CRM", 10);
        m.setId(id);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("CRM")))
                .thenReturn(List.of(m));

        Optional<ModuleEntity> result = repository.findByCode("CRM");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("findByCode returns empty when not found")
    void findByCode_returnsEmptyWhenNotFound() {
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("UNKNOWN")))
                .thenReturn(Collections.emptyList());

        Optional<ModuleEntity> result = repository.findByCode("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByCode returns empty for null/blank code")
    void findByCode_returnsEmptyForNull() {
        Optional<ModuleEntity> result1 = repository.findByCode(null);
        Optional<ModuleEntity> result2 = repository.findByCode("");

        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }

    @Test
    @DisplayName("findByCode uppercases the code before query")
    void findByCode_uppercasesCode() {
        ModuleEntity m = createModule("CRM", "CRM", 10);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("CRM")))
                .thenReturn(List.of(m));

        Optional<ModuleEntity> result = repository.findByCode("crm");

        assertThat(result).isPresent();
    }

    private ModuleEntity createModule(String code, String name, int displayOrder) {
        return new ModuleEntity(
                UUID.randomUUID(), code, name, "description",
                "ACTIVE", displayOrder, "1.0", true,
                java.time.Instant.now(), java.time.Instant.now()
        );
    }
}
