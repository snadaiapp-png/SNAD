package com.sanad.platform.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 — shared state-machine test harness (G1-T1 REFACTOR output; reused
 * by T3–T9 guard/service tests).
 *
 * <p>Exhaustively verifies a transition guard against the design matrix:
 * for the FULL cross product of states, the guard must accept exactly the
 * declared allowed pairs — no more, no less (fail-closed beyond the matrix).</p>
 */
public final class HrG1StateMatrixHarness {

    @FunctionalInterface
    public interface TransitionPredicate<S extends Enum<S>> {
        boolean allowed(S from, S to);
    }

    private HrG1StateMatrixHarness() {
    }

    /**
     * @param allowedPairs pairs in {@code "FROM->TO"} form (enum names)
     * @param states       all states of the machine
     * @param guard        the guard predicate under test
     */
    public static <S extends Enum<S>> void assertExactly(
            Set<String> allowedPairs, Class<S> type, S[] states, TransitionPredicate<S> guard) {
        Map<S, Set<S>> allowed = new HashMap<>();
        for (String pair : allowedPairs) {
            String[] parts = pair.split("->");
            S from = Enum.valueOf(type, parts[0]);
            S to = Enum.valueOf(type, parts[1]);
            allowed.computeIfAbsent(from, k -> new HashSet<>()).add(to);
        }
        for (S from : states) {
            for (S to : states) {
                boolean expected = allowed.getOrDefault(from, Set.of()).contains(to);
                assertThat(guard.allowed(from, to))
                        .as("guard(%s -> %s) must be %s per the design matrix", from, to,
                                expected ? "ALLOWED" : "FORBIDDEN")
                        .isEqualTo(expected);
            }
        }
    }

    /** Terminal states must have zero outgoing transitions. */
    public static <S extends Enum<S>> void assertTerminals(
            Set<S> terminals, S[] states, TransitionPredicate<S> guard) {
        for (S terminal : terminals) {
            for (S to : states) {
                assertThat(guard.allowed(terminal, to))
                        .as("terminal state %s must never transition out (to=%s)", terminal, to)
                        .isFalse();
            }
        }
    }
}
