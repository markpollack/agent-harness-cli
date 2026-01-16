///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.springaicommunity.agents:harness-test:0.1.0-SNAPSHOT
//REPOS mavenlocal,mavencentral
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
import org.springaicommunity.agents.harness.test.executor.DefaultCliExecutor;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.usecase.UseCaseLoader;

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
 *   jbang RunAITest.java --list
 */
public class RunAITest {

    private static final Path USE_CASES_DIR = Path.of("use-cases");
    private static final Path LOGS_DIR = Path.of("logs");
    private static final Path CLI_JAR = Path.of("../../cli-app/target/cli-app-0.1.0-SNAPSHOT.jar");

    public static void main(String... args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            return;
        }

        TestHarness harness = createHarness();

        if (args[0].equals("--all")) {
            runAll(harness);
        } else if (args[0].equals("--category") && args.length > 1) {
            runCategory(harness, args[1]);
        } else if (args[0].equals("--list")) {
            listUseCases();
        } else {
            runSingle(harness, args[0]);
        }
    }

    private static TestHarness createHarness() {
        List<String> command = List.of(
                "java", "-jar", CLI_JAR.toAbsolutePath().toString(), "--linear"
        );

        TestHarnessConfig config = TestHarnessConfig.builder()
                .cliCommand(command)
                .useCasesDir(USE_CASES_DIR)
                .transcriptsDir(LOGS_DIR)
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .defaultTimeoutSeconds(120)
                .build();

        return TestHarness.builder()
                .config(config)
                .executor(new DefaultCliExecutor())
                .build();
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
              jbang RunAITest.java --list                List all available use cases
              jbang RunAITest.java --help                Show this help

            Categories: basic, bug-fix, feature, refactor

            Environment Variables:
              ANTHROPIC_API_KEY    Required for agent execution (set to run agent tests)

            Examples:
              jbang RunAITest.java use-cases/basic/quit.yaml
              jbang RunAITest.java --category basic
              jbang RunAITest.java --all
            """);
    }
}
