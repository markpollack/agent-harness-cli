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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.TestHarnessConfig;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.CliExecutor.ExecutionConfig;
import org.springaicommunity.agents.harness.test.executor.ClaudeCodeExecutor;
import org.springaicommunity.agents.harness.test.executor.DefaultCliExecutor;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceContext;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceManager;

import java.io.IOException;
import java.util.List;

/**
 * Runs the same test case against both MiniAgent and Claude Code CLI,
 * generating a comparison report.
 *
 * <p>This enables distinguishing between:
 * <ul>
 *   <li>Test design issues (both agents behave similarly)</li>
 *   <li>Agent capability gaps (agents behave differently)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * ComparisonRunner runner = new ComparisonRunner(config);
 * ComparisonReport report = runner.compare(useCase);
 * System.out.println(report.format());
 * }</pre>
 */
public class ComparisonRunner {

    private static final Logger logger = LoggerFactory.getLogger(ComparisonRunner.class);

    private final TestHarnessConfig config;
    private final WorkspaceManager workspaceManager;
    private final CliExecutor miniAgentExecutor;
    private final ClaudeCodeExecutor claudeCodeExecutor;

    /**
     * Creates a comparison runner with the given configuration.
     */
    public ComparisonRunner(TestHarnessConfig config) {
        this.config = config;
        this.workspaceManager = new WorkspaceManager();
        this.miniAgentExecutor = new DefaultCliExecutor();
        this.claudeCodeExecutor = new ClaudeCodeExecutor();
    }

    /**
     * Runs the use case against both agents and generates a comparison report.
     *
     * @param useCase the test case to run
     * @return comparison report with results from both agents
     * @throws IOException if workspace setup fails
     */
    public ComparisonReport compare(UseCase useCase) throws IOException {
        logger.info("Starting comparison for: {}", useCase.name());

        // Run MiniAgent first
        ExecutionSummary miniAgentSummary = runMiniAgent(useCase);

        // Run Claude Code
        ExecutionSummary claudeCodeSummary = runClaudeCode(useCase);

        ComparisonReport report = new ComparisonReport(useCase, miniAgentSummary, claudeCodeSummary);
        logger.info("Comparison complete: {}", report.generateInsight());

        return report;
    }

    private ExecutionSummary runMiniAgent(UseCase useCase) throws IOException {
        logger.info("Running MiniAgent for: {}", useCase.name());

        WorkspaceContext workspace = workspaceManager.setup(useCase);
        long startTime = System.currentTimeMillis();

        try {
            ExecutionConfig execConfig = ExecutionConfig.builder()
                .command(config.cliCommand())
                .workingDirectory(workspace.workspacePath())
                .input(useCase.prompt())
                .timeoutSeconds(useCase.timeoutSeconds())
                .build();

            ExecutionResult result = miniAgentExecutor.execute(execConfig);
            long durationMs = System.currentTimeMillis() - startTime;

            // For MiniAgent, we don't have detailed tool tracking yet
            // This is a gap we identified - MiniAgent needs TracingToolCallListener
            return ExecutionSummary.builder()
                .agentId("MiniAgent")
                .toolCalls(List.of())  // TODO: Capture tool calls from MiniAgent
                .inputTokens(0)         // TODO: Extract from output
                .outputTokens(0)
                .thinkingTokens(0)
                .numTurns(extractTurns(result.output()))
                .success(result.isSuccess())
                .durationMs(durationMs)
                .build();
        }
        finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private ExecutionSummary runClaudeCode(UseCase useCase) throws IOException {
        logger.info("Running Claude Code for: {}", useCase.name());

        WorkspaceContext workspace = workspaceManager.setup(useCase);

        try {
            ExecutionConfig execConfig = ExecutionConfig.builder()
                .command(List.of("claude"))  // Not used by ClaudeCodeExecutor but required
                .workingDirectory(workspace.workspacePath())
                .input(useCase.prompt())
                .timeoutSeconds(useCase.timeoutSeconds())
                .build();

            claudeCodeExecutor.execute(execConfig);

            // ClaudeCodeExecutor captures detailed metrics
            return claudeCodeExecutor.getLastSummary();
        }
        finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private int extractTurns(String output) {
        // Simple heuristic: count occurrences of tool calls or LLM responses
        // This is a placeholder until we have proper tracking
        if (output == null || output.isEmpty()) {
            return 0;
        }
        int turns = 0;
        for (String line : output.split("\n")) {
            if (line.contains("Tool:") || line.contains("Assistant:")) {
                turns++;
            }
        }
        return Math.max(1, turns);
    }
}
