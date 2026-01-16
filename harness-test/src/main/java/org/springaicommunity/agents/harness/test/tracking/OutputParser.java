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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CLI output to extract tool calls and other metrics.
 *
 * <p>Supports parsing output from MiniAgent CLI which uses the format:
 * <pre>
 * Tool: toolName
 * Result: result text...
 * </pre>
 *
 * <p>This enables capturing tool calls when running agents through
 * the CLI without direct API access to ToolCallListener.
 */
public class OutputParser {

    // Pattern for LinearAgentCallback output: "Tool: toolName"
    private static final Pattern TOOL_LINE_PATTERN = Pattern.compile("^Tool:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern RESULT_LINE_PATTERN = Pattern.compile("^Result:\\s*(.+)$", Pattern.MULTILINE);

    // Pattern for LoggingToolCallListener output: "[toolName] {args}({context})"
    // Example: "LoggingToolCallListener --   [bash] {"arg0":"cat test-file.txt"}({})"
    // Note: Extra spaces before [toolName] in the log output
    private static final Pattern LOGGING_LISTENER_PATTERN = Pattern.compile(
        "LoggingToolCallListener\\s+--\\s+\\[([^\\]]+)\\]\\s+(.+?)\\(\\{\\}\\)\\s*$",
        Pattern.MULTILINE
    );

    // Pattern for LoggingToolCallListener completion: "[toolName] completed in Xms: result"
    // Example: "LoggingToolCallListener --   [bash] completed in 14ms: "<output>..."
    private static final Pattern LOGGING_COMPLETION_PATTERN = Pattern.compile(
        "LoggingToolCallListener\\s+--\\s+\\[([^\\]]+)\\]\\s+completed\\s+in\\s+(\\d+)ms:\\s+(.*)$",
        Pattern.MULTILINE
    );

    /**
     * Parses tool calls from CLI output.
     *
     * <p>Supports two formats:
     * <ul>
     *   <li>LinearAgentCallback: "Tool: toolName" followed by "Result: result"</li>
     *   <li>LoggingToolCallListener: "[toolName] args" followed by "[toolName] completed in Xms: result"</li>
     * </ul>
     *
     * @param output the CLI stdout/stderr output
     * @return list of tool call events extracted from output
     */
    public static List<ToolCallEvent> parseToolCalls(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }

        // Try LoggingToolCallListener format first (more common in MiniAgent)
        List<ToolCallEvent> loggingCalls = parseLoggingListenerFormat(output);
        if (!loggingCalls.isEmpty()) {
            return loggingCalls;
        }

        // Fall back to LinearAgentCallback format
        return parseLinearCallbackFormat(output);
    }

    /**
     * Parses LoggingToolCallListener format:
     * "[toolName] args" and "[toolName] completed in Xms: result"
     */
    private static List<ToolCallEvent> parseLoggingListenerFormat(String output) {
        List<ToolCallEvent> toolCalls = new ArrayList<>();

        // Find tool call starts: "[toolName] args"
        List<PendingToolCall> pending = new ArrayList<>();
        Matcher startMatcher = LOGGING_LISTENER_PATTERN.matcher(output);
        while (startMatcher.find()) {
            String toolName = startMatcher.group(1);
            String argsJson = startMatcher.group(2);
            Map<String, Object> input = parseJsonInput(argsJson);
            pending.add(new PendingToolCall(toolName, input));
        }

        // Find completions: "[toolName] completed in Xms: result"
        Matcher completionMatcher = LOGGING_COMPLETION_PATTERN.matcher(output);
        int completionIndex = 0;
        while (completionMatcher.find()) {
            String toolName = completionMatcher.group(1);
            String result = completionMatcher.group(3);

            // Match with pending call (order preserved)
            if (completionIndex < pending.size()) {
                PendingToolCall call = pending.get(completionIndex);
                toolCalls.add(ToolCallEvent.success(call.toolName, call.input, result));
                completionIndex++;
            } else {
                // No matching start, create with just the completion info
                toolCalls.add(ToolCallEvent.success(toolName, Map.of(), result));
            }
        }

        // Add any pending calls without completions
        for (int i = completionIndex; i < pending.size(); i++) {
            PendingToolCall call = pending.get(i);
            toolCalls.add(ToolCallEvent.success(call.toolName, call.input, null));
        }

        return toolCalls;
    }

    /**
     * Parses LinearAgentCallback format:
     * "Tool: toolName" followed by "Result: result"
     */
    private static List<ToolCallEvent> parseLinearCallbackFormat(String output) {
        List<ToolCallEvent> toolCalls = new ArrayList<>();
        String[] lines = output.split("\n");

        String pendingToolName = null;

        for (String line : lines) {
            // Check for Tool: line
            Matcher toolMatcher = TOOL_LINE_PATTERN.matcher(line);
            if (toolMatcher.matches()) {
                // If we had a pending tool without result, add it
                if (pendingToolName != null) {
                    toolCalls.add(ToolCallEvent.success(
                        pendingToolName,
                        Map.of(),  // No input captured from output
                        null       // No result captured
                    ));
                }
                pendingToolName = toolMatcher.group(1).trim();
                continue;
            }

            // Check for Result: line
            Matcher resultMatcher = RESULT_LINE_PATTERN.matcher(line);
            if (resultMatcher.matches() && pendingToolName != null) {
                String result = resultMatcher.group(1).trim();
                toolCalls.add(ToolCallEvent.success(
                    pendingToolName,
                    Map.of(),  // No input captured from output
                    result
                ));
                pendingToolName = null;
            }
        }

        // Handle any remaining pending tool
        if (pendingToolName != null) {
            toolCalls.add(ToolCallEvent.success(
                pendingToolName,
                Map.of(),
                null
            ));
        }

        return toolCalls;
    }

    /**
     * Attempts to parse JSON input from tool call arguments.
     * Falls back to empty map on parse errors.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonInput(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            // Simple JSON parsing - handle common patterns
            // Full JSON parsing would require a library
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                // Basic extraction of key-value pairs
                Map<String, Object> result = new java.util.HashMap<>();
                // Match "key":"value" patterns
                Pattern kvPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher kvMatcher = kvPattern.matcher(json);
                while (kvMatcher.find()) {
                    result.put(kvMatcher.group(1), kvMatcher.group(2));
                }
                return result;
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return Map.of();
    }

    private record PendingToolCall(String toolName, Map<String, Object> input) {}

    /**
     * Counts turns from CLI output.
     *
     * <p>Estimates turn count based on response patterns in output.
     * A turn is typically indicated by tool usage or response output.
     *
     * @param output the CLI stdout/stderr output
     * @return estimated number of turns
     */
    public static int countTurns(String output) {
        if (output == null || output.isEmpty()) {
            return 0;
        }

        int turns = 0;
        boolean sawToolInCurrentTurn = false;

        for (String line : output.split("\n")) {
            if (line.startsWith("Tool:")) {
                if (!sawToolInCurrentTurn) {
                    turns++;
                    sawToolInCurrentTurn = true;
                }
            } else if (line.startsWith("[Thinking...]")) {
                // New thinking phase might indicate a new turn
                sawToolInCurrentTurn = false;
            }
        }

        // At least 1 turn if there's any output
        return Math.max(1, turns);
    }
}
