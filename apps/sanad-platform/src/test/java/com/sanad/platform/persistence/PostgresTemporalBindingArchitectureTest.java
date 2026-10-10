package com.sanad.platform.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-module architecture gate for PostgreSQL temporal binding.
 *
 * <p>This scans every production Java source file, including future modules,
 * and rejects the unsafe pattern that caused the production user-provisioning
 * incident: a raw java.time.Instant passed to JDBC parameter binding.
 */
class PostgresTemporalBindingArchitectureTest {

    private static final Pattern INSTANT_VARIABLE =
            Pattern.compile("\\bInstant\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final List<String> JDBC_CALLS = List.of(
            ".update(", ".query(", ".queryForObject(", ".batchUpdate(",
            ".addValue(", ".setObject(");

    @Test
    void productionSourcesDoNotBindRawInstantsToJdbc() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("PostgresTemporalBindings.java"))
                    .forEach(path -> inspect(path, violations));
        }

        assertTrue(
                violations.isEmpty(),
                "Raw java.time.Instant JDBC binding is forbidden. Use "
                        + "PostgresTemporalBindings / Timestamp.from. Violations:\n"
                        + String.join("\n", violations));
    }

    private static void inspect(Path path, List<String> violations) {
        final String source;
        try {
            source = Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!source.contains("Instant")) return;

        List<String> instantVariables = new ArrayList<>();
        Matcher matcher = INSTANT_VARIABLE.matcher(source);
        while (matcher.find()) instantVariables.add(matcher.group(1));

        for (String marker : JDBC_CALLS) {
            int cursor = 0;
            while ((cursor = source.indexOf(marker, cursor)) >= 0) {
                int open = cursor + marker.length() - 1;
                int close = matchingParen(source, open);
                if (close < 0) break;
                String call = source.substring(open + 1, close);
                String unsafeSurface = scrubKnownSafeConversions(call);

                if (Pattern.compile("\\bInstant\\s*\\.\\s*now\\s*\\(")
                        .matcher(unsafeSurface).find()) {
                    violations.add(path + " -> raw Instant.now() in " + marker);
                }

                for (String variable : instantVariables) {
                    if (Pattern.compile("\\b" + Pattern.quote(variable) + "\\b")
                            .matcher(unsafeSurface).find()) {
                        violations.add(path + " -> raw Instant variable '" + variable
                                + "' in " + marker);
                    }
                }
                cursor = close + 1;
            }
        }
    }

    private static String scrubKnownSafeConversions(String input) {
        String value = input;
        value = value.replaceAll(
                "(?:java\\.sql\\.)?Timestamp\\.from\\([^;\\n]*?\\)",
                " SAFE_TIMESTAMP ");
        value = value.replaceAll(
                "PostgresTemporalBindings\\.(?:timestamp|timestamptz|setTimestamptz)\\([^;\\n]*?\\)",
                " SAFE_TEMPORAL ");
        value = value.replaceAll(
                "(?:java\\.time\\.)?OffsetDateTime\\.ofInstant\\([^;\\n]*?ZoneOffset\\.UTC\\)",
                " SAFE_OFFSET_DATETIME ");
        return value;
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }
}
