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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.CliExecutor.ExecutionConfig;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.springaicommunity.agents.harness.test.usecase.QuestionStrategy;
import org.springaicommunity.agents.harness.test.usecase.SuccessCriterion;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;
import org.springaicommunity.agents.harness.test.validation.JuryFactory;
import org.springaicommunity.agents.harness.test.validation.TestJudgmentAdapter;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TestHarness.
 */
class TestHarnessTest {

    @TempDir
    Path tempDir;

    private TestHarnessConfig config;
    private MockCliExecutor mockExecutor;
    private TestHarness harness;

    @BeforeEach
    void setUp() {
        config = TestHarnessConfig.builder()
                .cliCommand("echo", "test")
                .useCasesDir(tempDir.resolve("use-cases"))
                .transcriptsDir(tempDir.resolve("transcripts"))
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .build();

        mockExecutor = new MockCliExecutor();

        harness = TestHarness.builder()
                .config(config)
                .executor(mockExecutor)
                .workspaceManager(new WorkspaceManager())
                .useCaseLoader(new UseCaseLoader())
                .juryFactory(new JuryFactory())
                .judgmentAdapter(new TestJudgmentAdapter())
                .build();
    }

    @Test
    void runSimpleUseCase() {
        UseCase useCase = UseCase.builder()
                .name("Simple Test")
                .prompt("Hello")
                .build();

        mockExecutor.setResult(ExecutionResult.success("Hello response", 100));

        TestResult result = harness.run(useCase);

        assertThat(result.useCaseName()).isEqualTo("Simple Test");
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo("PASSED");
    }

    @Test
    void runUseCaseWithSuccessCriteria() throws IOException {
        // Create a file that will be checked by the criterion
        Path workspaceFile = tempDir.resolve("test-output.txt");
        Files.writeString(workspaceFile, "expected content");

        UseCase useCase = UseCase.builder()
                .name("File Check Test")
                .prompt("Create file")
                .successCriteria(List.of(SuccessCriterion.outputContains("success")))
                .build();

        mockExecutor.setResult(ExecutionResult.success("Task completed with success", 200));

        TestResult result = harness.run(useCase);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void runUseCaseWithTimeout() {
        UseCase useCase = UseCase.builder()
                .name("Timeout Test")
                .prompt("Long running task")
                .timeoutSeconds(5)
                .build();

        mockExecutor.setResult(ExecutionResult.timeout("Partial output...", 5000));

        TestResult result = harness.run(useCase);

        assertThat(result.passed()).isFalse();
        assertThat(result.status()).isEqualTo("TIMEOUT");
    }

    @Test
    void runUseCaseWithFailedCriteria() {
        UseCase useCase = UseCase.builder()
                .name("Failing Test")
                .prompt("Do something")
                .successCriteria(List.of(SuccessCriterion.outputContains("expected-but-missing")))
                .build();

        mockExecutor.setResult(ExecutionResult.success("Some other output", 100));

        TestResult result = harness.run(useCase);

        assertThat(result.passed()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void buildInputWithPromptOnly() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Hello world")
                .build();

        String input = harness.buildInput(useCase);

        assertThat(input).isEqualTo("Hello world");
    }

    @Test
    void buildInputWithQuestionStrategy() {
        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("Initial prompt")
                .questionStrategy(QuestionStrategy.first())
                .build();

        String input = harness.buildInput(useCase);

        // QuestionStrategy handles dynamic answer selection, not predefined answers
        // The prompt is still just the initial prompt
        assertThat(input).isEqualTo("Initial prompt");
    }

    @Test
    void countTurnsFromOutput() {
        String output = """
                > First prompt
                Response 1
                > Second prompt
                Response 2
                > Third prompt
                Response 3
                """;

        int turns = harness.countTurns(output);

        assertThat(turns).isEqualTo(3);
    }

    @Test
    void countTurnsWithEmptyOutput() {
        assertThat(harness.countTurns("")).isEqualTo(0);
        assertThat(harness.countTurns(null)).isEqualTo(0);
    }

    @Test
    void sanitizeFilenameRemovesSpecialChars() {
        assertThat(harness.sanitizeFilename("Simple Test")).isEqualTo("simple-test");
        assertThat(harness.sanitizeFilename("Test/With\\Slashes")).isEqualTo("test-with-slashes");
        assertThat(harness.sanitizeFilename("Test   Multiple   Spaces")).isEqualTo("test-multiple-spaces");
        assertThat(harness.sanitizeFilename("Test!@#$%^&*()")).isEqualTo("test");
    }

    @Test
    void saveTranscriptCreatesFile() throws IOException {
        UseCase useCase = UseCase.builder()
                .name("Transcript Test")
                .prompt("prompt")
                .build();

        Path transcriptPath = harness.saveTranscript(useCase, "Test output content");

        assertThat(transcriptPath).isNotNull();
        assertThat(Files.exists(transcriptPath)).isTrue();
        assertThat(Files.readString(transcriptPath)).isEqualTo("Test output content");
    }

    @Test
    void saveTranscriptReturnsNullWhenDisabled() throws IOException {
        TestHarnessConfig noTranscriptConfig = TestHarnessConfig.builder()
                .cliCommand("echo")
                .saveTranscripts(false)
                .build();

        TestHarness noTranscriptHarness = TestHarness.builder()
                .config(noTranscriptConfig)
                .executor(mockExecutor)
                .build();

        UseCase useCase = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .build();

        Path transcriptPath = noTranscriptHarness.saveTranscript(useCase, "output");

        assertThat(transcriptPath).isNull();
    }

    @Test
    void buildExecutionConfigUsesCorrectTimeout() {
        UseCase useCaseWithTimeout = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .timeoutSeconds(30)
                .build();

        var workspace = new org.springaicommunity.agents.harness.test.workspace.WorkspaceContext(
                tempDir, true, List.of(), useCaseWithTimeout);

        ExecutionConfig execConfig = harness.buildExecutionConfig(useCaseWithTimeout, workspace);

        assertThat(execConfig.timeoutSeconds()).isEqualTo(30);
    }

    @Test
    void buildExecutionConfigUsesDefaultTimeout() {
        UseCase useCaseNoTimeout = UseCase.builder()
                .name("Test")
                .prompt("prompt")
                .build();

        var workspace = new org.springaicommunity.agents.harness.test.workspace.WorkspaceContext(
                tempDir, true, List.of(), useCaseNoTimeout);

        ExecutionConfig execConfig = harness.buildExecutionConfig(useCaseNoTimeout, workspace);

        assertThat(execConfig.timeoutSeconds()).isEqualTo(config.defaultTimeoutSeconds());
    }

    @Test
    void builderRequiresConfig() {
        assertThatThrownBy(() -> TestHarness.builder().executor(mockExecutor).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("config is required");
    }

    @Test
    void builderRequiresExecutor() {
        assertThatThrownBy(() -> TestHarness.builder().config(config).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("executor is required");
    }

    @Test
    void runAllThrowsWhenUseCasesDirNotConfigured() {
        TestHarnessConfig noUseCasesConfig = TestHarnessConfig.builder()
                .cliCommand("echo")
                .build();

        TestHarness noUseCasesHarness = TestHarness.builder()
                .config(noUseCasesConfig)
                .executor(mockExecutor)
                .build();

        assertThatThrownBy(noUseCasesHarness::runAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("useCasesDir not configured");
    }

    /**
     * Mock CLI executor for testing.
     */
    static class MockCliExecutor implements CliExecutor {
        private ExecutionResult result = ExecutionResult.success("", 0);
        private ExecutionConfig lastConfig;

        void setResult(ExecutionResult result) {
            this.result = result;
        }

        ExecutionConfig lastConfig() {
            return lastConfig;
        }

        @Override
        public ExecutionResult execute(ExecutionConfig config) {
            this.lastConfig = config;
            return result;
        }
    }

}
