package com.sanad.platform.ai.domain;

import java.util.Map;

/**
 * AI Provider — abstraction for AI inference execution.
 *
 * <p>Implementations may use:
 * <ul>
 *   <li>Deterministic rules (default, always available, zero-cost)</li>
 *   <li>External AI providers (OpenAI, Anthropic, etc. — when configured)</li>
 * </ul>
 *
 * <p>ALL providers MUST produce advisory-only output. No provider
 * implementation may mutate business state. The provider returns a
 * {@link Result} containing the output summary and metadata — the caller
 * is responsible for persisting it via {@link com.sanad.platform.ai.domain.AiInferenceRepository}.
 */
public interface AiProvider {

    /** Provider name (e.g., 'deterministic', 'openai', 'anthropic'). */
    String providerName();

    /**
     * Execute an AI inference.
     *
     * @param agent  the agent definition (system prompt, model config, etc.)
     * @param input  the input text/prompt
     * @return the result containing output, token counts, latency, and cost
     */
    Result execute(AiAgent agent, String input);

    /** Result of an AI inference execution. */
    record Result(
            String outputSummary,
            String outputHash,
            Integer tokensInput,
            Integer tokensOutput,
            long latencyMs,
            int costCents,
            boolean success,
            String errorMessage
    ) {
        public static Result success(String output, Integer tokensIn, Integer tokensOut,
                                      long latencyMs, int costCents) {
            return new Result(
                    output,
                    output != null ? Integer.toHexString(output.hashCode()) : null,
                    tokensIn, tokensOut, latencyMs, costCents,
                    true, null
            );
        }

        public static Result failure(String errorMessage, long latencyMs) {
            return new Result(null, null, null, null, latencyMs, 0, false, errorMessage);
        }
    }
}
