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

import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;
import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Report comparing executions from two different agents on the same task.
 *
 * <p>This report helps identify:
 * <ul>
 *   <li>Test design issues (both fail/pass in similar ways)</li>
 *   <li>Agent capability gaps (one succeeds, one fails)</li>
 *   <li>Different approaches (both succeed, different tool sequences)</li>
 *   <li><b>Tool gaps</b> (Claude uses tools MiniAgent lacks - onboarding candidates)</li>
 * </ul>
 *
 * @param useCase the use case that was tested
 * @param miniAgent execution summary from MiniAgent
 * @param claudeCode execution summary from Claude Code
 */
public record ComparisonReport(
    UseCase useCase,
    ExecutionSummary miniAgent,
    ExecutionSummary claudeCode
) {

    /**
     * Gets the tool usage Venn diagram comparison.
     * This identifies shared tools, exclusive tools, and onboarding candidates.
     */
    public ToolUsageComparison toolUsageComparison() {
        return ToolUsageComparison.from(miniAgent, claudeCode);
    }

    /**
     * Returns true if both agents succeeded.
     */
    public boolean bothPassed() {
        return miniAgent.success() && claudeCode.success();
    }

    /**
     * Returns true if both agents failed.
     */
    public boolean bothFailed() {
        return !miniAgent.success() && !claudeCode.success();
    }

    /**
     * Returns true if only MiniAgent succeeded.
     */
    public boolean onlyMiniAgentPassed() {
        return miniAgent.success() && !claudeCode.success();
    }

    /**
     * Returns true if only Claude Code succeeded.
     */
    public boolean onlyClaudeCodePassed() {
        return !miniAgent.success() && claudeCode.success();
    }

    /**
     * Analyzes key differences between the two executions.
     */
    public List<String> analyzeDifferences() {
        List<String> differences = new ArrayList<>();

        // Success status
        if (miniAgent.success() != claudeCode.success()) {
            String who = miniAgent.success() ? "MiniAgent" : "ClaudeCode";
            differences.add("Only " + who + " succeeded");
        }

        // Tool count difference
        int miniTools = miniAgent.toolCallCount();
        int claudeTools = claudeCode.toolCallCount();
        if (Math.abs(miniTools - claudeTools) > 2) {
            differences.add(String.format("Tool call count differs: MiniAgent=%d, ClaudeCode=%d",
                miniTools, claudeTools));
        }

        // Token usage comparison (thinking tokens are key)
        if (claudeCode.thinkingTokens() > 0 && miniAgent.thinkingTokens() == 0) {
            differences.add("Claude Code used thinking tokens, MiniAgent did not");
        }

        // Turn count difference
        if (Math.abs(miniAgent.numTurns() - claudeCode.numTurns()) > 3) {
            differences.add(String.format("Turn count differs: MiniAgent=%d, ClaudeCode=%d",
                miniAgent.numTurns(), claudeCode.numTurns()));
        }

        // Tool sequence comparison
        List<String> miniSequence = miniAgent.toolSequence();
        List<String> claudeSequence = claudeCode.toolSequence();
        if (!miniSequence.isEmpty() && !claudeSequence.isEmpty()) {
            // Compare first tool used
            if (!miniSequence.get(0).equals(claudeSequence.get(0))) {
                differences.add(String.format("First tool differs: MiniAgent=%s, ClaudeCode=%s",
                    miniSequence.get(0), claudeSequence.get(0)));
            }
        }

        // Tool gap analysis (Venn diagram)
        ToolUsageComparison toolComparison = toolUsageComparison();
        if (toolComparison.hasToolGap()) {
            differences.add("TOOL GAP: Claude used tools MiniAgent lacks: " +
                String.join(", ", toolComparison.claudeOnly()));
        }

        return differences;
    }

    /**
     * Generates an insight based on the comparison.
     */
    public String generateInsight() {
        if (bothPassed()) {
            List<String> diffs = analyzeDifferences();
            if (diffs.isEmpty()) {
                return "IDENTICAL: Both agents succeeded with similar approaches";
            }
            return "BOTH PASSED with differences: " + String.join("; ", diffs);
        }

        if (bothFailed()) {
            return "BOTH FAILED: Likely a test design issue - consider revising success criteria";
        }

        if (onlyClaudeCodePassed()) {
            return "MINIAGENT GAP: Claude Code succeeded, MiniAgent failed - investigate approach difference";
        }

        if (onlyMiniAgentPassed()) {
            return "UNEXPECTED: MiniAgent succeeded but Claude Code failed - unusual case";
        }

        return "UNKNOWN: Unable to determine insight";
    }

    /**
     * Prints a formatted comparison report.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("COMPARISON REPORT: ").append(useCase.name()).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        // Summary line
        sb.append("RESULT: ");
        if (bothPassed()) {
            sb.append("✅ BOTH PASSED\n");
        } else if (bothFailed()) {
            sb.append("❌ BOTH FAILED\n");
        } else if (onlyClaudeCodePassed()) {
            sb.append("⚠️  ONLY CLAUDE CODE PASSED\n");
        } else {
            sb.append("⚠️  ONLY MINIAGENT PASSED\n");
        }
        sb.append("\n");

        // Insight
        sb.append("INSIGHT: ").append(generateInsight()).append("\n\n");

        // MiniAgent summary
        sb.append("─── MiniAgent ───────────────────────────────────────────────\n");
        sb.append(miniAgent.format());
        sb.append("\n");

        // Claude Code summary
        sb.append("─── Claude Code ─────────────────────────────────────────────\n");
        sb.append(claudeCode.format());
        sb.append("\n");

        // Differences
        List<String> diffs = analyzeDifferences();
        if (!diffs.isEmpty()) {
            sb.append("─── Key Differences ─────────────────────────────────────────\n");
            for (String diff : diffs) {
                sb.append("  • ").append(diff).append("\n");
            }
            sb.append("\n");
        }

        // Tool sequence comparison
        sb.append("─── Tool Sequences ──────────────────────────────────────────\n");
        sb.append("MiniAgent:   ");
        if (miniAgent.toolSequence().isEmpty()) {
            sb.append("(no tools)\n");
        } else {
            sb.append(String.join(" → ", miniAgent.toolSequence())).append("\n");
        }
        sb.append("ClaudeCode:  ");
        if (claudeCode.toolSequence().isEmpty()) {
            sb.append("(no tools)\n");
        } else {
            sb.append(String.join(" → ", claudeCode.toolSequence())).append("\n");
        }
        sb.append("\n");

        // Tool usage Venn diagram
        sb.append(toolUsageComparison().format());

        sb.append("\n═══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
