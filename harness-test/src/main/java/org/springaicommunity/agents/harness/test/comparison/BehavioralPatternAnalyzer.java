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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyzes behavioral patterns in tool sequences beyond simple presence/absence.
 *
 * <p>While {@link ToolUsageComparison} uses Venn diagrams to show WHAT tools are used,
 * this analyzer examines HOW and WHEN tools are used - detecting patterns like:
 * <ul>
 *   <li>End verification - does sequence end with compilation/test check?</li>
 *   <li>Tool purpose - is bash used for exploration or verification?</li>
 *   <li>Sequence similarity - similar flow despite different tools?</li>
 * </ul>
 */
public class BehavioralPatternAnalyzer {

    private static final Set<String> VERIFICATION_COMMANDS = Set.of(
            "javac", "java ", "npm test", "pytest", "go test", "mvn test",
            "gradle test", "make test", "cargo test", "dotnet test"
    );

    private static final Set<String> EXPLORATION_COMMANDS = Set.of(
            "ls", "find", "cat", "head", "tail", "wc", "file", "pwd"
    );

    private static final Pattern COMPILE_PATTERN = Pattern.compile(
            "(?i)(javac|gcc|g\\+\\+|rustc|go build|npm run build|tsc|dotnet build)"
    );

    private static final Pattern TEST_PATTERN = Pattern.compile(
            "(?i)(test|spec|check|verify|assert)"
    );

    /**
     * Analyze behavioral patterns in a comparison report.
     */
    public BehavioralAnalysis analyze(ComparisonReport report) {
        List<String> miniAgentSeq = report.miniAgent().toolSequence();
        List<String> claudeSeq = report.claudeCode().toolSequence();

        boolean miniAgentVerifies = hasEndVerification(miniAgentSeq);
        boolean claudeVerifies = hasEndVerification(claudeSeq);

        List<BehavioralGap> gaps = new ArrayList<>();

        // Check verification pattern gap
        if (claudeVerifies && !miniAgentVerifies) {
            gaps.add(new BehavioralGap(
                    GapType.MISSING_END_VERIFICATION,
                    "Claude Code verifies at end, MiniAgent does not",
                    "Add verification step (javac/test) after code changes"
            ));
        }

        // Check if MiniAgent verifies early but not late
        if (hasEarlyVerification(miniAgentSeq) && !miniAgentVerifies) {
            gaps.add(new BehavioralGap(
                    GapType.EARLY_ONLY_VERIFICATION,
                    "MiniAgent runs verification early (exploration) but not at end (confirmation)",
                    "Run verification again after making changes"
            ));
        }

        // Check planning pattern (TodoWrite at start)
        boolean claudePlans = startsWithPlanning(claudeSeq);
        boolean miniAgentPlans = startsWithPlanning(miniAgentSeq);
        if (claudePlans && !miniAgentPlans) {
            gaps.add(new BehavioralGap(
                    GapType.MISSING_PLANNING,
                    "Claude Code starts with planning (TodoWrite), MiniAgent does not",
                    "Consider using TodoWrite for multi-step tasks"
            ));
        }

        // Calculate behavioral similarity (beyond just tool presence)
        double behavioralSimilarity = calculateBehavioralSimilarity(miniAgentSeq, claudeSeq);

        return new BehavioralAnalysis(
                miniAgentVerifies,
                claudeVerifies,
                miniAgentPlans,
                claudePlans,
                behavioralSimilarity,
                gaps
        );
    }

    /**
     * Check if sequence ends with a verification step (compile/test).
     */
    public boolean hasEndVerification(List<String> sequence) {
        if (sequence.isEmpty()) return false;

        // Check last 2 tools (verification might be second-to-last before Submit)
        int checkCount = Math.min(2, sequence.size());
        for (int i = sequence.size() - checkCount; i < sequence.size(); i++) {
            String tool = sequence.get(i).toLowerCase();
            if (isVerificationTool(tool)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if sequence has verification early (exploration phase).
     */
    public boolean hasEarlyVerification(List<String> sequence) {
        if (sequence.size() < 3) return false;

        // Check first third of sequence
        int earlyEnd = sequence.size() / 3;
        for (int i = 0; i <= earlyEnd; i++) {
            String tool = sequence.get(i).toLowerCase();
            if (isVerificationTool(tool)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if sequence starts with planning (TodoWrite).
     */
    public boolean startsWithPlanning(List<String> sequence) {
        if (sequence.isEmpty()) return false;
        String first = sequence.get(0).toLowerCase();
        return first.contains("todo") || first.contains("plan");
    }

    /**
     * Classify a tool call's purpose based on position and content.
     */
    public ToolPurpose classifyToolPurpose(String tool, String input, int position, int totalCalls) {
        String toolLower = tool.toLowerCase();
        String inputLower = input != null ? input.toLowerCase() : "";

        // Bash tool purpose depends on command
        if (toolLower.contains("bash")) {
            if (COMPILE_PATTERN.matcher(inputLower).find()) {
                // Early = exploration, late = verification
                double relativePosition = (double) position / totalCalls;
                return relativePosition > 0.7 ? ToolPurpose.VERIFICATION : ToolPurpose.EXPLORATION;
            }
            if (TEST_PATTERN.matcher(inputLower).find()) {
                return ToolPurpose.VERIFICATION;
            }
            if (EXPLORATION_COMMANDS.stream().anyMatch(inputLower::contains)) {
                return ToolPurpose.EXPLORATION;
            }
        }

        // TodoWrite is planning (check before "write" to avoid misclassification)
        if (toolLower.contains("todo")) {
            return ToolPurpose.PLANNING;
        }

        // Read/Glob/Grep are exploration
        if (toolLower.contains("read") || toolLower.contains("glob") || toolLower.contains("grep")) {
            return ToolPurpose.EXPLORATION;
        }

        // Write/Edit are modification
        if (toolLower.contains("write") || toolLower.contains("edit")) {
            return ToolPurpose.MODIFICATION;
        }

        return ToolPurpose.UNKNOWN;
    }

    private boolean isVerificationTool(String tool) {
        if (tool.contains("bash")) {
            // Need to check command content, but we only have tool name here
            // Assume bash at end is verification
            return true;
        }
        return VERIFICATION_COMMANDS.stream().anyMatch(tool::contains);
    }

    private double calculateBehavioralSimilarity(List<String> seqA, List<String> seqB) {
        if (seqA.isEmpty() && seqB.isEmpty()) return 1.0;
        if (seqA.isEmpty() || seqB.isEmpty()) return 0.0;

        // Compare phase patterns: exploration -> modification -> verification
        List<ToolPurpose> phasesA = extractPhases(seqA);
        List<ToolPurpose> phasesB = extractPhases(seqB);

        // Simple phase similarity
        int matches = 0;
        int minLen = Math.min(phasesA.size(), phasesB.size());
        for (int i = 0; i < minLen; i++) {
            if (phasesA.get(i) == phasesB.get(i)) {
                matches++;
            }
        }

        int maxLen = Math.max(phasesA.size(), phasesB.size());
        return maxLen > 0 ? (double) matches / maxLen : 0.0;
    }

    private List<ToolPurpose> extractPhases(List<String> sequence) {
        List<ToolPurpose> phases = new ArrayList<>();
        for (int i = 0; i < sequence.size(); i++) {
            phases.add(classifyToolPurpose(sequence.get(i), null, i, sequence.size()));
        }
        return phases;
    }

    /**
     * Result of behavioral analysis.
     */
    public record BehavioralAnalysis(
            boolean miniAgentVerifiesAtEnd,
            boolean claudeVerifiesAtEnd,
            boolean miniAgentPlans,
            boolean claudePlans,
            double behavioralSimilarity,
            List<BehavioralGap> gaps
    ) {
        public boolean hasGaps() {
            return !gaps.isEmpty();
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("─── Behavioral Analysis ─────────────────────────────────\n");
            sb.append(String.format("  End Verification: MiniAgent=%s, Claude=%s\n",
                    miniAgentVerifiesAtEnd ? "✅" : "❌",
                    claudeVerifiesAtEnd ? "✅" : "❌"));
            sb.append(String.format("  Planning: MiniAgent=%s, Claude=%s\n",
                    miniAgentPlans ? "✅" : "❌",
                    claudePlans ? "✅" : "❌"));
            sb.append(String.format("  Behavioral Similarity: %.1f%%\n", behavioralSimilarity * 100));

            if (!gaps.isEmpty()) {
                sb.append("\n  ⚠️  BEHAVIORAL GAPS:\n");
                for (BehavioralGap gap : gaps) {
                    sb.append(String.format("    • %s: %s\n", gap.type(), gap.description()));
                    sb.append(String.format("      Fix: %s\n", gap.recommendation()));
                }
            }
            return sb.toString();
        }
    }

    /**
     * A behavioral gap between agents.
     */
    public record BehavioralGap(
            GapType type,
            String description,
            String recommendation
    ) {}

    /**
     * Types of behavioral gaps.
     */
    public enum GapType {
        MISSING_END_VERIFICATION,
        EARLY_ONLY_VERIFICATION,
        MISSING_PLANNING,
        DIFFERENT_PHASE_ORDER
    }

    /**
     * Purpose classification for tool calls.
     */
    public enum ToolPurpose {
        EXPLORATION,    // Reading, searching, understanding
        MODIFICATION,   // Writing, editing code
        VERIFICATION,   // Compiling, testing
        PLANNING,       // TodoWrite, organizing
        UNKNOWN
    }
}
