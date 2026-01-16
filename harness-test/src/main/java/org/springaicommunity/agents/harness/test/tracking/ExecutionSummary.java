/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springaicommunity.agents.harness.test.tracking;

import java.util.List;

/**
 * Summary of an agent execution including tool calls and token usage.
 *
 * <p>This record captures the essential metrics for comparing executions
 * between different agents (MiniAgent vs Claude Code).
 *
 * @param agentId identifier for the agent ("MiniAgent" or "ClaudeCode")
 * @param toolCalls ordered list of tool calls made during execution
 * @param inputTokens number of input tokens used
 * @param outputTokens number of output tokens generated
 * @param thinkingTokens number of thinking tokens used (critical for comparison)
 * @param numTurns number of LLM turns in the execution
 * @param success whether the execution completed successfully
 * @param durationMs execution duration in milliseconds
 */
public record ExecutionSummary(
    String agentId,
    List<ToolCallEvent> toolCalls,
    int inputTokens,
    int outputTokens,
    int thinkingTokens,
    int numTurns,
    boolean success,
    long durationMs
) {

    /**
     * Gets the total number of tokens used.
     */
    public int totalTokens() {
        return inputTokens + outputTokens + thinkingTokens;
    }

    /**
     * Gets the count of tool calls.
     */
    public int toolCallCount() {
        return toolCalls != null ? toolCalls.size() : 0;
    }

    /**
     * Gets the sequence of tool names called.
     */
    public List<String> toolSequence() {
        if (toolCalls == null) {
            return List.of();
        }
        return toolCalls.stream()
            .map(ToolCallEvent::toolName)
            .toList();
    }

    /**
     * Prints a formatted summary of the execution.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s in %dms, %d turns%n",
            agentId, success ? "SUCCESS" : "FAILED", durationMs, numTurns));
        sb.append(String.format("  Tokens: input=%d, output=%d, thinking=%d (total=%d)%n",
            inputTokens, outputTokens, thinkingTokens, totalTokens()));
        sb.append(String.format("  Tool calls: %d%n", toolCallCount()));
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCallEvent tc = toolCalls.get(i);
                sb.append(String.format("    %d. %s: %s%n",
                    i + 1, tc.toolName(), tc.inputSummary()));
            }
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private List<ToolCallEvent> toolCalls = List.of();
        private int inputTokens;
        private int outputTokens;
        private int thinkingTokens;
        private int numTurns;
        private boolean success;
        private long durationMs;

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder toolCalls(List<ToolCallEvent> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder thinkingTokens(int thinkingTokens) {
            this.thinkingTokens = thinkingTokens;
            return this;
        }

        public Builder numTurns(int numTurns) {
            this.numTurns = numTurns;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public ExecutionSummary build() {
            return new ExecutionSummary(agentId, toolCalls, inputTokens, outputTokens,
                thinkingTokens, numTurns, success, durationMs);
        }
    }
}
