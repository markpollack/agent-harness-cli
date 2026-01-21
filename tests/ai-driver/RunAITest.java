///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.springaicommunity.agents:harness-test:0.1.0-SNAPSHOT
//DEPS org.springaicommunity.agents:harness-agents:0.1.0-SNAPSHOT
//DEPS org.springaicommunity:claude-code-sdk:1.0.0-SNAPSHOT
//DEPS org.springaicommunity:agent-judge-llm:0.9.0-SNAPSHOT
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

import org.springaicommunity.agents.harness.test.TestHarness;
import org.springaicommunity.agents.harness.test.TestHarnessConfig;
import org.springaicommunity.agents.harness.test.TestResult;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.comparison.ComparisonRunner;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.DefaultCliExecutor;
import org.springaicommunity.agents.harness.test.executor.InProcessExecutor;
import org.springaicommunity.agents.harness.test.executor.ToolCallRecord;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;
import org.springaicommunity.agents.harness.test.validation.JuryFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AI-Driven Integration Test Runner for agent-harness-cli
 * <p>
 * Uses "AI Turks" pattern: AI agents test AI agents through
 * synthetic user simulation and Judge-based validation.
 * <p>
 * Usage:
 *   jbang RunAITest.java use-cases/basic/quit.yaml
 *   jbang RunAITest.java --all
 *   jbang RunAITest.java --category bug-fix
 *   jbang RunAITest.java --category advanced --timeout 2400
 *   jbang RunAITest.java --compare use-cases/intermediate/21-api-design.yaml
 *   jbang RunAITest.java --list
 */
public class RunAITest {

    private static final Path USE_CASES_DIR = Path.of("use-cases");
    private static final Path LOGS_DIR = Path.of("logs");
    private static final Path CLI_JAR = Path.of("../../cli-app/target/cli-app-0.1.0-SNAPSHOT.jar");
    private static final int DEFAULT_MAX_TURNS = 10;
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int ADVANCED_MAX_TURNS = 25;

    // Runtime options parsed from command line
    private static int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private static int maxTurns = DEFAULT_MAX_TURNS;

    // Cached executor for reuse across tests
    private static CliExecutor cachedExecutor;

    // Cached ChatClient.Builder for LLM judge (uses Opus 4.5)
    private static ChatClient.Builder judgeChatClientBuilder;

    public static void main(String... args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return;
        }

        // Parse global options
        int i = 0;
        String command = null;
        String commandArg = null;

        while (i < args.length) {
            String arg = args[i];
            if (arg.equals("--timeout") && i + 1 < args.length) {
                timeoutSeconds = Integer.parseInt(args[++i]);
                System.out.println("Timeout set to: " + timeoutSeconds + " seconds");
            } else if (arg.equals("--max-turns") && i + 1 < args.length) {
                maxTurns = Integer.parseInt(args[++i]);
                System.out.println("Max turns set to: " + maxTurns);
            } else if (arg.startsWith("--")) {
                command = arg;
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    commandArg = args[++i];
                }
            } else if (command == null) {
                command = "single";
                commandArg = arg;
            }
            i++;
        }

        // Auto-configure for advanced category
        if ("--category".equals(command) && "advanced".equals(commandArg)) {
            if (timeoutSeconds == DEFAULT_TIMEOUT_SECONDS) {
                timeoutSeconds = 2400; // 40 minutes for advanced
                System.out.println("Auto-set timeout to 2400s for advanced tests");
            }
            if (maxTurns == DEFAULT_MAX_TURNS) {
                maxTurns = ADVANCED_MAX_TURNS;
                System.out.println("Auto-set max turns to " + maxTurns + " for advanced tests");
            }
        }

        TestHarness harness = createHarness();

        switch (command) {
            case "--all" -> runAll(harness);
            case "--category" -> runCategory(harness, commandArg);
            case "--compare" -> runComparison(createConfig(), commandArg);
            case "--list" -> listUseCases();
            case "single" -> runSingle(harness, commandArg);
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static TestHarness createHarness() {
        TestHarnessConfig config = createConfig();
        CliExecutor executor = getOrCreateExecutor();

        // Create JuryFactory with LLM judge if API key is available
        JuryFactory juryFactory = createJuryFactory();

        return TestHarness.builder()
                .config(config)
                .executor(executor)
                .juryFactory(juryFactory)
                .build();
    }

    private static JuryFactory createJuryFactory() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("LLM Judge disabled (no ANTHROPIC_API_KEY)");
            return new JuryFactory(); // Deterministic judges only
        }

        if (judgeChatClientBuilder == null) {
            judgeChatClientBuilder = createJudgeChatClientBuilder(apiKey);
            System.out.println("LLM Judge enabled (Claude Opus 4.5 for expectedBehavior evaluation)");
        }

        return new JuryFactory(judgeChatClientBuilder);
    }

    private static ChatClient.Builder createJudgeChatClientBuilder(String apiKey) {
        AnthropicApi api = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        // Use Opus 4.5 for the judge - a stronger model for evaluation
        // Large token limit to allow complete, accurate evaluation
        // Future: Consider adding thinking mode for more detailed reasoning
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model("claude-opus-4-5")
                .maxTokens(8192)
                .build();

        ChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel);
    }

    private static TestHarnessConfig createConfig() {
        List<String> command = List.of(
                "java", "-jar", CLI_JAR.toAbsolutePath().toString(), "--linear"
        );

        return TestHarnessConfig.builder()
                .cliCommand(command)
                .useCasesDir(USE_CASES_DIR)
                .transcriptsDir(LOGS_DIR)
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .defaultTimeoutSeconds(timeoutSeconds)
                .build();
    }

    private static CliExecutor getOrCreateExecutor() {
        if (cachedExecutor != null) {
            return cachedExecutor;
        }

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("WARNING: ANTHROPIC_API_KEY not set. Falling back to subprocess execution.");
            System.err.println("         Set ANTHROPIC_API_KEY for in-process MiniAgent execution with tool call capture.");
            cachedExecutor = new DefaultCliExecutor();
            return cachedExecutor;
        }

        System.out.println("Using InProcessExecutor with Anthropic API (structured tool call capture enabled)");
        cachedExecutor = createInProcessExecutor(apiKey);
        return cachedExecutor;
    }

    private static InProcessExecutor createInProcessExecutor(String apiKey) {
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

        return new InProcessExecutor(chatModel, maxTurns);
    }

    private static void runSingle(TestHarness harness, String useCasePath) throws Exception {
        Path yamlPath = Path.of(useCasePath);
        if (!yamlPath.isAbsolute() && !Files.exists(yamlPath)) {
            yamlPath = USE_CASES_DIR.resolve(useCasePath);
        }

        TestResult result = harness.run(yamlPath);
        printResult(result);
        System.exit(result.passed() ? 0 : 1);
    }

    private static void runAll(TestHarness harness) throws Exception {
        List<TestResult> results = harness.runAll();
        printSummary(results);

        long failed = results.stream().filter(r -> !r.passed()).count();
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void runCategory(TestHarness harness, String category) throws Exception {
        List<TestResult> results = harness.runCategory(category);
        if (results.isEmpty()) {
            System.err.println("No use cases found in category: " + category);
            System.exit(1);
        }

        printSummary(results);

        long failed = results.stream().filter(r -> !r.passed()).count();
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void listUseCases() throws Exception {
        UseCaseLoader loader = new UseCaseLoader();
        List<Path> allUseCases = loader.findUseCases(USE_CASES_DIR);

        System.out.println("Available use cases:");
        System.out.println("=".repeat(60));

        for (Path path : allUseCases) {
            String category = path.getParent().getFileName().toString();
            String name = path.getFileName().toString().replace(".yaml", "");
            System.out.printf("  [%s] %s%n", category, name);
        }

        System.out.println("=".repeat(60));
        System.out.printf("Total: %d use cases%n", allUseCases.size());
    }

    private static void runComparison(TestHarnessConfig config, String useCasePath) throws Exception {
        Path yamlPath = Path.of(useCasePath);
        if (!yamlPath.isAbsolute() && !Files.exists(yamlPath)) {
            yamlPath = USE_CASES_DIR.resolve(useCasePath);
        }

        System.out.println("Running comparison test: MiniAgent vs Claude Code");
        System.out.println("=".repeat(60));
        System.out.println("Use case: " + yamlPath);
        System.out.println();

        UseCaseLoader loader = new UseCaseLoader();
        UseCase useCase = loader.load(yamlPath);

        CliExecutor executor = getOrCreateExecutor();
        ComparisonRunner runner = new ComparisonRunner(config, executor);
        ComparisonReport report = runner.compare(useCase);

        System.out.println(report.format());

        // Save report to learnings directory
        Path learningsDir = Path.of("../../plans/learnings");
        if (!Files.exists(learningsDir)) {
            Files.createDirectories(learningsDir);
        }

        String reportFileName = "comparison-" + useCase.name().replaceAll("[^a-zA-Z0-9]", "-") + ".md";
        Path reportFile = learningsDir.resolve(reportFileName);
        Files.writeString(reportFile, formatAsMarkdown(report));
        System.out.println("Report saved to: " + reportFile);
    }

    private static String formatAsMarkdown(ComparisonReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Comparison Report: ").append(report.useCase().name()).append("\n\n");
        md.append("## Summary\n\n");
        md.append("- **MiniAgent**: ").append(report.miniAgent().success() ? "PASSED" : "FAILED").append("\n");
        md.append("- **Claude Code**: ").append(report.claudeCode().success() ? "PASSED" : "FAILED").append("\n\n");

        md.append("## Insight\n\n");
        md.append(report.generateInsight()).append("\n\n");

        md.append("## MiniAgent Execution\n\n");
        md.append("```\n").append(report.miniAgent().format()).append("```\n\n");

        md.append("## Claude Code Execution\n\n");
        md.append("```\n").append(report.claudeCode().format()).append("```\n\n");

        var differences = report.analyzeDifferences();
        if (!differences.isEmpty()) {
            md.append("## Key Differences\n\n");
            for (String diff : differences) {
                md.append("- ").append(diff).append("\n");
            }
            md.append("\n");
        }

        md.append("## Tool Sequences\n\n");
        md.append("**MiniAgent**: ");
        if (report.miniAgent().toolSequence().isEmpty()) {
            md.append("(no tools captured)\n");
        } else {
            md.append(String.join(" → ", report.miniAgent().toolSequence())).append("\n");
        }
        md.append("**Claude Code**: ");
        if (report.claudeCode().toolSequence().isEmpty()) {
            md.append("(no tools captured)\n");
        } else {
            md.append(String.join(" → ", report.claudeCode().toolSequence())).append("\n");
        }

        return md.toString();
    }

    private static void printResult(TestResult result) {
        String status = result.passed() ? "PASSED" : "FAILED";
        System.out.printf("%n%s: %s%n", result.useCaseName(), status);
        System.out.printf("  Status: %s%n", result.status());
        System.out.printf("  Duration: %dms%n", result.durationMs());
        System.out.printf("  Turns: %d%n", result.turnsUsed());

        if (result.transcriptFile() != null) {
            System.out.printf("  Transcript: %s%n", result.transcriptFile());
        }

        if (!result.issues().isEmpty()) {
            System.out.println("  Issues:");
            for (String issue : result.issues()) {
                System.out.printf("    - %s%n", issue);
            }
        }
    }

    private static void printSummary(List<TestResult> results) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("TEST RESULTS SUMMARY");
        System.out.println("=".repeat(60));

        for (TestResult result : results) {
            String status = result.passed() ? "PASS" : "FAIL";
            System.out.printf("  [%s] %s (%dms)%n", status, result.useCaseName(), result.durationMs());
        }

        System.out.println("=".repeat(60));

        long passed = results.stream().filter(TestResult::passed).count();
        long failed = results.size() - passed;

        System.out.printf("Passed: %d, Failed: %d, Total: %d%n", passed, failed, results.size());

        if (failed > 0) {
            System.out.println();
            System.out.println("Failed tests:");
            for (TestResult result : results) {
                if (!result.passed()) {
                    System.out.printf("  - %s: %s%n", result.useCaseName(), result.reasoning());
                }
            }
        }
    }

    private static void printUsage() {
        System.out.println("""
            AI-Driven Integration Test Runner for agent-harness-cli

            Usage:
              jbang RunAITest.java <use-case.yaml>       Run single use case
              jbang RunAITest.java --all                 Run all use cases
              jbang RunAITest.java --category <name>     Run use cases in category
              jbang RunAITest.java --compare <use-case>  Compare MiniAgent vs Claude Code
              jbang RunAITest.java --list                List all available use cases
              jbang RunAITest.java --help                Show this help

            Options:
              --timeout <seconds>    Set timeout per test (default: 120, advanced: 2400)
              --max-turns <n>        Set max turns per test (default: 10, advanced: 25)

            Categories: basic, bootstrap, intermediate, advanced, bug-fix

            Note: 'advanced' category auto-configures 40min timeout and 25 max turns

            Environment Variables:
              ANTHROPIC_API_KEY    Required for agent execution (set to run agent tests)

            Examples:
              jbang RunAITest.java use-cases/basic/quit.yaml
              jbang RunAITest.java --category basic
              jbang RunAITest.java --category advanced
              jbang RunAITest.java --category advanced --timeout 3600
              jbang RunAITest.java --compare use-cases/advanced/31-config-parser-fix.yaml
            """);
    }
}
