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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates multiple ComparisonReports and generates a human-readable summary.
 *
 * <p>Use this after running a batch of comparison tests to get a comprehensive
 * overview of how MiniAgent compares to Claude Code across all tests.</p>
 *
 * <p>Example usage:
 * <pre>{@code
 * ComparisonReportSummarizer summarizer = new ComparisonReportSummarizer();
 * for (ComparisonReport report : reports) {
 *     summarizer.add(report);
 * }
 * String summary = summarizer.generateSummary();
 * summarizer.saveTo(Path.of("plans/learnings/summary.md"));
 * }</pre>
 */
public class ComparisonReportSummarizer {

    private final List<ComparisonReport> reports = new ArrayList<>();
    private final String suiteName;

    public ComparisonReportSummarizer() {
        this("Comparison");
    }

    public ComparisonReportSummarizer(String suiteName) {
        this.suiteName = suiteName;
    }

    /**
     * Add a comparison report to the summary.
     */
    public void add(ComparisonReport report) {
        reports.add(report);
    }

    /**
     * Add multiple reports.
     */
    public void addAll(Collection<ComparisonReport> reports) {
        this.reports.addAll(reports);
    }

    /**
     * Generate a comprehensive markdown summary report.
     */
    public String generateSummary() {
        if (reports.isEmpty()) {
            return "# No reports to summarize\n";
        }

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# ").append(suiteName).append(" Test Summary Report\n\n");
        sb.append("**Generated**: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("**Tests**: ").append(reports.size()).append("\n\n");
        sb.append("---\n\n");

        // Executive Summary
        appendExecutiveSummary(sb);

        // Detailed Results Table
        appendDetailedResults(sb);

        // Tool Usage Analysis
        appendToolUsageAnalysis(sb);

        // Behavioral Analysis
        appendBehavioralAnalysis(sb);

        // Loss Function Analysis
        appendLossAnalysis(sb);

        // Tool Gaps
        appendToolGaps(sb);

        // Recommendations
        appendRecommendations(sb);

        return sb.toString();
    }

    private void appendExecutiveSummary(StringBuilder sb) {
        sb.append("## Executive Summary\n\n");

        int miniPassed = (int) reports.stream().filter(r -> r.miniAgent().success()).count();
        int claudePassed = (int) reports.stream().filter(r -> r.claudeCode().success()).count();
        int bothPassed = (int) reports.stream().filter(ComparisonReport::bothPassed).count();
        int bothFailed = (int) reports.stream().filter(ComparisonReport::bothFailed).count();

        double avgMiniTime = reports.stream()
                .mapToLong(r -> r.miniAgent().durationMs())
                .average().orElse(0);
        double avgClaudeTime = reports.stream()
                .mapToLong(r -> r.claudeCode().durationMs())
                .average().orElse(0);

        double avgMiniCalls = reports.stream()
                .mapToInt(r -> r.miniAgent().toolCallCount())
                .average().orElse(0);
        double avgClaudeCalls = reports.stream()
                .mapToInt(r -> r.claudeCode().toolCallCount())
                .average().orElse(0);

        sb.append("| Metric | MiniAgent | Claude Code | Winner |\n");
        sb.append("|--------|-----------|-------------|--------|\n");
        sb.append(String.format("| **Pass Rate** | %d/%d (%.0f%%) | %d/%d (%.0f%%) | %s |\n",
                miniPassed, reports.size(), 100.0 * miniPassed / reports.size(),
                claudePassed, reports.size(), 100.0 * claudePassed / reports.size(),
                miniPassed == claudePassed ? "TIE" : (miniPassed > claudePassed ? "MiniAgent" : "Claude")));
        sb.append(String.format("| **Avg Time** | %.1fs | %.1fs | %s |\n",
                avgMiniTime / 1000, avgClaudeTime / 1000,
                avgMiniTime < avgClaudeTime ? "MiniAgent" : "Claude"));
        sb.append(String.format("| **Avg Tool Calls** | %.1f | %.1f | %s |\n",
                avgMiniCalls, avgClaudeCalls,
                avgMiniCalls < avgClaudeCalls ? "MiniAgent" : "Claude"));
        sb.append(String.format("| **Both Passed** | %d/%d | - | - |\n", bothPassed, reports.size()));
        sb.append(String.format("| **Both Failed** | %d/%d | - | - |\n", bothFailed, reports.size()));

        if (avgClaudeTime > 0) {
            sb.append(String.format("\n**Speed Advantage**: MiniAgent is %.1fx faster on average\n",
                    avgClaudeTime / avgMiniTime));
        }
        if (avgClaudeCalls > 0) {
            sb.append(String.format("**Efficiency Advantage**: MiniAgent uses %.1fx fewer tool calls\n",
                    avgClaudeCalls / avgMiniCalls));
        }
        sb.append("\n---\n\n");
    }

    private void appendDetailedResults(StringBuilder sb) {
        sb.append("## Detailed Test Results\n\n");
        sb.append("| # | Test Name | Result | MiniAgent | Claude Code | Speed Ratio | Tool Ratio |\n");
        sb.append("|---|-----------|--------|-----------|-------------|-------------|------------|\n");

        int testNum = 1;
        for (ComparisonReport report : reports) {
            String result = report.bothPassed() ? "BOTH PASS" :
                    report.bothFailed() ? "BOTH FAIL" :
                    report.onlyMiniAgentPassed() ? "Mini only" : "Claude only";

            String miniInfo = String.format("%.1fs, %d calls",
                    report.miniAgent().durationMs() / 1000.0,
                    report.miniAgent().toolCallCount());
            String claudeInfo = String.format("%.1fs, %d calls",
                    report.claudeCode().durationMs() / 1000.0,
                    report.claudeCode().toolCallCount());

            double speedRatio = report.miniAgent().durationMs() > 0 ?
                    (double) report.claudeCode().durationMs() / report.miniAgent().durationMs() : 0;
            double toolRatio = report.miniAgent().toolCallCount() > 0 ?
                    (double) report.claudeCode().toolCallCount() / report.miniAgent().toolCallCount() : 0;

            sb.append(String.format("| %d | %s | %s | %s | %s | %.1fx | %.1fx |\n",
                    testNum++,
                    report.useCase().name(),
                    result,
                    miniInfo,
                    claudeInfo,
                    speedRatio,
                    toolRatio));
        }
        sb.append("\n---\n\n");
    }

    private void appendToolUsageAnalysis(StringBuilder sb) {
        sb.append("## Tool Usage Analysis\n\n");

        // Aggregate tool usage
        Map<String, Integer> miniToolCounts = new HashMap<>();
        Map<String, Integer> claudeToolCounts = new HashMap<>();

        for (ComparisonReport report : reports) {
            ToolUsageComparison tc = report.toolUsageComparison();
            for (String tool : tc.miniAgentTools()) {
                miniToolCounts.merge(tool, 1, Integer::sum);
            }
            for (String tool : tc.claudeCodeTools()) {
                claudeToolCounts.merge(tool, 1, Integer::sum);
            }
        }

        Set<String> allTools = new TreeSet<>();
        allTools.addAll(miniToolCounts.keySet());
        allTools.addAll(claudeToolCounts.keySet());

        sb.append("### Tool Frequency (tests using each tool)\n\n");
        sb.append("| Tool | MiniAgent | Claude Code | Notes |\n");
        sb.append("|------|-----------|-------------|-------|\n");

        for (String tool : allTools) {
            int miniCount = miniToolCounts.getOrDefault(tool, 0);
            int claudeCount = claudeToolCounts.getOrDefault(tool, 0);
            String notes = "";
            if (miniCount == 0) notes = "Claude only";
            else if (claudeCount == 0) notes = "MiniAgent only";
            else if (claudeCount == reports.size() && miniCount < reports.size()) notes = "Claude always uses";

            sb.append(String.format("| %s | %d/%d | %d/%d | %s |\n",
                    tool, miniCount, reports.size(), claudeCount, reports.size(), notes));
        }

        // Similarity scores
        sb.append("\n### Tool Similarity Scores (Jaccard)\n\n");
        sb.append("| Test | Similarity | Tool Gap |\n");
        sb.append("|------|------------|----------|\n");

        double totalSimilarity = 0;
        for (ComparisonReport report : reports) {
            ToolUsageComparison tc = report.toolUsageComparison();
            totalSimilarity += tc.jaccardSimilarity();
            String gap = tc.claudeOnly().isEmpty() ? "None" : String.join(", ", tc.claudeOnly());
            sb.append(String.format("| %s | %.1f%% | %s |\n",
                    report.useCase().name(),
                    tc.jaccardSimilarity() * 100,
                    gap));
        }

        sb.append(String.format("\n**Average Similarity**: %.1f%%\n",
                totalSimilarity / reports.size() * 100));
        sb.append("\n---\n\n");
    }

    private void appendBehavioralAnalysis(StringBuilder sb) {
        sb.append("## Behavioral Analysis\n\n");

        int miniVerifies = 0, claudeVerifies = 0;
        int miniPlans = 0, claudePlans = 0;
        double totalBehavioralSim = 0;

        sb.append("| Test | Mini Verifies | Claude Verifies | Mini Plans | Claude Plans | Behavioral Sim |\n");
        sb.append("|------|---------------|-----------------|------------|--------------|----------------|\n");

        for (ComparisonReport report : reports) {
            BehavioralPatternAnalyzer.BehavioralAnalysis ba = report.behavioralAnalysis();
            if (ba.miniAgentVerifiesAtEnd()) miniVerifies++;
            if (ba.claudeVerifiesAtEnd()) claudeVerifies++;
            if (ba.miniAgentPlans()) miniPlans++;
            if (ba.claudePlans()) claudePlans++;
            totalBehavioralSim += ba.behavioralSimilarity();

            sb.append(String.format("| %s | %s | %s | %s | %s | %.0f%% |\n",
                    report.useCase().name(),
                    ba.miniAgentVerifiesAtEnd() ? "Yes" : "-",
                    ba.claudeVerifiesAtEnd() ? "Yes" : "-",
                    ba.miniAgentPlans() ? "Yes" : "-",
                    ba.claudePlans() ? "Yes" : "-",
                    ba.behavioralSimilarity() * 100));
        }

        sb.append(String.format("\n**Summary**: MiniAgent verifies %d/%d, plans %d/%d | ",
                miniVerifies, reports.size(), miniPlans, reports.size()));
        sb.append(String.format("Claude verifies %d/%d, plans %d/%d\n",
                claudeVerifies, reports.size(), claudePlans, reports.size()));
        sb.append(String.format("**Avg Behavioral Similarity**: %.1f%%\n",
                totalBehavioralSim / reports.size() * 100));
        sb.append("\n---\n\n");
    }

    private void appendLossAnalysis(StringBuilder sb) {
        sb.append("## Loss Function Analysis\n\n");
        sb.append("Using efficiency-based loss: `loss = 0.5 * (time/baseline) + 0.5 * (calls/baseline)`\n\n");

        // Find baselines (minimum values)
        long minTime = reports.stream()
                .mapToLong(r -> Math.min(r.miniAgent().durationMs(), r.claudeCode().durationMs()))
                .min().orElse(1);
        int minCalls = reports.stream()
                .mapToInt(r -> Math.min(r.miniAgent().toolCallCount(), r.claudeCode().toolCallCount()))
                .min().orElse(1);

        sb.append("| Test | MiniAgent Loss | Claude Loss | Delta |\n");
        sb.append("|------|---------------|-------------|-------|\n");

        double totalMiniLoss = 0, totalClaudeLoss = 0;
        for (ComparisonReport report : reports) {
            double miniLoss = 0.5 * ((double) report.miniAgent().durationMs() / minTime) +
                    0.5 * ((double) report.miniAgent().toolCallCount() / minCalls);
            double claudeLoss = 0.5 * ((double) report.claudeCode().durationMs() / minTime) +
                    0.5 * ((double) report.claudeCode().toolCallCount() / minCalls);

            // Normalize to 0-1 range (approx)
            miniLoss = Math.min(1.0, miniLoss / 10);
            claudeLoss = Math.min(1.0, claudeLoss / 10);

            totalMiniLoss += miniLoss;
            totalClaudeLoss += claudeLoss;

            double delta = miniLoss - claudeLoss;
            String deltaStr = delta < 0 ?
                    String.format("%.2f (Mini better)", delta) :
                    String.format("+%.2f (Claude better)", delta);

            sb.append(String.format("| %s | %.2f | %.2f | %s |\n",
                    report.useCase().name(), miniLoss, claudeLoss, deltaStr));
        }

        sb.append(String.format("\n**Average Loss**: MiniAgent = %.2f, Claude = %.2f\n",
                totalMiniLoss / reports.size(), totalClaudeLoss / reports.size()));
        sb.append(String.format("**Delta**: %.2f (negative = MiniAgent more efficient)\n",
                (totalMiniLoss - totalClaudeLoss) / reports.size()));
        sb.append("\n---\n\n");
    }

    private void appendToolGaps(StringBuilder sb) {
        sb.append("## Tool Gaps (Claude uses, MiniAgent doesn't)\n\n");

        Map<String, List<String>> gapsByTool = new HashMap<>();
        for (ComparisonReport report : reports) {
            for (String tool : report.toolUsageComparison().claudeOnly()) {
                gapsByTool.computeIfAbsent(tool, k -> new ArrayList<>())
                        .add(report.useCase().name());
            }
        }

        if (gapsByTool.isEmpty()) {
            sb.append("No tool gaps detected - MiniAgent uses all tools Claude uses.\n\n");
        } else {
            sb.append("| Tool | Tests Where Claude Used It | Count |\n");
            sb.append("|------|---------------------------|-------|\n");

            gapsByTool.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .forEach(entry -> {
                        sb.append(String.format("| %s | %s | %d |\n",
                                entry.getKey(),
                                entry.getValue().size() > 3 ?
                                        entry.getValue().subList(0, 3).stream().collect(Collectors.joining(", ")) + "..." :
                                        String.join(", ", entry.getValue()),
                                entry.getValue().size()));
                    });
        }
        sb.append("\n---\n\n");
    }

    private void appendRecommendations(StringBuilder sb) {
        sb.append("## Recommendations\n\n");

        int bothPassed = (int) reports.stream().filter(ComparisonReport::bothPassed).count();
        int miniOnly = (int) reports.stream().filter(ComparisonReport::onlyMiniAgentPassed).count();
        int claudeOnly = (int) reports.stream().filter(ComparisonReport::onlyClaudeCodePassed).count();

        if (bothPassed == reports.size()) {
            sb.append("1. **Excellent**: All tests pass for both agents\n");
        } else if (claudeOnly > 0) {
            sb.append(String.format("1. **Gap Identified**: Claude passed %d tests MiniAgent failed - investigate\n", claudeOnly));
        }

        // Check for TodoWrite gap
        long todoWriteGap = reports.stream()
                .filter(r -> r.toolUsageComparison().claudeOnly().contains("todowrite"))
                .count();
        if (todoWriteGap > reports.size() / 2) {
            sb.append("2. **TodoWrite**: Claude uses it extensively but MiniAgent doesn't - this is a style choice, not a capability gap\n");
        }

        // Check verification patterns
        long miniVerifies = reports.stream()
                .filter(r -> r.behavioralAnalysis().miniAgentVerifiesAtEnd())
                .count();
        long claudeVerifies = reports.stream()
                .filter(r -> r.behavioralAnalysis().claudeVerifiesAtEnd())
                .count();
        if (claudeVerifies > miniVerifies + 2) {
            sb.append("3. **Verification**: Consider adding more end-verification to MiniAgent's approach\n");
        }

        sb.append("\n");
    }

    /**
     * Save summary to a file.
     */
    public void saveTo(Path path) throws IOException {
        Files.writeString(path, generateSummary());
    }

    /**
     * Get the number of reports collected.
     */
    public int getReportCount() {
        return reports.size();
    }

    /**
     * Get all collected reports.
     */
    public List<ComparisonReport> getReports() {
        return Collections.unmodifiableList(reports);
    }
}
