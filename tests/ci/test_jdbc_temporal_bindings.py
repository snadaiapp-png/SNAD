import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "ci" / "check_jdbc_temporal_bindings.py"
spec = importlib.util.spec_from_file_location("jdbc_temporal_guard", SCRIPT)
guard = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(guard)


class JdbcTemporalBindingGuardTest(unittest.TestCase):
    def scan(self, source: str):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "Example.java"
            path.write_text(source, encoding="utf-8")
            return guard.scan_source(path, source)

    def test_rejects_direct_instant_in_jdbc_template(self):
        findings = self.scan(
            """
            import java.time.Instant;
            class Example {
              void f(org.springframework.jdbc.core.JdbcTemplate jdbc) {
                Instant now = Instant.now();
                jdbc.update("insert into x(created_at) values (?)", now);
              }
            }
            """
        )
        self.assertTrue(findings)

    def test_rejects_named_parameter_add_value_with_instant(self):
        findings = self.scan(
            """
            import java.time.Instant;
            class Example {
              void f() {
                Instant effectiveAt = Instant.now();
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("effectiveAt", effectiveAt);
              }
            }
            """
        )
        self.assertTrue(findings)

    def test_rejects_prepared_statement_set_object_without_sql_type(self):
        findings = self.scan(
            """
            import java.time.Instant;
            class Example {
              void f(java.sql.PreparedStatement ps) throws Exception {
                Instant now = Instant.now();
                ps.setObject(1, now);
              }
            }
            """
        )
        self.assertTrue(findings)

    def test_accepts_timestamp_conversion(self):
        findings = self.scan(
            """
            import java.sql.Timestamp;
            import java.time.Instant;
            class Example {
              void f(org.springframework.jdbc.core.JdbcTemplate jdbc) {
                Instant now = Instant.now();
                jdbc.update("insert into x(created_at) values (?)", Timestamp.from(now));
              }
            }
            """
        )
        self.assertEqual([], findings)

    def test_accepts_explicit_timestamptz_binding(self):
        findings = self.scan(
            """
            import java.sql.Types;
            import java.time.Instant;
            class Example {
              void f(java.sql.PreparedStatement ps) throws Exception {
                Instant now = Instant.now();
                ps.setObject(1, java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
              }
            }
            """
        )
        self.assertEqual([], findings)

    def test_repository_tree_has_no_unsafe_temporal_bindings(self):
        findings = guard.scan_tree(
            Path(__file__).resolve().parents[2] / "apps" / "sanad-platform" / "src" / "main" / "java"
        )
        self.assertEqual([], findings, "\n".join(findings))


if __name__ == "__main__":
    unittest.main()
