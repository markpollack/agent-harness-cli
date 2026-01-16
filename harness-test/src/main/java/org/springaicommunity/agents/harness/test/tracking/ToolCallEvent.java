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

import java.util.Map;

/**
 * Records a single tool call during agent execution.
 *
 * <p>Captures the essential information needed for comparison:
 * <ul>
 *   <li>Which tool was called</li>
 *   <li>What input was provided</li>
 *   <li>What output was returned</li>
 *   <li>Whether it succeeded</li>
 * </ul>
 *
 * @param toolName the name of the tool that was called
 * @param input the input parameters passed to the tool
 * @param output the output returned by the tool (may be truncated)
 * @param success whether the tool call succeeded
 */
public record ToolCallEvent(
    String toolName,
    Map<String, Object> input,
    Object output,
    boolean success
) {

    /**
     * Creates a successful tool call event.
     */
    public static ToolCallEvent success(String toolName, Map<String, Object> input, Object output) {
        return new ToolCallEvent(toolName, input, output, true);
    }

    /**
     * Creates a failed tool call event.
     */
    public static ToolCallEvent failed(String toolName, Map<String, Object> input, Object output) {
        return new ToolCallEvent(toolName, input, output, false);
    }

    /**
     * Gets a summary of the input for display purposes.
     */
    public String inputSummary() {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        // For common tools, show key parameter
        if (input.containsKey("file_path")) {
            return "file_path: " + input.get("file_path");
        }
        if (input.containsKey("command")) {
            String cmd = String.valueOf(input.get("command"));
            return "command: " + (cmd.length() > 50 ? cmd.substring(0, 50) + "..." : cmd);
        }
        if (input.containsKey("pattern")) {
            return "pattern: " + input.get("pattern");
        }
        return input.toString().substring(0, Math.min(100, input.toString().length()));
    }
}
