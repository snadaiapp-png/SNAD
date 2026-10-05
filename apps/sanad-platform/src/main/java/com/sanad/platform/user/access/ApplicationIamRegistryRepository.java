package com.sanad.platform.user.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class ApplicationIamRegistryRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public ApplicationIamRegistryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ApplicationIamRegistration> findDiscoverable() {
        return jdbc.query("""
                SELECT a.code, a.name, a.localized_name, c.status, c.contract_version,
                       c.capability_namespaces::text, c.supported_scopes::text,
                       c.declared_capabilities::text
                  FROM applications a
                  JOIN application_iam_contracts c ON c.application_code = a.code
                 WHERE a.status = 'ACTIVE'
                 ORDER BY a.display_order, a.code
                """, (rs, rowNum) -> new ApplicationIamRegistration(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("localized_name"),
                rs.getString("status"),
                rs.getString("contract_version"),
                parseSet(rs.getString("capability_namespaces")),
                parseSet(rs.getString("supported_scopes")),
                parseSet(rs.getString("declared_capabilities"))));
    }

    private static Set<String> parseSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(JSON.readValue(json, STRING_LIST));
        } catch (Exception ignored) {
            return Set.of();
        }
    }
}
