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
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.ClaudeSyncClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates summary reports from comparison markdown files using Claude.
 *
 * <p>Reads all comparison-*.md files from a directory and uses Claude
 * to generate a comprehensive human-readable summary report.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * SummaryReportGenerator generator = new SummaryReportGenerator();
 * String summary = generator.generate(Path.of("plans/learnings"), "intermediate");
 * generator.generateAndSave(Path.of("plans/learnings"), "intermediate");
 * }</pre>
 */
public class SummaryReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SummaryReportGenerator.class);

    private final Path workingDirectory;

    public SummaryReportGenerator() {
        this(Path.of("/home/mark/projects/agent-harness-cli"));
    }

    public SummaryReportGenerator(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Generate a summary report from comparison files in a directory.
     *
     * @param reportsDir directory containing comparison-*.md files
     * @param suiteName name of the test suite (e.g., "intermediate", "bootstrap")
     * @return markdown summary report
     */
    public String generate(Path reportsDir, String suiteName) throws IOException {
        logger.info("Generating summary for {} suite from {}", suiteName, reportsDir);

        // Find and read all comparison reports
        List<String> reports = readComparisonReports(reportsDir);

        if (reports.isEmpty()) {
            return "# No comparison reports found\n\nNo comparison-*.md files in: " + reportsDir;
        }

        logger.info("Found {} comparison reports", reports.size());

        // Build prompt with all reports
        String prompt = buildSummaryPrompt(suiteName, reports);

        // Use Claude to generate summary
        try (ClaudeSyncClient client = ClaudeClient.sync()
                .workingDirectory(workingDirectory)
                .model("claude-sonnet-4-20250514")
                .build()) {

            return client.connectText(prompt);

        } catch (Exception e) {
            logger.error("Summary generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate summary: " + e.getMessage(), e);
        }
    }

    /**
     * Generate and save a summary report.
     *
     * @param reportsDir directory containing comparison-*.md files
     * @param suiteName name of the test suite
     * @return path to saved summary file
     */
    public Path generateAndSave(Path reportsDir, String suiteName) throws IOException {
        String summary = generate(reportsDir, suiteName);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = String.format("%s-summary-%s.md", suiteName.toLowerCase(), timestamp);
        Path outputPath = reportsDir.resolve(filename);

        Files.writeString(outputPath, summary);
        logger.info("Summary saved to: {}", outputPath);

        return outputPath;
    }

    private List<String> readComparisonReports(Path reportsDir) throws IOException {
        if (!Files.exists(reportsDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(reportsDir)) {
            return paths
                    .filter(p -> p.getFileName().toString().startsWith("comparison-"))
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .map(this::readFile)
                    .filter(content -> content != null)
                    .toList();
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            logger.warn("Failed to read {}: {}", path, e.getMessage());
            return null;
        }
    }

    private String buildSummaryPrompt(String suiteName, List<String> reports) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("""
            You are generating a summary report for the %s test suite.

            IMPORTANT CONTEXT:
            - Claude Code is the BENCHMARK - we cannot modify it
            - MiniAgent is OUR agent that we CAN and WILL modify
            - The goal is to identify what MiniAgent should LEARN FROM Claude Code to become "as good as Claude Code"
            - All recommendations must be about improving MiniAgent, NOT about changing Claude Code

            ## Task
            Analyze these comparison reports between MiniAgent and Claude Code and create a comprehensive summary report.

            ## Required Format
            Generate a markdown report with EXACTLY these sections in this order:

            # %s Test Comparison Report

            **Date**: [today's date]
            **Test Suite**: %sComparisonIT
            **Result**: [summarize pass/fail]

            ---

            ## Executive Summary

            | Metric | MiniAgent | Claude Code | Winner |
            |--------|-----------|-------------|--------|
            | **Pass Rate** | x/y | x/y | ... |
            | **Avg Time** | ~Xs | ~Xs | ... (Nx faster) |
            | **Avg Tool Calls** | X | X | ... (Nx fewer) |
            | **Verification** | ... | ... | ... |
            | **Planning (TodoWrite)** | ... | ... | ... |

            ---

            ## Detailed Test Results

            | # | Test Name | MiniAgent | Claude Code | Speed Ratio | Tool Ratio |
            |---|-----------|-----------|-------------|-------------|------------|
            [One row per test with time and calls]

            **Averages**: MiniAgent ~Xs, X calls | Claude Code ~Xs, X calls

            ---

            ## Tool Usage Analysis (Venn Diagram)

            ### Aggregate Tool Usage

            | Tool | MiniAgent | Claude Code | Notes |
            |------|-----------|-------------|-------|
            [Show frequency of each tool, e.g., "12/12" or "0/12"]

            ### Tool Similarity Scores

            | Test | Jaccard Similarity | Tool Gap (Claude Only) |
            |------|-------------------|----------|
            [Calculate Jaccard similarity and list tools Claude used that MiniAgent didn't]

            **Average Similarity**: X%%

            ---

            ## Behavioral Analysis

            ### End Verification Pattern
            [Table showing which agent verified with javac/java at end]

            ### Planning Pattern (TodoWrite)
            [Analyze TodoWrite usage patterns]

            ---

            ## Loss Function Analysis

            Using: `efficiency_loss = 0.5 * (time / baseline_time) + 0.5 * (calls / baseline_calls)`

            | Test | MiniAgent Loss | Claude Loss | Delta |
            |------|---------------|-------------|-------|
            [One row per test]

            **Average Loss**: MiniAgent = X, Claude Code = X

            ---

            ## Key Findings

            ### 1. MiniAgent Strengths
            [Bullet points]

            ### 2. Claude Code Strengths
            [Bullet points]

            ### 3. Key Differences
            [Table comparing approaches]

            ### 4. Tool Gap Analysis
            [What tools Claude uses that MiniAgent doesn't, and WHY]

            ---

            ## Recommendations for MiniAgent
            [Numbered list of prioritized changes TO MINIAGENT to close gaps with Claude Code]
            Remember: We can ONLY modify MiniAgent. Claude Code is the benchmark.

            ---

            ## TL;DR - What To Do Next

            **BOTTOM LINE**: [One sentence summary of how MiniAgent compares to the Claude Code benchmark]

            **What MiniAgent Does Well** (keep these):
            - [Strength 1]
            - [Strength 2]

            **What MiniAgent Should Learn From Claude Code** (gaps to close):
            - [Gap 1 - what Claude does that MiniAgent should adopt]
            - [Gap 2]

            **Action Items for MiniAgent Development** (Priority Order):
            1. [Highest priority change TO MINIAGENT with specific details]
            2. [Second priority change TO MINIAGENT]
            3. [Third priority change TO MINIAGENT]

            **Status**: [MiniAgent is ready for X / MiniAgent needs Y before Z]

            CRITICAL: All action items must be changes to MiniAgent code/prompts/tools.
            Claude Code is the benchmark we're trying to match - we cannot modify it.
            Focus on what MiniAgent should LEARN FROM or ADOPT FROM Claude Code.

            ## Output Instructions
            - Output ONLY the markdown report, nothing else
            - Do NOT use any tools - just output the report as text
            - Do NOT ask questions or request clarification
            - Start your response with "# %s Test Comparison Report"

            ## Comparison Reports

            """, suiteName, suiteName, suiteName, suiteName));

        for (int i = 0; i < reports.size(); i++) {
            sb.append(String.format("### Report %d\n\n%s\n\n---\n\n", i + 1, reports.get(i)));
        }

        sb.append("""

            ## Output Format
            Output a well-formatted markdown report. Use tables where appropriate.
            Be concise but thorough. Focus on actionable insights.
            """);

        return sb.toString();
    }

    /**
     * Main method for command-line usage.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: SummaryReportGenerator <reports-dir> <suite-name>");
            System.out.println("Example: SummaryReportGenerator plans/learnings intermediate");
            System.exit(1);
        }

        Path reportsDir = Path.of(args[0]);
        String suiteName = args[1];

        SummaryReportGenerator generator = new SummaryReportGenerator();
        Path outputPath = generator.generateAndSave(reportsDir, suiteName);

        System.out.println("Summary generated: " + outputPath);
    }
}
