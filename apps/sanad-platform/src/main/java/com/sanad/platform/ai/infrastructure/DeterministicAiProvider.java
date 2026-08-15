package com.sanad.platform.ai.infrastructure;

import com.sanad.platform.ai.domain.AiAgent;
import com.sanad.platform.ai.domain.AiProvider;
import org.springframework.stereotype.Component;

/**
 * Deterministic (rule-based) AI Provider — the default, always-available provider.
 *
 * <p>Uses simple deterministic rules to produce advisory output. Zero cost,
 * zero external dependencies. This is the safe fallback when no external
 * AI provider is configured.
 *
 * <p>AI safety: ALL output is advisory-only. This provider NEVER mutates
 * business state — it only returns a {@link Result} with advisory text.
 */
@Component
public class DeterministicAiProvider implements AiProvider {

    @Override
    public String providerName() {
        return "deterministic";
    }

    @Override
    public Result execute(AiAgent agent, String input) {
        var start = System.currentTimeMillis();
        if (input == null || input.isBlank()) {
            return Result.failure("Input must not be blank", System.currentTimeMillis() - start);
        }

        // Deterministic rule: echo the input with a summary marker.
        // This is intentionally simple — it proves the provider abstraction
        // works end-to-end without any external AI dependency.
        var output = "[ADVISORY] Agent '" + agent.name() + "' (" + agent.code()
                + ") processed input (" + input.length() + " chars). "
                + "Recommendation: review the input and take appropriate action. "
                + "This is an advisory-only output — no business state was mutated.";

        var tokensIn = Math.max(1, input.length() / 4);  // rough token estimate
        var tokensOut = Math.max(1, output.length() / 4);
        var latency = System.currentTimeMillis() - start;

        return Result.success(output, tokensIn, tokensOut, latency, 0);
    }
}
