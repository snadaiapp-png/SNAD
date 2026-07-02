package com.sanad.platform.idempotency.service;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Stage 05A.2.7 — Functional interface for tenant-bound SQL work.
 */
@FunctionalInterface
public interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
}
