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

package org.springaicommunity.agents.harness.test;

import org.springaicommunity.agents.harness.test.validation.TestJudgmentAdapter.ValidationResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of a test harness execution.
 *
 * @param useCaseName name of the use case that was tested
 * @param passed whether the test passed
 * @param status status description (COMPLETED, TIMEOUT, ERROR, etc.)
 * @param turnsUsed number of agent turns executed
 * @param durationMs total test duration in milliseconds
 * @param validation validation result from jury evaluation
 * @param transcriptFile path to the saved transcript file (if any)
 */
public record TestResult(
    String useCaseName,
    boolean passed,
    String status,
    int turnsUsed,
    long durationMs,
    ValidationResult validation,
    Path transcriptFile
) {

    /**
     * Create a successful test result.
     */
    public static TestResult success(String useCaseName, ValidationResult validation,
                                     int turnsUsed, long durationMs, Path transcriptFile) {
        return new TestResult(
                useCaseName,
                true,
                "PASSED",
                turnsUsed,
                durationMs,
                validation,
                transcriptFile
        );
    }

    /**
     * Create a failed test result.
     */
    public static TestResult failed(String useCaseName, ValidationResult validation,
                                    int turnsUsed, long durationMs, Path transcriptFile) {
        return new TestResult(
                useCaseName,
                false,
                "FAILED",
                turnsUsed,
                durationMs,
                validation,
                transcriptFile
        );
    }

    /**
     * Create a result based on validation.
     */
    public static TestResult fromValidation(String useCaseName, ValidationResult validation,
                                            int turnsUsed, long durationMs, Path transcriptFile) {
        return new TestResult(
                useCaseName,
                validation.passed(),
                validation.passed() ? "PASSED" : "FAILED",
                turnsUsed,
                durationMs,
                validation,
                transcriptFile
        );
    }

    /**
     * Create an error result.
     */
    public static TestResult error(String useCaseName, String errorMessage, long durationMs) {
        return new TestResult(
                useCaseName,
                false,
                "ERROR: " + errorMessage,
                0,
                durationMs,
                null,
                null
        );
    }

    /**
     * Create a timeout result.
     */
    public static TestResult timeout(String useCaseName, long durationMs) {
        return new TestResult(
                useCaseName,
                false,
                "TIMEOUT",
                0,
                durationMs,
                null,
                null
        );
    }

    /**
     * Get issues from validation (if available).
     */
    public List<String> issues() {
        return validation != null ? validation.issues() : List.of();
    }

    /**
     * Get reasoning from validation (if available).
     */
    public String reasoning() {
        return validation != null ? validation.reasoning() : status;
    }

}
