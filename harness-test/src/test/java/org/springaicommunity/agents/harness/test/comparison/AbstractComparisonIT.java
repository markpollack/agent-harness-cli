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

package org.springaicommunity.agents.harness.test.comparison;

import org.springaicommunity.agents.harness.test.TestHarnessConfig;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.InProcessExecutor;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Base class for comparison integration tests.
 *
 * <p>Provides common setup, assertion logic, and summary report generation
 * for comparing MiniAgent vs Claude Code. Subclasses specify the use case
 * category and any category-specific configuration.</p>
 *
 * <p>After all tests complete, subclasses should call {@link #generateAndSaveSummary(String, Path)}
 * in their @AfterAll method to produce a human-readable report.</p>
 */
abstract class AbstractComparisonIT {

    protected static final Path USE_CASES_DIR = Path.of("../tests/ai-driver/use-cases");
    protected static final Path LEARNINGS_DIR = Path.of("../plans/learnings");

    protected static ComparisonRunner runner;
    protected static UseCaseLoader loader;
    protected static ComparisonReportSummarizer summarizer;

    /**
     * Initialize the runner with category-specific configuration.
     */
    protected static void initRunner(String suiteName, int maxTurns, int timeoutSeconds) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");

        AnthropicApi api = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(4096)
                .build();

        ChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();

        CliExecutor executor = new InProcessExecutor(chatModel, maxTurns);

        TestHarnessConfig config = TestHarnessConfig.builder()
                .cliCommand(List.of("dummy")) // Not used by InProcessExecutor
                .useCasesDir(USE_CASES_DIR)
                .transcriptsDir(Path.of("target/test-transcripts"))
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .defaultTimeoutSeconds(timeoutSeconds)
                .build();

        runner = new ComparisonRunner(config, executor);
        loader = new UseCaseLoader();
        summarizer = new ComparisonReportSummarizer(suiteName);
    }

    /**
     * Find use cases in a category directory.
     */
    protected static Stream<String> findUseCases(Path categoryDir) throws Exception {
        if (!Files.exists(categoryDir)) {
            return Stream.empty();
        }

        return loader.findUseCases(categoryDir).stream()
                .map(path -> path.getFileName().toString().replace(".yaml", ""));
    }

    /**
     * Run comparison and validate both agents succeed.
     * Automatically adds the report to the summarizer.
     */
    protected void runComparison(String useCaseName, Path categoryDir) throws Exception {
        Path yamlPath = categoryDir.resolve(useCaseName + ".yaml");
        UseCase useCase = loader.load(yamlPath);

        ComparisonReport report = runner.compare(useCase);

        // Add to summarizer for end-of-suite report
        summarizer.add(report);

        // Log the comparison
        System.out.println(report.format());

        // Both agents should succeed
        assertThat(report.miniAgent().success())
                .as("MiniAgent should succeed on: %s", useCaseName)
                .isTrue();

        assertThat(report.claudeCode().success())
                .as("Claude Code should succeed on: %s", useCaseName)
                .isTrue();

        // Log tool gap analysis
        ToolUsageComparison tc = report.toolUsageComparison();
        if (tc.hasToolGap()) {
            System.out.printf("TOOL GAP for %s: Claude used %s that MiniAgent lacks%n",
                    useCaseName, tc.claudeOnly());
        }

        // Log behavioral analysis
        BehavioralPatternAnalyzer.BehavioralAnalysis behavior = report.behavioralAnalysis();
        if (behavior.hasGaps()) {
            System.out.printf("BEHAVIORAL GAPS for %s:%n", useCaseName);
            System.out.println(behavior.format());
        }
    }

    /**
     * Generate and save the summary report after all tests complete.
     * Call this from @AfterAll in subclasses.
     */
    protected static void generateAndSaveSummary(String suiteName, Path outputDir) {
        if (summarizer == null || summarizer.getReportCount() == 0) {
            System.out.println("No reports to summarize.");
            return;
        }

        try {
            // Ensure output directory exists
            Files.createDirectories(outputDir);

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = String.format("%s-summary-%s.md",
                    suiteName.toLowerCase().replace(" ", "-"), timestamp);
            Path outputPath = outputDir.resolve(filename);

            // Save the summary
            summarizer.saveTo(outputPath);

            System.out.println("\n" + "=".repeat(70));
            System.out.println("SUMMARY REPORT GENERATED");
            System.out.println("=".repeat(70));
            System.out.println("File: " + outputPath.toAbsolutePath());
            System.out.println("Tests: " + summarizer.getReportCount());
            System.out.println("=".repeat(70) + "\n");

            // Also print summary to console
            System.out.println(summarizer.generateSummary());

        } catch (IOException e) {
            System.err.println("Failed to save summary report: " + e.getMessage());
            // Still print to console
            System.out.println(summarizer.generateSummary());
        }
    }
}
