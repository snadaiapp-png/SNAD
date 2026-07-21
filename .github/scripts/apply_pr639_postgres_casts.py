from pathlib import Path


def replace_exact(path: Path, replacements: dict[str, tuple[str, int]]) -> None:
    text = path.read_text(encoding="utf-8")
    for old, (new, expected_count) in replacements.items():
        count = text.count(old)
        assert count == expected_count, (
            f"{path}: expected {expected_count} occurrences of {old!r}, found {count}"
        )
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")


repository = Path(
    "apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/"
    "JdbcAddressCommunicationRepository.java"
)
replace_exact(
    repository,
    {
        ":beforeTime IS NULL": ("CAST(:beforeTime AS TIMESTAMP) IS NULL", 2),
        ":beforeId IS NULL": ("CAST(:beforeId AS UUID) IS NULL", 2),
        ":methodType IS NULL": ("CAST(:methodType AS VARCHAR) IS NULL", 1),
        ":verificationStatus IS NULL": (
            "CAST(:verificationStatus AS VARCHAR) IS NULL",
            1,
        ),
    },
)

test_path = Path(
    "apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/infrastructure/"
    "JdbcAddressCommunicationNullableFilterPostgresTest.java"
)
replace_exact(
    test_path,
    {
        ":beforeTime IS NULL": ("CAST(:beforeTime AS TIMESTAMP) IS NULL", 2),
        ":beforeId IS NULL": ("CAST(:beforeId AS UUID) IS NULL", 2),
        ":methodType IS NULL": ("CAST(:methodType AS VARCHAR) IS NULL", 1),
        ":verificationStatus IS NULL": (
            "CAST(:verificationStatus AS VARCHAR) IS NULL",
            1,
        ),
    },
)

text = test_path.read_text(encoding="utf-8")
old = '''                .contains("addValue(\\\"verificationStatus\\\", verificationStatus, Types.VARCHAR)");'''
new = '''                .contains("addValue(\\\"verificationStatus\\\", verificationStatus, Types.VARCHAR)")
                .contains("CAST(:beforeTime AS TIMESTAMP) IS NULL")
                .contains("CAST(:beforeId AS UUID) IS NULL")
                .contains("CAST(:methodType AS VARCHAR) IS NULL")
                .contains("CAST(:verificationStatus AS VARCHAR) IS NULL");'''
assert text.count(old) == 1, "source contract assertion block changed unexpectedly"
test_path.write_text(text.replace(old, new, 1), encoding="utf-8")

Path(".github/workflows/apply-pr639-postgres-casts.yml").unlink()
Path(".github/scripts/apply_pr639_postgres_casts.py").unlink()
