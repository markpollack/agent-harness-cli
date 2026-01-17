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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;

import java.time.Duration;
import java.util.Map;

/**
 * Record capturing a tool call execution.
 *
 * <p>This is a minimal representation of a tool call event, capturing
 * only the essential data needed for test validation. For richer tracking
 * (tokens, costs, metrics), use the commercial tuvium integration.</p>
 *
 * @param toolName the name of the tool that was called
 * @param input the input arguments to the tool
 * @param output the result from the tool (null if failed)
 * @param duration how long the tool execution took
 * @param success whether the tool execution succeeded
 */
public record ToolCallRecord(
    String toolName,
    String input,
    String output,
    Duration duration,
    boolean success
) {

    /**
     * Create a successful tool call record.
     */
    public static ToolCallRecord success(String toolName, String input, String output, Duration duration) {
        return new ToolCallRecord(toolName, input, output, duration, true);
    }

    /**
     * Create a failed tool call record.
     */
    public static ToolCallRecord failure(String toolName, String input, Duration duration) {
        return new ToolCallRecord(toolName, input, null, duration, false);
    }

    /**
     * Convert this record to a ToolCallEvent for use with ExecutionSummary.
     *
     * @return equivalent ToolCallEvent
     */
    public ToolCallEvent toToolCallEvent() {
        Map<String, Object> inputMap = parseInputToMap(input);
        return new ToolCallEvent(toolName, inputMap, output, success);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> parseInputToMap(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(input, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // If not valid JSON, wrap as single "input" key
            return Map.of("input", input);
        }
    }

}
