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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.TestHarnessConfig;
import org.springaicommunity.agents.harness.test.executor.CliExecutor;
import org.springaicommunity.agents.harness.test.executor.CliExecutor.ExecutionConfig;
import org.springaicommunity.agents.harness.test.executor.ClaudeCodeExecutor;
import org.springaicommunity.agents.harness.test.executor.ExecutionResult;
import org.springaicommunity.agents.harness.test.executor.ToolCallRecord;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceContext;
import org.springaicommunity.agents.harness.test.workspace.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String CACHE_DIR = ".claude-code-cache";

    private final TestHarnessConfig config;
    private final WorkspaceManager workspaceManager;
    private final CliExecutor miniAgentExecutor;
    private final ClaudeCodeExecutor claudeCodeExecutor;
    private final ObjectMapper objectMapper;
    private final Path cacheDirectory;
    private boolean useCache = true;

    /**
     * Creates a comparison runner with the given configuration and executor.
     *
     * <p>For structured tool call capture, use {@link org.springaicommunity.agents.harness.test.executor.InProcessExecutor}.
     * For subprocess-based execution, use {@link org.springaicommunity.agents.harness.test.executor.DefaultCliExecutor}.</p>
     *
     * @param config the test harness configuration
     * @param miniAgentExecutor the executor to use for MiniAgent tests
     */
    public ComparisonRunner(TestHarnessConfig config, CliExecutor miniAgentExecutor) {
        this.config = config;
        this.workspaceManager = new WorkspaceManager();
        this.miniAgentExecutor = miniAgentExecutor;
        this.claudeCodeExecutor = new ClaudeCodeExecutor();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.cacheDirectory = Path.of(CACHE_DIR);
    }

    /**
     * Enable or disable Claude Code result caching.
     *
     * <p>When enabled (default), successful Claude Code results are cached to disk.
     * On subsequent runs, cached results are used instead of re-running Claude Code.
     * This speeds up iteration when developing MiniAgent.</p>
     *
     * @param useCache true to use caching, false to always run fresh
     * @return this runner for chaining
     */
    public ComparisonRunner useCache(boolean useCache) {
        this.useCache = useCache;
        return this;
    }

    /**
     * Runs the use case against both agents and generates a comparison report.
     *
     * <p>Claude Code runs first to establish a baseline. This helps with debugging
     * MiniAgent failures - we can compare its behavior against a known-working reference.</p>
     *
     * @param useCase the test case to run
     * @return comparison report with results from both agents
     * @throws IOException if workspace setup fails
     */
    public ComparisonReport compare(UseCase useCase) throws IOException {
        logger.info("Starting comparison for: {}", useCase.name());

        // Run Claude Code first as baseline (helps debug MiniAgent failures)
        ExecutionSummary claudeCodeSummary = runClaudeCode(useCase);
        logger.info("Claude Code baseline complete: success={}, turns={}, tools={}",
                claudeCodeSummary.success(), claudeCodeSummary.numTurns(), claudeCodeSummary.toolCalls().size());

        // Run MiniAgent
        ExecutionSummary miniAgentSummary = runMiniAgent(useCase);

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

            // Get tool calls directly from executor (structured data, no parsing)
            List<ToolCallRecord> toolCallRecords = miniAgentExecutor.getToolCalls();
            List<ToolCallEvent> toolCalls = toolCallRecords.stream()
                .map(ToolCallRecord::toToolCallEvent)
                .toList();

            logger.info("Captured {} tool calls from MiniAgent execution", toolCalls.size());

            // Estimate turns from tool call count (each tool call is roughly one turn)
            int numTurns = Math.max(1, toolCalls.size());

            return ExecutionSummary.builder()
                .agentId("MiniAgent")
                .toolCalls(toolCalls)
                .inputTokens(0)         // TODO: Extract from result if available
                .outputTokens(0)
                .thinkingTokens(0)
                .numTurns(numTurns)
                .success(result.isSuccess())
                .durationMs(durationMs)
                .build();
        }
        finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private ExecutionSummary runClaudeCode(UseCase useCase) throws IOException {
        // Check cache first if enabled
        if (useCache) {
            ExecutionSummary cached = loadCachedResult(useCase);
            if (cached != null && cached.success()) {
                logger.info("Using cached Claude Code result for: {} (success={}, turns={}, tools={})",
                        useCase.name(), cached.success(), cached.numTurns(), cached.toolCalls().size());
                return cached;
            }
        }

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
            ExecutionSummary summary = claudeCodeExecutor.getLastSummary();

            // Cache successful results
            if (useCache && summary.success()) {
                saveCachedResult(useCase, summary);
            }

            return summary;
        }
        finally {
            workspaceManager.cleanup(workspace);
        }
    }

    /**
     * Load cached Claude Code result for a use case.
     */
    private ExecutionSummary loadCachedResult(UseCase useCase) {
        Path cacheFile = getCacheFile(useCase);
        if (!Files.exists(cacheFile)) {
            return null;
        }

        try {
            CachedSummary cached = objectMapper.readValue(cacheFile.toFile(), CachedSummary.class);
            logger.debug("Loaded cached result from: {}", cacheFile);
            return cached.toExecutionSummary();
        } catch (IOException e) {
            logger.warn("Failed to load cached result: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save Claude Code result to cache.
     */
    private void saveCachedResult(UseCase useCase, ExecutionSummary summary) {
        try {
            Files.createDirectories(cacheDirectory);
            Path cacheFile = getCacheFile(useCase);
            CachedSummary cached = CachedSummary.from(summary);
            objectMapper.writeValue(cacheFile.toFile(), cached);
            logger.info("Cached Claude Code result to: {}", cacheFile);
        } catch (IOException e) {
            logger.warn("Failed to cache result: {}", e.getMessage());
        }
    }

    /**
     * Get cache file path for a use case.
     */
    private Path getCacheFile(UseCase useCase) {
        String safeName = useCase.name().replaceAll("[^a-zA-Z0-9-]", "_");
        return cacheDirectory.resolve(safeName + ".json");
    }

    /**
     * Serializable wrapper for ExecutionSummary caching.
     */
    public static class CachedSummary {
        public String agentId;
        public List<CachedToolCall> toolCalls;
        public int inputTokens;
        public int outputTokens;
        public int thinkingTokens;
        public int numTurns;
        public boolean success;
        public long durationMs;

        public CachedSummary() {} // For Jackson

        public static CachedSummary from(ExecutionSummary summary) {
            CachedSummary cached = new CachedSummary();
            cached.agentId = summary.agentId();
            cached.toolCalls = summary.toolCalls().stream()
                .map(tc -> CachedToolCall.from(tc))
                .toList();
            cached.inputTokens = summary.inputTokens();
            cached.outputTokens = summary.outputTokens();
            cached.thinkingTokens = summary.thinkingTokens();
            cached.numTurns = summary.numTurns();
            cached.success = summary.success();
            cached.durationMs = summary.durationMs();
            return cached;
        }

        public ExecutionSummary toExecutionSummary() {
            return ExecutionSummary.builder()
                .agentId(agentId)
                .toolCalls(toolCalls.stream().map(CachedToolCall::toToolCallEvent).toList())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .thinkingTokens(thinkingTokens)
                .numTurns(numTurns)
                .success(success)
                .durationMs(durationMs)
                .build();
        }
    }

    /**
     * Serializable wrapper for ToolCallEvent.
     */
    public static class CachedToolCall {
        public String toolName;
        public java.util.Map<String, Object> input;
        public Object output;
        public boolean success;

        public CachedToolCall() {} // For Jackson

        public static CachedToolCall from(ToolCallEvent event) {
            CachedToolCall cached = new CachedToolCall();
            cached.toolName = event.toolName();
            cached.input = event.input();
            cached.output = event.output();
            cached.success = event.success();
            return cached;
        }

        public ToolCallEvent toToolCallEvent() {
            return new ToolCallEvent(toolName, input, output, success);
        }
    }

}
