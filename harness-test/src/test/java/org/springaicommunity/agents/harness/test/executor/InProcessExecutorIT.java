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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for InProcessExecutor.
 *
 * <p>These tests require ANTHROPIC_API_KEY environment variable to be set.
 * They run actual MiniAgent tasks and verify tool call capture.</p>
 *
 * <p>Run with: {@code ./mvnw verify -pl harness-test -Dit.test=InProcessExecutorIT}</p>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class InProcessExecutorIT {

    @TempDir
    Path tempDir;

    private ChatModel chatModel;
    private InProcessExecutor executor;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        AnthropicApi api = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model("claude-sonnet-4-20250514")
                .maxTokens(1024)
                .build();

        chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();

        executor = new InProcessExecutor(chatModel, 5); // Low turn limit for testing
    }

    @Test
    void executorCapturesToolCallsForReadTask() throws IOException {
        // Setup: Create a file to read
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello from the test file!");

        // Execute: Ask agent to read the file
        CliExecutor.ExecutionConfig config = CliExecutor.ExecutionConfig.builder()
                .command("dummy") // Not used by InProcessExecutor
                .workingDirectory(tempDir)
                .input("Read the file test.txt and tell me what it says. Use the Submit tool when done.")
                .timeoutSeconds(60)
                .build();

        ExecutionResult result = executor.execute(config);

        // Verify: Tool calls were captured
        List<ToolCallRecord> toolCalls = executor.getToolCalls();

        assertThat(toolCalls).isNotEmpty();
        assertThat(toolCalls.stream().map(ToolCallRecord::toolName))
                .contains("Read"); // Should have used Read tool

        // Verify output mentions the file content
        assertThat(result.output()).containsIgnoringCase("hello");
    }

    @Test
    void executorCapturesToolCallDuration() throws IOException {
        // Setup: Create a file
        Path testFile = tempDir.resolve("data.txt");
        Files.writeString(testFile, "Test data");

        // Execute
        CliExecutor.ExecutionConfig config = CliExecutor.ExecutionConfig.builder()
                .command("dummy")
                .workingDirectory(tempDir)
                .input("Read data.txt and submit its contents.")
                .timeoutSeconds(60)
                .build();

        executor.execute(config);

        // Verify: Duration is captured
        List<ToolCallRecord> toolCalls = executor.getToolCalls();
        assertThat(toolCalls).isNotEmpty();

        for (ToolCallRecord record : toolCalls) {
            assertThat(record.duration()).isNotNull();
            assertThat(record.duration().toMillis()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void executorHandlesSimpleTask() {
        // Execute: Simple task that doesn't require file access
        CliExecutor.ExecutionConfig config = CliExecutor.ExecutionConfig.builder()
                .command("dummy")
                .workingDirectory(tempDir)
                .input("What is 2 + 2? Submit your answer using the Submit tool.")
                .timeoutSeconds(30)
                .build();

        ExecutionResult result = executor.execute(config);

        // Should complete successfully
        assertThat(result.output()).isNotNull();
        assertThat(result.output()).containsIgnoringCase("4");
    }

}
