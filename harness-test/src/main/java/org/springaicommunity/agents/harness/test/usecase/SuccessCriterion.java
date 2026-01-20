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
 * Success criterion for deterministic test validation.
 * These criteria are used to build deterministic judges in the JuryFactory.
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>{@code file_exists} - Check if a file exists (args: [path])</li>
 *   <li>{@code file_contains} - Check if file contains text (args: [path, text])</li>
 *   <li>{@code command_succeeds} - Check if command exits with 0 (args: [command...])</li>
 *   <li>{@code no_exceptions} - Check transcript contains no exceptions (no args)</li>
 *   <li>{@code output_contains} - Check output contains text (args: [text])</li>
 *   <li>{@code compiles} - Check Java files compile with javac (args: [pattern] optional)</li>
 * </ul>
 *
 * @param type criterion type name
 * @param args type-specific arguments
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuccessCriterion(
    String type,
    List<String> args
) {

    public static final String TYPE_FILE_EXISTS = "file_exists";
    public static final String TYPE_FILE_CONTAINS = "file_contains";
    public static final String TYPE_COMMAND_SUCCEEDS = "command_succeeds";
    public static final String TYPE_NO_EXCEPTIONS = "no_exceptions";
    public static final String TYPE_OUTPUT_CONTAINS = "output_contains";
    public static final String TYPE_COMPILES = "compiles";

    public SuccessCriterion {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        args = args != null ? List.copyOf(args) : List.of();
    }

    /**
     * Create a file_exists criterion.
     */
    public static SuccessCriterion fileExists(String path) {
        return new SuccessCriterion(TYPE_FILE_EXISTS, List.of(path));
    }

    /**
     * Create a file_contains criterion.
     */
    public static SuccessCriterion fileContains(String path, String text) {
        return new SuccessCriterion(TYPE_FILE_CONTAINS, List.of(path, text));
    }

    /**
     * Create a command_succeeds criterion.
     */
    public static SuccessCriterion commandSucceeds(String... command) {
        return new SuccessCriterion(TYPE_COMMAND_SUCCEEDS, List.of(command));
    }

    /**
     * Create a no_exceptions criterion.
     */
    public static SuccessCriterion noExceptions() {
        return new SuccessCriterion(TYPE_NO_EXCEPTIONS, List.of());
    }

    /**
     * Create an output_contains criterion.
     */
    public static SuccessCriterion outputContains(String text) {
        return new SuccessCriterion(TYPE_OUTPUT_CONTAINS, List.of(text));
    }

    /**
     * Create a compiles criterion for Java files.
     *
     * @param pattern optional glob pattern (defaults to all .java files)
     */
    public static SuccessCriterion compiles(String pattern) {
        return pattern != null
                ? new SuccessCriterion(TYPE_COMPILES, List.of(pattern))
                : new SuccessCriterion(TYPE_COMPILES, List.of());
    }

    /**
     * Create a compiles criterion for all Java files.
     */
    public static SuccessCriterion compiles() {
        return compiles(null);
    }

}
