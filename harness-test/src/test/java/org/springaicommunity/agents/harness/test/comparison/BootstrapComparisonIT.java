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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springaicommunity.agents.harness.test.TestHarnessConfig;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Parameterized comparison tests for bootstrap use cases.
 *
 * <p>Runs MiniAgent vs Claude Code for each bootstrap test and validates both succeed.
 * Test results include timing from Surefire reports.</p>
 *
 * <p>Run with: {@code ./mvnw verify -pl harness-test -Dit.test=BootstrapComparisonIT}</p>
 *
 * <p>Run single test: {@code ./mvnw verify -pl harness-test -Dit.test="BootstrapComparisonIT#compareAgents[01-echo-hello]"}</p>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class BootstrapComparisonIT {

    private static final Path USE_CASES_DIR = Path.of("../tests/ai-driver/use-cases");
    private static final Path BOOTSTRAP_DIR = USE_CASES_DIR.resolve("bootstrap");
    private static final int MAX_TURNS = 10;

    private static ComparisonRunner runner;
    private static UseCaseLoader loader;

    @BeforeAll
    static void setUp() {
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

        CliExecutor executor = new InProcessExecutor(chatModel, MAX_TURNS);

        TestHarnessConfig config = TestHarnessConfig.builder()
                .cliCommand(List.of("dummy")) // Not used by InProcessExecutor
                .useCasesDir(USE_CASES_DIR)
                .transcriptsDir(Path.of("target/test-transcripts"))
                .saveTranscripts(true)
                .cleanupWorkspaces(true)
                .defaultTimeoutSeconds(120)
                .build();

        runner = new ComparisonRunner(config, executor);
        loader = new UseCaseLoader();
    }

    static Stream<String> bootstrapUseCases() throws Exception {
        if (!Files.exists(BOOTSTRAP_DIR)) {
            return Stream.empty();
        }

        return loader.findUseCases(BOOTSTRAP_DIR).stream()
                .map(path -> path.getFileName().toString().replace(".yaml", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bootstrapUseCases")
    @DisplayName("Compare MiniAgent vs Claude Code")
    void compareAgents(String useCaseName) throws Exception {
        Path yamlPath = BOOTSTRAP_DIR.resolve(useCaseName + ".yaml");
        UseCase useCase = loader.load(yamlPath);

        ComparisonReport report = runner.compare(useCase);

        // Log the comparison for debugging
        System.out.println(report.format());

        // Both agents should succeed on bootstrap tests
        assertThat(report.miniAgent().success())
                .as("MiniAgent should succeed on: %s", useCaseName)
                .isTrue();

        assertThat(report.claudeCode().success())
                .as("Claude Code should succeed on: %s", useCaseName)
                .isTrue();

        // Log tool gap analysis for insights
        ToolUsageComparison tc = report.toolUsageComparison();
        if (tc.hasToolGap()) {
            System.out.printf("TOOL GAP for %s: Claude used %s that MiniAgent lacks%n",
                    useCaseName, tc.claudeOnly());
        }
    }
}
