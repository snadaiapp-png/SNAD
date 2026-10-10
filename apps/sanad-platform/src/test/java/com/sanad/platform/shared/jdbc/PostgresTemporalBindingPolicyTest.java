package com.sanad.platform.shared.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Platform-wide guard against raw java.time.Instant JDBC writes. */
class PostgresTemporalBindingPolicyTest {

    private static final Pattern INSTANT_SYMBOL =
            Pattern.compile("\\bInstant\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private static final List<String> SAFE_TEMPORAL_BINDERS = List.of(
            "Timestamp.from(",
            "java.sql.Timestamp.from(",
            "OffsetDateTime.ofInstant(",
            "PostgresTemporalBinding.",
            "timestamp(",
            "ts("
    );

    @Test
    void productionJdbcWritesNeverBindRawInstant() throws Exception {
        Path root = Path.of("src/main/java");
        assertThat(root).exists();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                inspect(path, violations);
            }
        }

        assertThat(violations)
                .as("Raw Instant JDBC bindings are forbidden. Convert via Timestamp.from(...) "
                        + "or PostgresTemporalBinding. Violations: %s", violations)
                .isEmpty();
    }

    private static void inspect(Path path, List<String> violations) throws IOException {
        String source = Files.readString(path);
        if (!source.contains("Instant") || (!source.contains(".update(") && !source.contains(".addValue("))) {
            return;
        }

        Set<String> instantSymbols = new HashSet<>();
        Matcher symbols = INSTANT_SYMBOL.matcher(source);
        while (symbols.find()) {
            instantSymbols.add(symbols.group(1));
        }

        for (String call : extractCalls(source, ".update(")) {
            List<String> args = splitTopLevelArguments(call);
            for (int i = 1; i < args.size(); i++) {
                checkExpression(path, "update argument " + i, args.get(i), instantSymbols, violations);
            }
        }

        for (String call : extractCalls(source, ".addValue(")) {
            List<String> args = splitTopLevelArguments(call);
            if (args.size() >= 2) {
                checkExpression(path, "addValue value", args.get(1), instantSymbols, violations);
            }
        }
    }

    private static void checkExpression(
            Path path, String location, String expression, Set<String> instantSymbols, List<String> violations) {
        String compact = expression.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty() || isSafelyBound(compact)) return;

        if (compact.contains("Instant.now()")) {
            violations.add(path + " [" + location + "] raw Instant.now(): " + compact);
            return;
        }

        for (String symbol : instantSymbols) {
            if (Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(symbol)
                    + "(?![A-Za-z0-9_$])").matcher(compact).find()) {
                violations.add(path + " [" + location + "] raw Instant '" + symbol + "': " + compact);
                return;
            }
        }
    }

    private static boolean isSafelyBound(String expression) {
        for (String binder : SAFE_TEMPORAL_BINDERS) {
            if (expression.contains(binder)) return true;
        }
        return expression.contains("Types.TIMESTAMP")
                || expression.contains("Types.TIMESTAMP_WITH_TIMEZONE");
    }

    private static List<String> extractCalls(String source, String marker) {
        List<String> calls = new ArrayList<>();
        int from = 0;
        while (true) {
            int markerIndex = source.indexOf(marker, from);
            if (markerIndex < 0) break;
            int open = markerIndex + marker.length() - 1;
            int close = matchingParen(source, open);
            if (close < 0) break;
            calls.add(source.substring(open + 1, close));
            from = close + 1;
        }
        return calls;
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        boolean string = false;
        boolean character = false;
        boolean escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaped) { escaped = false; continue; }
            if ((string || character) && c == '\\') { escaped = true; continue; }
            if (!character && c == '"') { string = !string; continue; }
            if (!string && c == '\'') { character = !character; continue; }
            if (string || character) continue;
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<String> splitTopLevelArguments(String call) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        boolean string = false;
        boolean character = false;
        boolean escaped = false;
        int start = 0;
        for (int i = 0; i < call.length(); i++) {
            char c = call.charAt(i);
            if (escaped) { escaped = false; continue; }
            if ((string || character) && c == '\\') { escaped = true; continue; }
            if (!character && c == '"') { string = !string; continue; }
            if (!string && c == '\'') { character = !character; continue; }
            if (string || character) continue;
            if (c == '(' || c == '[' || c == '{') depth++;
            if (c == ')' || c == ']' || c == '}') depth--;
            if (c == ',' && depth == 0) {
                args.add(call.substring(start, i));
                start = i + 1;
            }
        }
        args.add(call.substring(start));
        return args;
    }
}
