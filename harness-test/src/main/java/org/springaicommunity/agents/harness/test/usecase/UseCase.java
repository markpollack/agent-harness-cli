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

package org.springaicommunity.agents.harness.test.usecase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Immutable use case model for AI-driven testing.
 *
 * <p>A use case defines:</p>
 * <ul>
 *   <li>Test identity: name, category, difficulty</li>
 *   <li>Environment setup: workspace, files to create</li>
 *   <li>Test execution: prompt, question handling strategy</li>
 *   <li>Validation: expected behavior, success criteria</li>
 *   <li>Constraints: max turns, timeout</li>
 * </ul>
 *
 * @param name use case display name
 * @param category test category (e.g., "basic", "bug-fix", "feature")
 * @param difficulty difficulty level: "easy", "medium", "hard"
 * @param setup workspace setup configuration
 * @param prompt initial prompt to send to the agent
 * @param questionStrategy strategy for answering agent questions
 * @param expectedBehavior description of expected agent behavior for LLM validation
 * @param successCriteria deterministic success criteria for validation
 * @param maxTurns maximum number of agent turns allowed
 * @param timeoutSeconds test timeout in seconds
 * @param requiresApi whether this test requires an LLM API key
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UseCase(
    String name,
    String category,
    String difficulty,
    Setup setup,
    String prompt,
    QuestionStrategy questionStrategy,
    String expectedBehavior,
    List<SuccessCriterion> successCriteria,
    int maxTurns,
    int timeoutSeconds,
    boolean requiresApi
) {

    public static final int DEFAULT_MAX_TURNS = 10;
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    public UseCase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        category = category != null ? category : "unknown";
        difficulty = difficulty != null ? difficulty : "unknown";
        successCriteria = successCriteria != null ? List.copyOf(successCriteria) : List.of();
        maxTurns = maxTurns > 0 ? maxTurns : DEFAULT_MAX_TURNS;
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Check if this is an interactive test requiring question handling.
     */
    public boolean isInteractive() {
        return questionStrategy != null;
    }

    /**
     * Check if this test has deterministic success criteria.
     */
    public boolean hasDeterministicCriteria() {
        return !successCriteria.isEmpty();
    }

    /**
     * Builder for creating UseCase instances programmatically.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String category;
        private String difficulty;
        private Setup setup;
        private String prompt;
        private QuestionStrategy questionStrategy;
        private String expectedBehavior;
        private List<SuccessCriterion> successCriteria;
        private int maxTurns;
        private int timeoutSeconds;
        private boolean requiresApi;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder difficulty(String difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder setup(Setup setup) {
            this.setup = setup;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder questionStrategy(QuestionStrategy questionStrategy) {
            this.questionStrategy = questionStrategy;
            return this;
        }

        public Builder expectedBehavior(String expectedBehavior) {
            this.expectedBehavior = expectedBehavior;
            return this;
        }

        public Builder successCriteria(List<SuccessCriterion> successCriteria) {
            this.successCriteria = successCriteria;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder requiresApi(boolean requiresApi) {
            this.requiresApi = requiresApi;
            return this;
        }

        public UseCase build() {
            return new UseCase(name, category, difficulty, setup, prompt, questionStrategy,
                    expectedBehavior, successCriteria, maxTurns, timeoutSeconds, requiresApi);
        }
    }

}
