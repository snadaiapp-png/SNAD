package com.sanad.platform.subscription.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link ProductEntity} — raw SQL, consistent with
 * {@link ApplicationRepository} and {@code ModuleRepository}.
 *
 * <p>R0C-11: the {@code uk_products_code} unique index is the final fail-closed
 * guard behind the service-level pre-check; a concurrent duplicate insert is
 * surfaced as {@link DuplicateKeyException} so the service can translate it
 * into the deterministic domain error.</p>
 */
@Repository
public class ProductRepository {

    static final RowMapper<ProductEntity> ROW_MAPPER = (rs, rowNum) -> {
        ProductEntity p = new ProductEntity();
        p.setId(rs.getObject("id", UUID.class));
        p.setCode(rs.getString("code"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setApplicationId(rs.getObject("application_id", UUID.class));
        p.setProductType(rs.getString("product_type"));
        p.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        p.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        p.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return p;
    };

    private final JdbcTemplate jdbc;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> findAll() {
        return jdbc.query("SELECT * FROM products ORDER BY code", ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> findAvailable() {
        return jdbc.query(
                "SELECT * FROM products WHERE status = 'ACTIVE' ORDER BY code",
                ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public Optional<ProductEntity> findById(UUID id) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT * FROM products WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProductEntity> findByCode(String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM products WHERE code = ?", ROW_MAPPER, code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE code = ?", Long.class, code);
        return count != null && count > 0;
    }

    /**
     * Fail-closed existence check for the {@code applications} FK
     * (mirrors {@code PriceService.requireExists} conventions).
     */
    @Transactional(readOnly = true)
    public boolean applicationExists(UUID applicationId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE id = ?", Long.class, applicationId);
        return count != null && count > 0;
    }

    @Transactional
    public void insert(ProductEntity p) {
        try {
            jdbc.update("""
                            INSERT INTO products (
                                id, code, name, description, application_id,
                                product_type, status, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getApplicationId(), p.getProductType(), p.getStatus(),
                    Timestamp.from(p.getCreatedAt()), Timestamp.from(p.getUpdatedAt()));
        } catch (DuplicateKeyException e) {
            // uk_products_code is the final guard; translate for the service.
            throw new DuplicateKeyException("Product code already exists: " + p.getCode(), e);
        }
    }

    @Transactional
    public void update(ProductEntity p) {
        try {
            jdbc.update("""
                            UPDATE products SET
                                name = ?, description = ?, application_id = ?,
                                product_type = ?, status = ?, updated_at = ?
                            WHERE id = ?
                            """,
                    p.getName(), p.getDescription(), p.getApplicationId(),
                    p.getProductType(), p.getStatus(),
                    Timestamp.from(p.getUpdatedAt()), p.getId());
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException(
                    "Product update violates a data integrity constraint: " + p.getId(), e);
        }
    }
}
