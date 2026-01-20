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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agents.harness.test.analysis.SelfCorrectionLoop.IterationResult;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;
import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SelfCorrectionLoop.
 */
@DisplayName("SelfCorrectionLoop Integration Tests")
class SelfCorrectionLoopIT {

    private SelfCorrectionLoop loop;

    @BeforeEach
    void setUp() {
        // Create a mock test runner that returns synthetic comparison reports
        Supplier<List<ComparisonReport>> testRunner = () -> List.of(
                createReport("test-1", List.of("Read", "Write"), List.of("Read", "Write")),
                createReport("test-2", List.of("Glob", "Read"), List.of("Glob", "Read", "TodoWrite")),
                createReport("test-3", List.of("Read"), List.of("Read"))
        );

        loop = new SelfCorrectionLoop(
                new ComparisonAnalysisAgent(Path.of("/home/mark/projects/agent-harness-cli")),
                testRunner
        );
    }

    @Test
    @DisplayName("Should run single correction cycle and analyze tool gaps")
    void runSingleCycle() {
        // When
        IterationResult result = loop.runCycle();

        // Then
        assertThat(result.iteration()).isEqualTo(1);
        assertThat(result.totalTests()).isEqualTo(3);
        assertThat(result.testsWithGaps()).isEqualTo(1); // Only test-2 has a gap
        assertThat(result.similarity()).isGreaterThan(0.8); // 2/3 tests have 100% similarity
        assertThat(result.loss()).isLessThan(0.2);
        assertThat(result.hasAnalysis()).isTrue();

        System.out.println(result.format());
    }

    @Test
    @DisplayName("Should track iteration history")
    void trackHistory() {
        // When
        loop.runCycle();
        loop.runCycle();

        // Then
        List<IterationResult> history = loop.getHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).iteration()).isEqualTo(1);
        assertThat(history.get(1).iteration()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should generate summary report")
    void generateSummary() {
        // Given
        loop.runCycle();

        // When
        String summary = loop.generateSummaryReport();

        // Then
        assertThat(summary).contains("SELF-CORRECTION LOOP SUMMARY");
        assertThat(summary).contains("Total iterations: 1");
        System.out.println(summary);
    }

    @Test
    @DisplayName("Should handle no tool gaps gracefully")
    void noToolGaps() {
        // Given: test runner with no gaps
        Supplier<List<ComparisonReport>> noGapsRunner = () -> List.of(
                createReport("test-1", List.of("Read"), List.of("Read")),
                createReport("test-2", List.of("Glob", "Read"), List.of("Glob", "Read"))
        );
        SelfCorrectionLoop noGapsLoop = new SelfCorrectionLoop(noGapsRunner);

        // When
        IterationResult result = noGapsLoop.runCycle();

        // Then
        assertThat(result.testsWithGaps()).isEqualTo(0);
        assertThat(result.similarity()).isEqualTo(1.0);
        assertThat(result.loss()).isEqualTo(0.0);
        assertThat(result.hasAnalysis()).isFalse(); // No analysis needed
    }

    private ComparisonReport createReport(String name,
                                          List<String> miniAgentTools,
                                          List<String> claudeCodeTools) {
        UseCase useCase = UseCase.builder()
                .name(name)
                .prompt("Test prompt")
                .build();

        List<ToolCallEvent> miniToolCalls = miniAgentTools.stream()
                .map(tool -> ToolCallEvent.success(tool, Map.of(), "output"))
                .toList();
        List<ToolCallEvent> claudeToolCalls = claudeCodeTools.stream()
                .map(tool -> ToolCallEvent.success(tool, Map.of(), "output"))
                .toList();

        ExecutionSummary miniResult = ExecutionSummary.builder()
                .agentId("MiniAgent")
                .toolCalls(miniToolCalls)
                .success(true)
                .durationMs(1000)
                .numTurns(2)
                .build();

        ExecutionSummary claudeResult = ExecutionSummary.builder()
                .agentId("ClaudeCode")
                .toolCalls(claudeToolCalls)
                .success(true)
                .durationMs(1000)
                .numTurns(2)
                .build();

        return new ComparisonReport(useCase, miniResult, claudeResult);
    }
}
