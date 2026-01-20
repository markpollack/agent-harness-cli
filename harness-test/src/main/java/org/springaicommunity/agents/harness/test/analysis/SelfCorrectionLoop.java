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
import org.springaicommunity.agents.harness.test.analysis.ComparisonAnalysisAgent.AnalysisResult;
import org.springaicommunity.agents.harness.test.analysis.ComparisonAnalysisAgent.BatchAnalysisResult;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.comparison.ToolUsageComparison;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Self-correction loop for autonomous MiniAgent improvement.
 *
 * <p>This loop:
 * <ol>
 *   <li>Runs comparison tests between MiniAgent and Claude Code</li>
 *   <li>Analyzes results using ComparisonAnalysisAgent</li>
 *   <li>Identifies tool gaps and root causes</li>
 *   <li>Proposes fixes for human review</li>
 *   <li>Tracks improvement over iterations</li>
 * </ol>
 *
 * <p>Note: Automatic fix application requires human approval in the current version.
 * The loop focuses on analysis and recommendation generation.</p>
 */
public class SelfCorrectionLoop {

    private static final Logger logger = LoggerFactory.getLogger(SelfCorrectionLoop.class);

    private final ComparisonAnalysisAgent analyzer;
    private final Supplier<List<ComparisonReport>> testRunner;
    private final List<IterationResult> history = new ArrayList<>();

    /**
     * Create a self-correction loop with default analyzer.
     *
     * @param testRunner supplier that runs comparison tests and returns reports
     */
    public SelfCorrectionLoop(Supplier<List<ComparisonReport>> testRunner) {
        this(new ComparisonAnalysisAgent(), testRunner);
    }

    /**
     * Create a self-correction loop with custom analyzer and test runner.
     *
     * @param analyzer the comparison analysis agent
     * @param testRunner supplier that runs comparison tests
     */
    public SelfCorrectionLoop(ComparisonAnalysisAgent analyzer, Supplier<List<ComparisonReport>> testRunner) {
        this.analyzer = analyzer;
        this.testRunner = testRunner;
    }

    /**
     * Run a single correction cycle.
     *
     * <p>This performs:
     * <ol>
     *   <li>Run comparison tests</li>
     *   <li>Calculate current loss (1 - average similarity)</li>
     *   <li>Identify reports with tool gaps</li>
     *   <li>Analyze gaps and generate recommendations</li>
     * </ol>
     *
     * @return the iteration result with analysis and recommendations
     */
    public IterationResult runCycle() {
        logger.info("Starting correction cycle #{}", history.size() + 1);

        // 1. Run comparison tests
        logger.info("Running comparison tests...");
        List<ComparisonReport> reports = testRunner.get();
        logger.info("Completed {} tests", reports.size());

        // 2. Calculate current metrics
        double averageSimilarity = calculateAverageSimilarity(reports);
        double loss = 1.0 - averageSimilarity;
        int testsWithGaps = countTestsWithGaps(reports);

        logger.info("Current metrics: similarity={:.1f}%, loss={:.3f}, tests with gaps={}",
                averageSimilarity * 100, loss, testsWithGaps);

        // 3. Analyze results
        BatchAnalysisResult analysis = null;
        if (testsWithGaps > 0) {
            logger.info("Analyzing {} tests with tool gaps...", testsWithGaps);
            analysis = analyzer.analyzeAll(reports);
            logger.info("Analysis complete: {}", analysis.analysis().substring(0, Math.min(200, analysis.analysis().length())));
        } else {
            logger.info("No tool gaps detected - all tests match Claude Code behavior");
        }

        // 4. Create iteration result
        IterationResult result = new IterationResult(
                history.size() + 1,
                Instant.now(),
                reports.size(),
                testsWithGaps,
                averageSimilarity,
                loss,
                analysis,
                calculateImprovement()
        );

        history.add(result);
        return result;
    }

    /**
     * Run multiple correction cycles.
     *
     * @param maxIterations maximum number of cycles to run
     * @param targetLoss target loss to achieve (stop if reached)
     * @return list of iteration results
     */
    public List<IterationResult> runCycles(int maxIterations, double targetLoss) {
        List<IterationResult> results = new ArrayList<>();

        for (int i = 0; i < maxIterations; i++) {
            IterationResult result = runCycle();
            results.add(result);

            if (result.loss() <= targetLoss) {
                logger.info("Target loss {} achieved at iteration {}", targetLoss, i + 1);
                break;
            }

            // If no improvement in last 3 iterations, stop
            if (results.size() >= 3 && noRecentImprovement(results)) {
                logger.info("No improvement in last 3 iterations, stopping");
                break;
            }
        }

        return results;
    }

    /**
     * Get the history of all iterations.
     */
    public List<IterationResult> getHistory() {
        return List.copyOf(history);
    }

    /**
     * Generate a summary report of all iterations.
     */
    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("SELF-CORRECTION LOOP SUMMARY\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append(String.format("Total iterations: %d\n", history.size()));

        if (!history.isEmpty()) {
            IterationResult first = history.get(0);
            IterationResult last = history.get(history.size() - 1);

            sb.append(String.format("Initial loss: %.3f (similarity: %.1f%%)\n",
                    first.loss(), first.similarity() * 100));
            sb.append(String.format("Final loss: %.3f (similarity: %.1f%%)\n",
                    last.loss(), last.similarity() * 100));
            sb.append(String.format("Total improvement: %.1f%%\n",
                    (first.loss() - last.loss()) / first.loss() * 100));
        }

        sb.append("\n─── Iteration History ───────────────────────────────────────────\n");
        for (IterationResult result : history) {
            sb.append(String.format("  #%d: loss=%.3f, similarity=%.1f%%, gaps=%d",
                    result.iteration(),
                    result.loss(),
                    result.similarity() * 100,
                    result.testsWithGaps()));
            if (result.improvement() != 0) {
                sb.append(String.format(" (%+.1f%%)", result.improvement() * 100));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private double calculateAverageSimilarity(List<ComparisonReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        return reports.stream()
                .mapToDouble(r -> r.toolUsageComparison().jaccardSimilarity())
                .average()
                .orElse(0.0);
    }

    private int countTestsWithGaps(List<ComparisonReport> reports) {
        return (int) reports.stream()
                .filter(r -> r.toolUsageComparison().hasToolGap())
                .count();
    }

    private double calculateImprovement() {
        if (history.size() < 2) {
            return 0.0;
        }
        IterationResult prev = history.get(history.size() - 1);
        IterationResult curr = history.get(history.size() - 2);
        return curr.loss() - prev.loss();
    }

    private boolean noRecentImprovement(List<IterationResult> results) {
        int n = results.size();
        double recent = results.get(n - 1).loss();
        double prev1 = results.get(n - 2).loss();
        double prev2 = results.get(n - 3).loss();
        return recent >= prev1 && prev1 >= prev2;
    }

    /**
     * Result of a single correction iteration.
     */
    public record IterationResult(
            int iteration,
            Instant timestamp,
            int totalTests,
            int testsWithGaps,
            double similarity,
            double loss,
            BatchAnalysisResult analysis,
            double improvement
    ) {
        public boolean hasAnalysis() {
            return analysis != null;
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Iteration #%d at %s\n", iteration, timestamp));
            sb.append(String.format("  Tests: %d total, %d with gaps\n", totalTests, testsWithGaps));
            sb.append(String.format("  Similarity: %.1f%%\n", similarity * 100));
            sb.append(String.format("  Loss: %.3f\n", loss));
            if (improvement != 0) {
                sb.append(String.format("  Improvement: %+.1f%%\n", improvement * 100));
            }
            if (hasAnalysis()) {
                sb.append("  Analysis:\n");
                sb.append("    ").append(analysis.analysis().replace("\n", "\n    ")).append("\n");
            }
            return sb.toString();
        }
    }
}
