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

package org.springaicommunity.agents.harness.test.executor;

/**
 * Result of CLI execution.
 *
 * @param output the captured stdout/stderr output
 * @param exitCode process exit code (0 = success)
 * @param durationMs execution duration in milliseconds
 * @param timedOut whether execution was terminated due to timeout
 */
public record ExecutionResult(
    String output,
    int exitCode,
    long durationMs,
    boolean timedOut
) {

    /**
     * Check if execution was successful (exit code 0, not timed out).
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * Create a successful result.
     */
    public static ExecutionResult success(String output, long durationMs) {
        return new ExecutionResult(output, 0, durationMs, false);
    }

    /**
     * Create a failed result with exit code.
     */
    public static ExecutionResult failed(String output, int exitCode, long durationMs) {
        return new ExecutionResult(output, exitCode, durationMs, false);
    }

    /**
     * Create a timeout result.
     */
    public static ExecutionResult timeout(String partialOutput, long durationMs) {
        return new ExecutionResult(partialOutput, -1, durationMs, true);
    }

}
