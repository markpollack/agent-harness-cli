///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.springaicommunity.agents:harness-test:0.1.0-SNAPSHOT
//DEPS org.springaicommunity.agents:harness-agents:0.1.0-SNAPSHOT
//DEPS org.springaicommunity:claude-code-sdk:1.0.0-SNAPSHOT
//DEPS org.springframework.ai:spring-ai-anthropic:2.0.0-SNAPSHOT
//REPOS mavenlocal,mavencentral,spring-milestones=https://repo.spring.io/milestone,spring-snapshots=https://repo.spring.io/snapshot
//JAVA 21

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

import org.springaicommunity.agents.harness.test.TestHarnessConfig;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.comparison.ComparisonRunner;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.InProcessExecutor;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Comparison Test Runner: MiniAgent vs Claude Code
 * <p>
 * Dedicated script for running comparison tests between MiniAgent and Claude Code.
 * Generates reports with Venn diagram tool analysis and loss metrics.
 * <p>
 * Usage:
 *   jbang RunComparison.java use-cases/bootstrap/01-echo-hello.yaml
 *   jbang RunComparison.java --category bootstrap
 *   jbang RunComparison.java --all
 */
public class RunComparison {

    private static final Path USE_CASES_DIR = Path.of("use-cases");
    private static final Path LEARNINGS_DIR = Path.of("../../plans/learnings");
    private static final Path CLI_JAR = Path.of("../../cli-app/target/cli-app-0.1.0-SNAPSHOT.jar");
    private static final int DEFAULT_MAX_TURNS = 10;

    private static CliExecutor cachedExecutor;

    public static void main(String... args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return;
        }

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: ANTHROPIC_API_KEY is required for comparison tests");
            System.exit(1);
        }

        if (args[0].equals("--category") && args.length > 1) {
            runCategory(args[1]);
        } else if (args[0].equals("--all")) {
            runAll();
        } else {
            runSingle(args[0]);
        }
    }

    private static void runSingle(String useCasePath) throws Exception {
        Path yamlPath = resolvePath(useCasePath);

        System.out.println("Running comparison: MiniAgent vs Claude Code");
        System.out.println("=".repeat(60));
        System.out.println("Use case: " + yamlPath);
        System.out.println();

        UseCaseLoader loader = new UseCaseLoader();
        UseCase useCase = loader.load(yamlPath);

        ComparisonRunner runner = createRunner();
        ComparisonReport report = runner.compare(useCase);

        System.out.println(report.format());
        saveReport(useCase.name(), report);

        boolean success = report.miniAgent().success() && report.claudeCode().success();
        System.exit(success ? 0 : 1);
    }

    private static void runCategory(String category) throws Exception {
        Path categoryDir = USE_CASES_DIR.resolve(category);
        if (!Files.exists(categoryDir) || !Files.isDirectory(categoryDir)) {
            System.err.println("Category directory not found: " + categoryDir);
            System.exit(1);
        }

        UseCaseLoader loader = new UseCaseLoader();
        List<Path> useCases = loader.findUseCases(categoryDir);

        if (useCases.isEmpty()) {
            System.err.println("No use cases found in category: " + category);
            System.exit(1);
        }

        runSuite(category, useCases, loader);
    }

    private static void runAll() throws Exception {
        UseCaseLoader loader = new UseCaseLoader();
        List<Path> useCases = loader.findUseCases(USE_CASES_DIR);

        if (useCases.isEmpty()) {
            System.err.println("No use cases found");
            System.exit(1);
        }

        runSuite("all", useCases, loader);
    }

    private static void runSuite(String suiteName, List<Path> useCases, UseCaseLoader loader) throws Exception {
        System.out.println("Running comparison suite: MiniAgent vs Claude Code");
        System.out.println("Suite: " + suiteName);
        System.out.println("Use cases: " + useCases.size());
        System.out.println("=".repeat(60));
        System.out.println();

        ComparisonRunner runner = createRunner();
        List<ComparisonReport> reports = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (Path yamlPath : useCases) {
            try {
                UseCase useCase = loader.load(yamlPath);
                System.out.printf("Running: %s... ", useCase.name());

                ComparisonReport report = runner.compare(useCase);
                reports.add(report);

                boolean bothPassed = report.miniAgent().success() && report.claudeCode().success();
                if (bothPassed) {
                    passed++;
                    System.out.println("PASS");
                } else {
                    failed++;
                    System.out.println("FAIL");
                }
            } catch (Exception e) {
                failed++;
                System.out.println("ERROR: " + e.getMessage());
            }
        }

        printSummary(reports, passed, failed);
        saveSuiteSummary(suiteName, reports);

        System.exit(failed > 0 ? 1 : 0);
    }

    private static void printSummary(List<ComparisonReport> reports, int passed, int failed) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("COMPARISON SUITE SUMMARY");
        System.out.println("=".repeat(60));

        for (ComparisonReport report : reports) {
            String miniStatus = report.miniAgent().success() ? "PASS" : "FAIL";
            String claudeStatus = report.claudeCode().success() ? "PASS" : "FAIL";
            String insight = report.toolUsageComparison().identicalToolSets() ? "IDENTICAL" :
                    (report.toolUsageComparison().hasToolGap() ? "TOOL GAP" : "Different");

            System.out.printf("  [Mini:%s Claude:%s] %s - %s%n",
                    miniStatus, claudeStatus, report.useCase().name(), insight);
        }

        System.out.println("=".repeat(60));
        System.out.printf("Passed: %d, Failed: %d, Total: %d%n", passed, failed, reports.size());
    }

    private static ComparisonRunner createRunner() {
        TestHarnessConfig config = TestHarnessConfig.builder()
                .cliCommand(List.of("java", "-jar", CLI_JAR.toAbsolutePath().toString(), "--linear"))
                .useCasesDir(USE_CASES_DIR)
                .transcriptsDir(Path.of("logs"))
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .defaultTimeoutSeconds(120)
                .build();

        return new ComparisonRunner(config, getOrCreateExecutor());
    }

    private static CliExecutor getOrCreateExecutor() {
        if (cachedExecutor != null) {
            return cachedExecutor;
        }

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        System.out.println("Using InProcessExecutor with Anthropic API");

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

        cachedExecutor = new InProcessExecutor(chatModel, DEFAULT_MAX_TURNS);
        return cachedExecutor;
    }

    private static Path resolvePath(String useCasePath) {
        Path yamlPath = Path.of(useCasePath);
        if (!yamlPath.isAbsolute() && !Files.exists(yamlPath)) {
            yamlPath = USE_CASES_DIR.resolve(useCasePath);
        }
        return yamlPath;
    }

    private static void saveReport(String useCaseName, ComparisonReport report) throws Exception {
        if (!Files.exists(LEARNINGS_DIR)) {
            Files.createDirectories(LEARNINGS_DIR);
        }

        String fileName = "comparison-" + useCaseName.replaceAll("[^a-zA-Z0-9]", "-") + ".md";
        Path reportFile = LEARNINGS_DIR.resolve(fileName);
        Files.writeString(reportFile, formatReportMarkdown(report));
        System.out.println("Report saved to: " + reportFile);
    }

    private static void saveSuiteSummary(String suiteName, List<ComparisonReport> reports) throws Exception {
        if (!Files.exists(LEARNINGS_DIR)) {
            Files.createDirectories(LEARNINGS_DIR);
        }

        String fileName = "comparison-suite-" + suiteName + ".md";
        Path summaryFile = LEARNINGS_DIR.resolve(fileName);
        Files.writeString(summaryFile, formatSuiteSummary(suiteName, reports));
        System.out.println("Summary saved to: " + summaryFile);
    }

    private static String formatReportMarkdown(ComparisonReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Comparison Report: ").append(report.useCase().name()).append("\n\n");
        md.append("**Date**: ").append(LocalDate.now()).append("\n\n");

        md.append("## Summary\n\n");
        md.append("- **MiniAgent**: ").append(report.miniAgent().success() ? "PASSED" : "FAILED").append("\n");
        md.append("- **Claude Code**: ").append(report.claudeCode().success() ? "PASSED" : "FAILED").append("\n\n");

        md.append("## Insight\n\n");
        md.append(report.generateInsight()).append("\n\n");

        md.append("## Tool Usage Comparison\n\n");
        md.append("```\n").append(report.toolUsageComparison().format()).append("```\n\n");

        md.append("## Tool Sequences\n\n");
        md.append("**MiniAgent**: ");
        if (report.miniAgent().toolSequence().isEmpty()) {
            md.append("(no tools captured)\n");
        } else {
            md.append(String.join(" -> ", report.miniAgent().toolSequence())).append("\n");
        }
        md.append("**Claude Code**: ");
        if (report.claudeCode().toolSequence().isEmpty()) {
            md.append("(no tools captured)\n");
        } else {
            md.append(String.join(" -> ", report.claudeCode().toolSequence())).append("\n");
        }

        return md.toString();
    }

    private static String formatSuiteSummary(String suiteName, List<ComparisonReport> reports) {
        StringBuilder md = new StringBuilder();
        md.append("# Comparison Suite: ").append(suiteName).append("\n\n");
        md.append("**Date**: ").append(LocalDate.now()).append("\n");
        md.append("**Tests**: ").append(reports.size()).append("\n\n");

        md.append("## Results\n\n");
        md.append("| # | Test | MiniAgent | Claude Code | Insight |\n");
        md.append("|---|------|-----------|-------------|----------|\n");

        int i = 1;
        for (ComparisonReport report : reports) {
            String miniStatus = report.miniAgent().success() ? "PASS" : "FAIL";
            String claudeStatus = report.claudeCode().success() ? "PASS" : "FAIL";
            String insight = report.toolUsageComparison().identicalToolSets() ? "IDENTICAL" :
                    (report.toolUsageComparison().hasToolGap() ? "TOOL GAP" : "Different");

            md.append(String.format("| %02d | %s | %s | %s | %s |\n",
                    i++, report.useCase().name(), miniStatus, claudeStatus, insight));
        }

        md.append("\n## Tool Gap Analysis\n\n");

        boolean anyGaps = false;
        for (ComparisonReport report : reports) {
            var tc = report.toolUsageComparison();
            if (tc.hasToolGap()) {
                anyGaps = true;
                md.append("### ").append(report.useCase().name()).append("\n");
                md.append("- **Claude Only (B-A)**: ").append(tc.claudeOnly()).append("\n");
                md.append("- **Jaccard Similarity**: ").append(String.format("%.1f%%", tc.jaccardSimilarity() * 100)).append("\n\n");
            }
        }

        if (!anyGaps) {
            md.append("No tool gaps detected. All tests used equivalent tool sets.\n");
        }

        return md.toString();
    }

    private static void printUsage() {
        System.out.println("""
            Comparison Test Runner: MiniAgent vs Claude Code

            Usage:
              jbang RunComparison.java <use-case.yaml>    Run single comparison
              jbang RunComparison.java --category <name>  Run all comparisons in category
              jbang RunComparison.java --all              Run all comparisons
              jbang RunComparison.java --help             Show this help

            Categories: basic, bootstrap, intermediate, bug-fix

            Environment Variables:
              ANTHROPIC_API_KEY    Required for both MiniAgent and Claude Code execution

            Examples:
              jbang RunComparison.java use-cases/bootstrap/01-echo-hello.yaml
              jbang RunComparison.java --category bootstrap
              jbang RunComparison.java --all

            Output:
              - Individual reports saved to plans/learnings/comparison-<name>.md
              - Suite summaries saved to plans/learnings/comparison-suite-<category>.md
            """);
    }
}
