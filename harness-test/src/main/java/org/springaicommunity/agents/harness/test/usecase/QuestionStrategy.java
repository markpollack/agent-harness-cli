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

import java.util.Map;

/**
 * Strategy for answering agent questions during interactive testing.
 *
 * @param defaultStrategy default answer strategy: "first", "last", or "random"
 * @param overrides keyword-based answer overrides (e.g., "approach" -> "defensive")
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionStrategy(
    String defaultStrategy,
    Map<String, String> overrides
) {

    public static final String STRATEGY_FIRST = "first";
    public static final String STRATEGY_LAST = "last";
    public static final String STRATEGY_RANDOM = "random";

    public QuestionStrategy {
        defaultStrategy = defaultStrategy != null ? defaultStrategy : STRATEGY_FIRST;
        overrides = overrides != null ? Map.copyOf(overrides) : Map.of();
    }

    /**
     * Create a default strategy that always selects the first option.
     */
    public static QuestionStrategy first() {
        return new QuestionStrategy(STRATEGY_FIRST, null);
    }

    /**
     * Create a strategy that always selects the last option.
     */
    public static QuestionStrategy last() {
        return new QuestionStrategy(STRATEGY_LAST, null);
    }

    /**
     * Select an answer for a given question line.
     *
     * @param questionLine the question text from the agent
     * @return the selected answer (typically "1", "2", etc.)
     */
    public String selectAnswer(String questionLine) {
        if (overrides != null && questionLine != null) {
            for (var entry : overrides.entrySet()) {
                if (questionLine.toLowerCase().contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }

        return switch (defaultStrategy) {
            case STRATEGY_LAST -> "9"; // Usually won't have 9 options
            case STRATEGY_RANDOM -> String.valueOf((int) (Math.random() * 4) + 1);
            default -> "1";
        };
    }

}
