package com.sanad.platform.finance.infrastructure;

import com.sanad.platform.finance.domain.FinanceAccount;
import com.sanad.platform.finance.domain.FinanceAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcFinanceAccountRepository implements FinanceAccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcFinanceAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<FinanceAccount> MAPPER = (rs, rowNum) -> new FinanceAccount(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            FinanceAccount.AccountType.valueOf(rs.getString("account_type")),
            rs.getObject("parent_account_id", UUID.class),
            rs.getString("currency"),
            FinanceAccount.Status.valueOf(rs.getString("status")),
            rs.getString("description"),
            rs.getBigDecimal("balance"),
            rs.getLong("version_lock"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public FinanceAccount save(FinanceAccount account) {
        jdbc.update("""
                INSERT INTO finance_accounts
                    (id, tenant_id, code, name, account_type, parent_account_id, currency,
                     status, description, balance, version_lock, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    account_type = EXCLUDED.account_type,
                    parent_account_id = EXCLUDED.parent_account_id,
                    currency = EXCLUDED.currency,
                    status = EXCLUDED.status,
                    description = EXCLUDED.description,
                    balance = EXCLUDED.balance,
                    version_lock = EXCLUDED.version_lock,
                    version = EXCLUDED.version,
                    updated_at = EXCLUDED.updated_at
                """,
                account.id(), account.tenantId(), account.code(), account.name(),
                account.accountType().name(), account.parentAccountId(), account.currency(),
                account.status().name(), account.description(), account.balance(),
                account.versionLock(), account.version(),
                Timestamp.from(account.createdAt()), Timestamp.from(account.updatedAt())
        );
        return account;
    }

    @Override
    public Optional<FinanceAccount> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM finance_accounts WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<FinanceAccount> findByCode(UUID tenantId, String code) {
        return jdbc.query("SELECT * FROM finance_accounts WHERE tenant_id = ? AND code = ?",
                MAPPER, tenantId, code).stream().findFirst();
    }

    @Override
    public List<FinanceAccount> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM finance_accounts WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<FinanceAccount> findByTenantAndType(UUID tenantId, FinanceAccount.AccountType type, int limit) {
        return jdbc.query("SELECT * FROM finance_accounts WHERE tenant_id = ? AND account_type = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, type.name(), Math.max(1, Math.min(limit, 1000)));
    }
}
