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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DefaultCliExecutor.
 */
class DefaultCliExecutorTest {

    private DefaultCliExecutor executor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        executor = new DefaultCliExecutor();
    }

    @Test
    void executeSimpleCommand() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("echo", "hello")
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("hello");
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.durationMs()).isPositive();
    }

    @Test
    void executeWithWorkingDirectory() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content");

        var config = CliExecutor.ExecutionConfig.builder()
                .command("ls")
                .workingDirectory(tempDir)
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("test.txt");
    }

    @Test
    void executeWithInput() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("cat")
                .input("hello from stdin")
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).isEqualTo("hello from stdin");
    }

    @Test
    void executeWithFailingCommand() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("false") // Always exits with 1
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void executeWithTimeout() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("sleep", "10")
                .timeoutSeconds(1)
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void executeWithNonexistentCommand() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("nonexistent-command-12345")
                .build();

        ExecutionResult result = executor.execute(config);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
    }

    @Test
    void executionConfigBuilderDefaults() {
        var config = CliExecutor.ExecutionConfig.builder()
                .command("echo", "test")
                .build();

        assertThat(config.timeoutSeconds()).isEqualTo(120);
        assertThat(config.workingDirectory()).isNull();
        assertThat(config.input()).isNull();
    }

    @Test
    void executionConfigRequiresCommand() {
        assertThatThrownBy(() -> CliExecutor.ExecutionConfig.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

}
