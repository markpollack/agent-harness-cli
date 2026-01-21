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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.CliExecutor.ExecutionConfig;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;
import org.springaicommunity.agents.harness.test.validation.JuryFactory;
import org.springaicommunity.agents.harness.test.validation.TestJudgmentAdapter;
import org.springaicommunity.agents.harness.test.validation.TestJudgmentAdapter.ValidationResult;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceContext;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceManager;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.jury.Jury;
import org.springaicommunity.judge.jury.Verdict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Main facade for AI-driven test execution.
 *
 * <p>Orchestrates test execution by coordinating:</p>
 * <ul>
 *   <li>Use case loading and parsing</li>
 *   <li>Workspace setup and teardown</li>
 *   <li>CLI execution with input piping</li>
 *   <li>Validation via Judge/Jury framework</li>
 *   <li>Result collection and transcript saving</li>
 * </ul>
 *
 * <p>Inspired by Gemini CLI's TestRig pattern for reliable, repeatable test execution.
 */
public class TestHarness {

    private static final Logger logger = LoggerFactory.getLogger(TestHarness.class);

    private final TestHarnessConfig config;
    private final CliExecutor executor;
    private final WorkspaceManager workspaceManager;
    private final UseCaseLoader useCaseLoader;
    private final JuryFactory juryFactory;
    private final TestJudgmentAdapter judgmentAdapter;

    /**
     * Create a TestHarness with all dependencies injected.
     *
     * @param config harness configuration
     * @param executor CLI executor
     * @param workspaceManager workspace manager
     * @param useCaseLoader use case loader
     * @param juryFactory jury factory for building validators
     * @param judgmentAdapter adapter for converting verdicts
     */
    public TestHarness(
            TestHarnessConfig config,
            CliExecutor executor,
            WorkspaceManager workspaceManager,
            UseCaseLoader useCaseLoader,
            JuryFactory juryFactory,
            TestJudgmentAdapter judgmentAdapter) {
        this.config = config;
        this.executor = executor;
        this.workspaceManager = workspaceManager;
        this.useCaseLoader = useCaseLoader;
        this.juryFactory = juryFactory;
        this.judgmentAdapter = judgmentAdapter;
    }

    /**
     * Run a single use case.
     *
     * @param useCase the use case to run
     * @return test result
     */
    public TestResult run(UseCase useCase) {
        logger.info("Running use case: {}", useCase.name());
        long startTime = System.currentTimeMillis();

        WorkspaceContext workspace = null;
        try {
            // Setup workspace
            workspace = workspaceManager.setup(useCase);
            logger.debug("Workspace created: {}", workspace.workspacePath());

            // Build execution command with prompt as input
            ExecutionConfig execConfig = buildExecutionConfig(useCase, workspace);

            // Execute CLI
            ExecutionResult execResult = executor.execute(execConfig);
            logger.debug("Execution completed: exitCode={}, duration={}ms",
                    execResult.exitCode(), execResult.durationMs());

            // Check for timeout
            if (execResult.timedOut()) {
                return TestResult.timeout(useCase.name(), execResult.durationMs());
            }

            // Validate using Jury
            ValidationResult validation = validate(useCase, workspace, execResult);

            // Calculate total duration
            long durationMs = System.currentTimeMillis() - startTime;

            // Save transcript if configured
            Path transcriptFile = saveTranscript(useCase, execResult.output());

            // Build result
            return TestResult.fromValidation(
                    useCase.name(),
                    validation,
                    countTurns(execResult.output()),
                    durationMs,
                    transcriptFile
            );

        } catch (IOException e) {
            logger.error("Error running use case {}: {}", useCase.name(), e.getMessage(), e);
            long durationMs = System.currentTimeMillis() - startTime;
            return TestResult.error(useCase.name(), e.getMessage(), durationMs);

        } finally {
            // Cleanup workspace if configured
            if (config.cleanupWorkspaces() && workspace != null) {
                workspaceManager.cleanup(workspace);
            }
        }
    }

    /**
     * Run a use case from a YAML file path.
     *
     * @param useCasePath path to YAML file
     * @return test result
     * @throws IOException if the file cannot be loaded
     */
    public TestResult run(Path useCasePath) throws IOException {
        UseCase useCase = useCaseLoader.load(useCasePath);
        return run(useCase);
    }

    /**
     * Run all use cases from the configured directory.
     *
     * @return list of test results
     * @throws IOException if use cases cannot be loaded
     */
    public List<TestResult> runAll() throws IOException {
        if (config.useCasesDir() == null) {
            throw new IllegalStateException("useCasesDir not configured");
        }
        return runAll(config.useCasesDir());
    }

    /**
     * Run all use cases from a directory.
     *
     * @param directory directory containing use case YAML files
     * @return list of test results
     * @throws IOException if use cases cannot be loaded
     */
    public List<TestResult> runAll(Path directory) throws IOException {
        List<Path> useCasePaths = useCaseLoader.findUseCases(directory);
        logger.info("Found {} use cases in {}", useCasePaths.size(), directory);

        List<TestResult> results = new ArrayList<>();
        for (Path path : useCasePaths) {
            try {
                results.add(run(path));
            } catch (IOException e) {
                logger.error("Failed to load use case: {}", path, e);
                results.add(TestResult.error(path.getFileName().toString(), e.getMessage(), 0));
            }
        }
        return results;
    }

    /**
     * Run use cases matching a specific category.
     *
     * @param category category to filter by
     * @return list of test results
     * @throws IOException if use cases cannot be loaded
     */
    public List<TestResult> runCategory(String category) throws IOException {
        if (config.useCasesDir() == null) {
            throw new IllegalStateException("useCasesDir not configured");
        }

        List<Path> useCasePaths = useCaseLoader.findUseCasesByCategory(config.useCasesDir(), category);
        logger.info("Found {} use cases in category '{}'", useCasePaths.size(), category);

        List<TestResult> results = new ArrayList<>();
        for (Path path : useCasePaths) {
            results.add(run(path));
        }
        return results;
    }

    /**
     * Build execution configuration for a use case.
     */
    ExecutionConfig buildExecutionConfig(UseCase useCase, WorkspaceContext workspace) {
        // Build input: prompt followed by any configured answers
        String input = buildInput(useCase);

        int timeout = useCase.timeoutSeconds() > 0
                ? useCase.timeoutSeconds()
                : config.defaultTimeoutSeconds();

        return ExecutionConfig.builder()
                .command(config.cliCommand())
                .workingDirectory(workspace.workspacePath())
                .input(input)
                .timeoutSeconds(timeout)
                .build();
    }

    /**
     * Build the input string for CLI execution.
     *
     * <p>Note: The QuestionStrategy is designed for dynamic answer selection based on
     * question content. For simple cases, just the prompt is sent. Interactive question
     * handling would require more sophisticated stdin/stdout processing.
     */
    String buildInput(UseCase useCase) {
        // For now, just return the prompt
        // Interactive question handling requires real-time stdin/stdout processing
        // which is handled by the CLI executor
        return useCase.prompt();
    }

    /**
     * Validate execution result using Jury.
     */
    ValidationResult validate(UseCase useCase, WorkspaceContext workspace, ExecutionResult execResult) {
        Jury jury = juryFactory.buildJury(useCase, workspace.workspacePath());

        JudgmentContext context = JudgmentContext.builder()
                .goal(useCase.expectedBehavior() != null ? useCase.expectedBehavior() : useCase.prompt())
                .workspace(workspace.workspacePath())
                .agentOutput(execResult.output())
                .executionTime(Duration.ofMillis(execResult.durationMs()))
                .startedAt(Instant.now().minusMillis(execResult.durationMs()))
                .status(execResult.isSuccess() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED)
                .build();

        Verdict verdict = jury.vote(context);
        return judgmentAdapter.adapt(verdict);
    }

    /**
     * Save transcript to file if configured.
     */
    Path saveTranscript(UseCase useCase, String output) throws IOException {
        if (!config.saveTranscripts() || config.transcriptsDir() == null) {
            return null;
        }

        // Handle null or empty output gracefully
        if (output == null || output.isEmpty()) {
            logger.warn("No output to save for use case: {}", useCase.name());
            return null;
        }

        Files.createDirectories(config.transcriptsDir());

        String filename = sanitizeFilename(useCase.name()) + ".txt";
        Path transcriptPath = config.transcriptsDir().resolve(filename);

        Files.writeString(transcriptPath, output);
        logger.debug("Saved transcript to: {}", transcriptPath);

        return transcriptPath;
    }

    /**
     * Count agent turns from output (heuristic based on prompt markers).
     */
    int countTurns(String output) {
        if (output == null || output.isEmpty()) {
            return 0;
        }
        // Count occurrences of typical turn markers
        // This is a simple heuristic - real implementation may need CLI-specific parsing
        int turns = 0;
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains(">") || line.contains("claude>") || line.startsWith("$")) {
                turns++;
            }
        }
        return Math.max(1, turns);
    }

    /**
     * Sanitize filename for transcript saving.
     */
    String sanitizeFilename(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Get the configuration.
     */
    public TestHarnessConfig config() {
        return config;
    }

    /**
     * Builder for creating TestHarness instances with default components.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TestHarnessConfig config;
        private CliExecutor executor;
        private WorkspaceManager workspaceManager;
        private UseCaseLoader useCaseLoader;
        private JuryFactory juryFactory;
        private TestJudgmentAdapter judgmentAdapter;

        public Builder config(TestHarnessConfig config) {
            this.config = config;
            return this;
        }

        public Builder executor(CliExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder workspaceManager(WorkspaceManager workspaceManager) {
            this.workspaceManager = workspaceManager;
            return this;
        }

        public Builder useCaseLoader(UseCaseLoader useCaseLoader) {
            this.useCaseLoader = useCaseLoader;
            return this;
        }

        public Builder juryFactory(JuryFactory juryFactory) {
            this.juryFactory = juryFactory;
            return this;
        }

        public Builder judgmentAdapter(TestJudgmentAdapter judgmentAdapter) {
            this.judgmentAdapter = judgmentAdapter;
            return this;
        }

        public TestHarness build() {
            if (config == null) {
                throw new IllegalStateException("config is required");
            }
            // Use defaults for optional components
            if (workspaceManager == null) {
                workspaceManager = new WorkspaceManager();
            }
            if (useCaseLoader == null) {
                useCaseLoader = new UseCaseLoader();
            }
            if (juryFactory == null) {
                juryFactory = new JuryFactory();
            }
            if (judgmentAdapter == null) {
                judgmentAdapter = new TestJudgmentAdapter();
            }
            // Executor must be provided
            if (executor == null) {
                throw new IllegalStateException("executor is required");
            }

            return new TestHarness(
                    config,
                    executor,
                    workspaceManager,
                    useCaseLoader,
                    juryFactory,
                    judgmentAdapter
            );
        }
    }

}
