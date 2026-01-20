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

import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ToolUsageComparison.
 */
class ToolUsageComparisonTest {

    @Test
    void fromComputesVennSetsCorrectly() {
        // MiniAgent uses: Read, Write, Edit
        ExecutionSummary miniAgent = createSummary("MiniAgent",
            List.of("Read", "Write", "Read", "Edit"));

        // Claude uses: Read, Bash, TodoWrite
        ExecutionSummary claudeCode = createSummary("ClaudeCode",
            List.of("Read", "Bash", "Read", "TodoWrite"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        // A = {read, write, edit} (normalized to lowercase)
        assertThat(comparison.miniAgentTools()).containsExactlyInAnyOrder("read", "write", "edit");
        // B = {read, bash, todowrite} (normalized to lowercase)
        assertThat(comparison.claudeCodeTools()).containsExactlyInAnyOrder("read", "bash", "todowrite");
        // A ∩ B = {read}
        assertThat(comparison.shared()).containsExactly("read");
        // A - B = {write, edit}
        assertThat(comparison.miniAgentOnly()).containsExactlyInAnyOrder("write", "edit");
        // B - A = {bash, todowrite}
        assertThat(comparison.claudeOnly()).containsExactlyInAnyOrder("bash", "todowrite");
    }

    @Test
    void caseInsensitiveComparison() {
        // MiniAgent uses lowercase "bash"
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("bash", "read"));
        // Claude uses capitalized "Bash"
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Bash", "Read"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        // Both should normalize to same set, no gaps
        assertThat(comparison.identicalToolSets()).isTrue();
        assertThat(comparison.hasToolGap()).isFalse();
        assertThat(comparison.shared()).containsExactlyInAnyOrder("bash", "read");
    }

    @Test
    void hasToolGapReturnsTrueWhenClaudeHasExclusiveTools() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read", "TodoWrite"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.hasToolGap()).isTrue();
        assertThat(comparison.claudeOnly()).containsExactly("todowrite"); // lowercase
    }

    @Test
    void hasToolGapReturnsFalseWhenNoGap() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.hasToolGap()).isFalse();
        assertThat(comparison.claudeOnly()).isEmpty();
    }

    @Test
    void identicalToolSetsReturnsTrueWhenSameTools() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write", "Read"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Write", "Read"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.identicalToolSets()).isTrue();
        assertThat(comparison.miniAgentOnly()).isEmpty();
        assertThat(comparison.claudeOnly()).isEmpty();
    }

    @Test
    void jaccardSimilarityComputesCorrectly() {
        // A = {Read, Write, Edit}, B = {Read, Bash}
        // A ∩ B = {Read}, size = 1
        // A ∪ B = {Read, Write, Edit, Bash}, size = 4
        // Jaccard = 1/4 = 0.25
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write", "Edit"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read", "Bash"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.jaccardSimilarity()).isCloseTo(0.25, within(0.001));
    }

    @Test
    void jaccardSimilarityIsOneForIdenticalSets() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read", "Write"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.jaccardSimilarity()).isEqualTo(1.0);
    }

    @Test
    void jaccardSimilarityIsZeroForDisjointSets() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Bash", "TodoWrite"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.jaccardSimilarity()).isEqualTo(0.0);
    }

    @Test
    void jaccardSimilarityIsOneForBothEmpty() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of());
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of());

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.jaccardSimilarity()).isEqualTo(1.0);
    }

    @Test
    void formatIncludesAllSets() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read", "Bash"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);
        String formatted = comparison.format();

        assertThat(formatted).contains("Tool Usage Venn Diagram");
        assertThat(formatted).contains("MiniAgent tools (A):");
        assertThat(formatted).contains("Claude Code tools (B):");
        assertThat(formatted).contains("Shared (A ∩ B):");
        assertThat(formatted).contains("MiniAgent only (A-B):");
        assertThat(formatted).contains("Claude only (B-A):");
        assertThat(formatted).contains("Similarity:");
    }

    @Test
    void formatShowsToolGapWarning() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read", "TodoWrite"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);
        String formatted = comparison.format();

        assertThat(formatted).contains("TOOL GAP");
        assertThat(formatted).contains("Onboarding candidates");
        assertThat(formatted).contains("todowrite"); // lowercase due to normalization
    }

    @Test
    void formatShowsIdenticalWhenSameTools() {
        ExecutionSummary miniAgent = createSummary("MiniAgent", List.of("Read", "Write"));
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Write", "Read"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);
        String formatted = comparison.format();

        assertThat(formatted).contains("IDENTICAL");
    }

    @Test
    void handlesNullToolSequence() {
        ExecutionSummary miniAgent = ExecutionSummary.builder()
            .agentId("MiniAgent")
            .toolCalls(null)
            .success(true)
            .build();
        ExecutionSummary claudeCode = createSummary("ClaudeCode", List.of("Read"));

        ToolUsageComparison comparison = ToolUsageComparison.from(miniAgent, claudeCode);

        assertThat(comparison.miniAgentTools()).isEmpty();
        assertThat(comparison.claudeCodeTools()).containsExactly("read"); // lowercase
        assertThat(comparison.hasToolGap()).isTrue();
    }

    private ExecutionSummary createSummary(String agentId, List<String> toolNames) {
        List<ToolCallEvent> toolCalls = toolNames.stream()
            .map(name -> new ToolCallEvent(name, Map.of(), "output", true))
            .toList();

        return ExecutionSummary.builder()
            .agentId(agentId)
            .toolCalls(toolCalls)
            .success(true)
            .durationMs(1000)
            .build();
    }
}
