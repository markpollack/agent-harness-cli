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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OutputParser - parsing tool calls from CLI output.
 */
class OutputParserTest {

    @Test
    @DisplayName("Should parse LoggingToolCallListener format")
    void parseLoggingListenerFormat() {
        String output = """
            22:56:45.060 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [bash] {"arg0":"cat test-file.txt"}({})
            22:56:45.070 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [bash] completed in 11ms: "<output>\\nTest content\\n</output>\\n<returncode>0</returncode>"
            22:56:47.418 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [submit] {"arg0":"The content of test-file.txt"}({})
            22:56:47.418 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [submit] completed in 0ms: "The content of test-file.txt"
            """;

        List<ToolCallEvent> toolCalls = OutputParser.parseToolCalls(output);

        assertThat(toolCalls).hasSize(2);

        // First tool call: bash
        assertThat(toolCalls.get(0).toolName()).isEqualTo("bash");
        assertThat(toolCalls.get(0).input()).containsEntry("arg0", "cat test-file.txt");
        assertThat(toolCalls.get(0).success()).isTrue();

        // Second tool call: submit
        assertThat(toolCalls.get(1).toolName()).isEqualTo("submit");
        assertThat(toolCalls.get(1).success()).isTrue();
    }

    @Test
    @DisplayName("Should parse LinearAgentCallback format")
    void parseLinearCallbackFormat() {
        String output = """
            Tool: bash
            Result: command output here
            Tool: write
            Result: file written
            """;

        List<ToolCallEvent> toolCalls = OutputParser.parseToolCalls(output);

        assertThat(toolCalls).hasSize(2);
        assertThat(toolCalls.get(0).toolName()).isEqualTo("bash");
        assertThat(toolCalls.get(1).toolName()).isEqualTo("write");
    }

    @Test
    @DisplayName("Should return empty list for null or empty output")
    void handleEmptyOutput() {
        assertThat(OutputParser.parseToolCalls(null)).isEmpty();
        assertThat(OutputParser.parseToolCalls("")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no tool calls in output")
    void handleNoToolCalls() {
        String output = """
            Some random log output
            MiniAgent starting: test task
            MiniAgent completed: 100 tokens
            """;

        List<ToolCallEvent> toolCalls = OutputParser.parseToolCalls(output);
        assertThat(toolCalls).isEmpty();
    }

    @Test
    @DisplayName("Should count turns correctly")
    void countTurns() {
        String output = """
            [Thinking...]
            Tool: bash
            Tool: read
            [Thinking...]
            Tool: write
            """;

        int turns = OutputParser.countTurns(output);
        assertThat(turns).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should prefer LoggingToolCallListener format over LinearAgentCallback")
    void preferLoggingFormat() {
        // When both formats are present, LoggingToolCallListener should be preferred
        String output = """
            22:56:45.060 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [bash] {"arg0":"cat file.txt"}({})
            22:56:45.070 [main] INFO org.springaicommunity.agents.harness.agents.mini.LoggingToolCallListener --   [bash] completed in 11ms: "output"
            Tool: oldformat
            Result: should be ignored
            """;

        List<ToolCallEvent> toolCalls = OutputParser.parseToolCalls(output);

        // Should only pick up the LoggingToolCallListener format
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).toolName()).isEqualTo("bash");
    }

    @Test
    @DisplayName("Should handle multiple tool calls in sequence")
    void handleMultipleToolCalls() {
        String output = """
            [main] INFO LoggingToolCallListener --   [read] {"arg0":"/path/to/file"}({})
            [main] INFO LoggingToolCallListener --   [read] completed in 5ms: "file content"
            [main] INFO LoggingToolCallListener --   [bash] {"arg0":"ls -la"}({})
            [main] INFO LoggingToolCallListener --   [bash] completed in 10ms: "directory listing"
            [main] INFO LoggingToolCallListener --   [write] {"arg0":"/output.txt"}({})
            [main] INFO LoggingToolCallListener --   [write] completed in 3ms: "written"
            """;

        List<ToolCallEvent> toolCalls = OutputParser.parseToolCalls(output);

        assertThat(toolCalls).hasSize(3);
        assertThat(toolCalls.get(0).toolName()).isEqualTo("read");
        assertThat(toolCalls.get(1).toolName()).isEqualTo("bash");
        assertThat(toolCalls.get(2).toolName()).isEqualTo("write");
    }
}
