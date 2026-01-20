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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Venn diagram analysis of tool usage between two agent executions.
 *
 * <p>This record computes the three key sets for tool gap analysis:
 * <ul>
 *   <li><b>Shared (A ∩ B)</b>: Tools used by both agents - can compare usage patterns</li>
 *   <li><b>MiniAgent Only (A - B)</b>: Tools only MiniAgent used - possible inefficiency</li>
 *   <li><b>Claude Only (B - A)</b>: Tools only Claude used - <em>candidates for onboarding</em></li>
 * </ul>
 *
 * <p>The "Claude Only" set (B - A) is the most actionable: it identifies tools that
 * helped Claude Code succeed where MiniAgent struggled.
 *
 * <p>Example usage:
 * <pre>{@code
 * ToolUsageComparison comparison = ToolUsageComparison.from(miniAgentSummary, claudeCodeSummary);
 *
 * if (comparison.hasToolGap()) {
 *     Set<String> candidates = comparison.claudeOnly();
 *     // Consider onboarding these tools to MiniAgent
 * }
 *
 * System.out.println(comparison.format());
 * }</pre>
 *
 * @param miniAgentTools set A: unique tool names used by MiniAgent
 * @param claudeCodeTools set B: unique tool names used by Claude Code
 * @param shared A ∩ B: tools used by both agents
 * @param miniAgentOnly A - B: tools only MiniAgent used
 * @param claudeOnly B - A: tools only Claude used (onboarding candidates)
 */
public record ToolUsageComparison(
    Set<String> miniAgentTools,
    Set<String> claudeCodeTools,
    Set<String> shared,
    Set<String> miniAgentOnly,
    Set<String> claudeOnly
) {

    /**
     * Creates a ToolUsageComparison from two execution summaries.
     *
     * @param miniAgent the MiniAgent execution summary
     * @param claudeCode the Claude Code execution summary
     * @return comparison with computed Venn diagram sets
     */
    public static ToolUsageComparison from(ExecutionSummary miniAgent, ExecutionSummary claudeCode) {
        Set<String> a = toToolSet(miniAgent.toolSequence());
        Set<String> b = toToolSet(claudeCode.toolSequence());

        Set<String> shared = intersection(a, b);
        Set<String> aMinusB = difference(a, b);
        Set<String> bMinusA = difference(b, a);

        return new ToolUsageComparison(a, b, shared, aMinusB, bMinusA);
    }

    /**
     * Returns true if Claude Code used tools that MiniAgent did not.
     * This indicates a potential tool gap that could explain performance differences.
     */
    public boolean hasToolGap() {
        return !claudeOnly.isEmpty();
    }

    /**
     * Returns true if MiniAgent used tools that Claude Code did not.
     * This might indicate inefficiency or a different approach.
     */
    public boolean hasMiniAgentExclusiveTools() {
        return !miniAgentOnly.isEmpty();
    }

    /**
     * Returns true if both agents used identical tool sets.
     */
    public boolean identicalToolSets() {
        return miniAgentOnly.isEmpty() && claudeOnly.isEmpty();
    }

    /**
     * Computes the Jaccard similarity coefficient between the tool sets.
     * Returns a value between 0.0 (no overlap) and 1.0 (identical sets).
     *
     * @return Jaccard similarity: |A ∩ B| / |A ∪ B|
     */
    public double jaccardSimilarity() {
        if (miniAgentTools.isEmpty() && claudeCodeTools.isEmpty()) {
            return 1.0; // Both empty = identical
        }
        Set<String> union = new HashSet<>(miniAgentTools);
        union.addAll(claudeCodeTools);
        if (union.isEmpty()) {
            return 1.0;
        }
        return (double) shared.size() / union.size();
    }

    /**
     * Formats the comparison as a human-readable string.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("─── Tool Usage Venn Diagram ─────────────────────────────────\n");
        sb.append(String.format("  MiniAgent tools (A):   %s%n", formatSet(miniAgentTools)));
        sb.append(String.format("  Claude Code tools (B): %s%n", formatSet(claudeCodeTools)));
        sb.append("\n");
        sb.append(String.format("  Shared (A ∩ B):        %s%n", formatSet(shared)));
        sb.append(String.format("  MiniAgent only (A-B):  %s%n", formatSet(miniAgentOnly)));
        sb.append(String.format("  Claude only (B-A):     %s%n", formatSet(claudeOnly)));
        sb.append("\n");

        // Summary insights
        sb.append("  Similarity: ").append(String.format("%.1f%%", jaccardSimilarity() * 100)).append("\n");

        if (hasToolGap()) {
            sb.append("  ⚠️  TOOL GAP: Claude used tools MiniAgent lacks\n");
            sb.append("  📋 Onboarding candidates: ").append(String.join(", ", claudeOnly)).append("\n");
        } else if (identicalToolSets()) {
            sb.append("  ✅ IDENTICAL: Both agents used the same tools\n");
        } else {
            sb.append("  ℹ️  MiniAgent used extra tools not used by Claude\n");
        }

        return sb.toString();
    }

    private static String formatSet(Set<String> set) {
        if (set.isEmpty()) {
            return "(none)";
        }
        return "{" + String.join(", ", set) + "}";
    }

    private static Set<String> toToolSet(List<String> toolSequence) {
        if (toolSequence == null) {
            return Set.of();
        }
        // Normalize tool names to lowercase to avoid false gaps from case differences
        // e.g., "bash" vs "Bash" should be treated as the same tool
        return toolSequence.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        return a.stream()
            .filter(b::contains)
            .collect(Collectors.toSet());
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        return a.stream()
            .filter(t -> !b.contains(t))
            .collect(Collectors.toSet());
    }
}
