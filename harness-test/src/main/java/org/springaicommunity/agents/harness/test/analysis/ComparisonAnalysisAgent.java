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

package org.springaicommunity.agents.harness.test.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.comparison.ToolUsageComparison;
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.ClaudeSyncClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent that analyzes comparison reports between MiniAgent and Claude Code.
 *
 * <p>Uses Claude Code (via claude-agent-sdk-java) to:
 * <ul>
 *   <li>Identify tool gaps and their root causes</li>
 *   <li>Analyze behavioral differences</li>
 *   <li>Generate actionable recommendations</li>
 *   <li>Propose specific fixes (prompt changes, tool config)</li>
 * </ul>
 *
 * <p>This enables autonomous self-directed correction by automating
 * the analysis that a human would do when reviewing comparison reports.</p>
 */
public class ComparisonAnalysisAgent {

    private static final Logger logger = LoggerFactory.getLogger(ComparisonAnalysisAgent.class);

    private static final Path CLAUDE_CODE_ANALYSIS = Path.of("/home/mark/tuvium/claude-code-analysis");
    private static final Path SPRING_AI_AGENT_UTILS = Path.of("/home/mark/community/spring-ai-agent-utils");
    private static final Path MINI_AGENT_CONFIG = Path.of("/home/mark/projects/agent-harness/harness-agents/src/main/java/org/springaicommunity/agents/harness/agents/mini/MiniAgentConfig.java");

    private final Path workingDirectory;

    public ComparisonAnalysisAgent() {
        this(Path.of("/home/mark/projects/agent-harness-cli"));
    }

    public ComparisonAnalysisAgent(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Analyze a single comparison report and return insights.
     */
    public AnalysisResult analyze(ComparisonReport report) {
        logger.info("Analyzing comparison report: {}", report.useCase().name());

        ToolUsageComparison tc = report.toolUsageComparison();

        // If no tool gap, return simple result
        if (!tc.hasToolGap()) {
            return AnalysisResult.noGap(report.useCase().name(), tc.jaccardSimilarity());
        }

        // Build analysis prompt
        String prompt = buildAnalysisPrompt(report);

        // Use Claude Code to analyze
        try (ClaudeSyncClient client = ClaudeClient.sync()
                .workingDirectory(workingDirectory)
                .model("claude-sonnet-4-20250514")
                .build()) {

            String analysis = client.connectText(prompt);
            return parseAnalysisResult(report.useCase().name(), tc, analysis);

        } catch (Exception e) {
            logger.error("Analysis failed for {}: {}", report.useCase().name(), e.getMessage());
            return AnalysisResult.error(report.useCase().name(), e.getMessage());
        }
    }

    /**
     * Analyze multiple comparison reports and identify patterns.
     */
    public BatchAnalysisResult analyzeAll(List<ComparisonReport> reports) {
        logger.info("Analyzing {} comparison reports", reports.size());

        // Collect reports with tool gaps
        List<ComparisonReport> reportsWithGaps = reports.stream()
                .filter(r -> r.toolUsageComparison().hasToolGap())
                .toList();

        if (reportsWithGaps.isEmpty()) {
            return BatchAnalysisResult.allPassing(reports.size());
        }

        // Collect all unique tool gaps
        Set<String> allToolGaps = reportsWithGaps.stream()
                .flatMap(r -> r.toolUsageComparison().claudeOnly().stream())
                .collect(Collectors.toSet());

        // Build batch analysis prompt
        String prompt = buildBatchAnalysisPrompt(reportsWithGaps, allToolGaps);

        try (ClaudeSyncClient client = ClaudeClient.sync()
                .workingDirectory(workingDirectory)
                .model("claude-sonnet-4-20250514")
                .build()) {

            String analysis = client.connectText(prompt);
            return parseBatchAnalysisResult(reports.size(), reportsWithGaps.size(), allToolGaps, analysis);

        } catch (Exception e) {
            logger.error("Batch analysis failed: {}", e.getMessage());
            return BatchAnalysisResult.error(e.getMessage());
        }
    }

    private String buildAnalysisPrompt(ComparisonReport report) {
        ToolUsageComparison tc = report.toolUsageComparison();

        return String.format("""
            You are analyzing a comparison report between MiniAgent and Claude Code.

            ## Context
            - MiniAgent is a Spring AI-based agent with tools: Read, Write, Edit, Bash, Glob, Grep, Submit
            - Claude Code is the reference implementation we benchmark against
            - Tool descriptions are in: %s
            - MiniAgent's system prompt is in: %s

            ## Comparison Report: %s

            ### Tool Usage
            - MiniAgent tools: %s
            - Claude Code tools: %s
            - Shared: %s
            - MiniAgent only: %s
            - Claude only (TOOL GAP): %s
            - Jaccard Similarity: %.1f%%

            ### Tool Sequences
            - MiniAgent: %s
            - Claude Code: %s

            ## Your Task

            1. **Root Cause Analysis**: Why did MiniAgent use different tools than Claude Code?
               - Is MiniAgent missing a tool? (Check if it's in spring-ai-agent-utils)
               - Is the system prompt not guiding correct tool choice?
               - Is the tool description unclear?

            2. **Proposed Fix**: What specific change would fix this?
               - If prompt issue: What line to add/change in MiniAgentConfig.java?
               - If tool missing: What tool to add from spring-ai-agent-utils?

            3. **Expected Impact**: What similarity improvement do you expect?

            Output a concise analysis (max 200 words) with:
            - ROOT CAUSE: [one sentence]
            - FIX: [specific change]
            - EXPECTED SIMILARITY: [percentage]
            """,
                SPRING_AI_AGENT_UTILS,
                MINI_AGENT_CONFIG,
                report.useCase().name(),
                tc.miniAgentTools(),
                tc.claudeCodeTools(),
                tc.shared(),
                tc.miniAgentOnly(),
                tc.claudeOnly(),
                tc.jaccardSimilarity() * 100,
                String.join(" → ", report.miniAgent().toolSequence()),
                String.join(" → ", report.claudeCode().toolSequence())
        );
    }

    private String buildBatchAnalysisPrompt(List<ComparisonReport> reports, Set<String> allToolGaps) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are analyzing multiple comparison reports between MiniAgent and Claude Code.

            ## Context
            - MiniAgent is a Spring AI-based agent
            - Claude Code is the reference implementation
            - Tool descriptions are in: %s
            - MiniAgent's system prompt is in: %s

            ## Reports with Tool Gaps

            """.formatted(SPRING_AI_AGENT_UTILS, MINI_AGENT_CONFIG));

        for (ComparisonReport report : reports) {
            ToolUsageComparison tc = report.toolUsageComparison();
            sb.append(String.format("""
                ### %s
                - Tool Gap (B-A): %s
                - Similarity: %.1f%%
                - MiniAgent sequence: %s
                - Claude sequence: %s

                """,
                    report.useCase().name(),
                    tc.claudeOnly(),
                    tc.jaccardSimilarity() * 100,
                    String.join(" → ", report.miniAgent().toolSequence()),
                    String.join(" → ", report.claudeCode().toolSequence())
            ));
        }

        sb.append(String.format("""
            ## All Unique Tool Gaps
            %s

            ## Your Task

            Identify common patterns and prioritized fixes:

            1. Which tool gaps appear most frequently?
            2. What single change would have the biggest impact?
            3. Prioritized list of fixes (max 3)

            Output concise analysis (max 300 words).
            """, allToolGaps));

        return sb.toString();
    }

    private AnalysisResult parseAnalysisResult(String testName, ToolUsageComparison tc, String analysis) {
        // Simple parsing - in production, use structured output
        RootCause rootCause = RootCause.UNKNOWN;
        if (analysis.toLowerCase().contains("prompt")) {
            rootCause = RootCause.PROMPT_ISSUE;
        } else if (analysis.toLowerCase().contains("missing") || analysis.toLowerCase().contains("not registered")) {
            rootCause = RootCause.MISSING_TOOL;
        } else if (analysis.toLowerCase().contains("description")) {
            rootCause = RootCause.TOOL_DESCRIPTION;
        }

        return new AnalysisResult(
                testName,
                tc.claudeOnly(),
                tc.jaccardSimilarity(),
                rootCause,
                analysis,
                0.8 // confidence
        );
    }

    private BatchAnalysisResult parseBatchAnalysisResult(int totalTests, int testsWithGaps,
                                                          Set<String> allToolGaps, String analysis) {
        return new BatchAnalysisResult(
                totalTests,
                testsWithGaps,
                allToolGaps,
                analysis,
                0.8
        );
    }

    /**
     * Result of analyzing a single comparison report.
     */
    public record AnalysisResult(
            String testName,
            Set<String> toolGaps,
            double similarity,
            RootCause rootCause,
            String analysis,
            double confidence
    ) {
        public static AnalysisResult noGap(String testName, double similarity) {
            return new AnalysisResult(testName, Set.of(), similarity, RootCause.NONE, "No tool gap detected", 1.0);
        }

        public static AnalysisResult error(String testName, String error) {
            return new AnalysisResult(testName, Set.of(), 0, RootCause.UNKNOWN, "Error: " + error, 0);
        }

        public boolean hasToolGap() {
            return !toolGaps.isEmpty();
        }
    }

    /**
     * Result of analyzing multiple comparison reports.
     */
    public record BatchAnalysisResult(
            int totalTests,
            int testsWithGaps,
            Set<String> allToolGaps,
            String analysis,
            double confidence
    ) {
        public static BatchAnalysisResult allPassing(int totalTests) {
            return new BatchAnalysisResult(totalTests, 0, Set.of(), "All tests passing with no tool gaps", 1.0);
        }

        public static BatchAnalysisResult error(String error) {
            return new BatchAnalysisResult(0, 0, Set.of(), "Error: " + error, 0);
        }

        public boolean hasToolGaps() {
            return testsWithGaps > 0;
        }
    }

    /**
     * Root cause classification for tool gaps.
     */
    public enum RootCause {
        NONE,              // No tool gap
        PROMPT_ISSUE,      // System prompt doesn't guide correctly
        TOOL_DESCRIPTION,  // Tool description unclear
        MISSING_TOOL,      // Tool not registered in MiniAgent
        MODEL_PREFERENCE,  // Model just prefers different approach
        UNKNOWN            // Could not determine
    }
}
