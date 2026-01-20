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
import org.springaicommunity.agents.harness.test.analysis.ComparisonAnalysisAgent.AnalysisResult;
import org.springaicommunity.agents.harness.test.analysis.ComparisonAnalysisAgent.BatchAnalysisResult;
import org.springaicommunity.agents.harness.test.analysis.ComparisonAnalysisAgent.RootCause;
import org.springaicommunity.agents.harness.test.comparison.ComparisonReport;
import org.springaicommunity.agents.harness.test.tracking.ExecutionSummary;
import org.springaicommunity.agents.harness.test.tracking.ToolCallEvent;
import org.springaicommunity.agents.harness.test.usecase.UseCase;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ComparisonAnalysisAgent.
 *
 * <p>These tests require ANTHROPIC_API_KEY to be set since they use
 * Claude Code (via claude-agent-sdk-java) for analysis.</p>
 */
@DisplayName("ComparisonAnalysisAgent Integration Tests")
class ComparisonAnalysisAgentIT {

    private ComparisonAnalysisAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ComparisonAnalysisAgent(Path.of("/home/mark/projects/agent-harness-cli"));
    }

    @Test
    @DisplayName("Should return noGap result when tools are identical")
    void analyzeIdenticalTools() {
        // Given: A comparison report with identical tool usage
        ComparisonReport report = createReport(
                "test-identical",
                List.of("Read", "Write", "Bash"),  // MiniAgent
                List.of("Read", "Write", "Bash")   // Claude Code
        );

        // When
        AnalysisResult result = agent.analyze(report);

        // Then
        assertThat(result.hasToolGap()).isFalse();
        assertThat(result.rootCause()).isEqualTo(RootCause.NONE);
        assertThat(result.similarity()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should identify tool gap when Claude uses additional tools")
    void analyzeWithToolGap() {
        // Given: Claude uses TodoWrite but MiniAgent doesn't
        ComparisonReport report = createReport(
                "test-with-gap",
                List.of("Glob", "Read"),                  // MiniAgent
                List.of("Glob", "Read", "TodoWrite")      // Claude Code
        );

        // When
        AnalysisResult result = agent.analyze(report);

        // Then
        assertThat(result.hasToolGap()).isTrue();
        assertThat(result.toolGaps().stream().map(String::toLowerCase).toList())
                .contains("todowrite");
        assertThat(result.analysis()).isNotEmpty();
        System.out.println("Analysis result: " + result.analysis());
    }

    @Test
    @DisplayName("Should analyze batch of reports and identify patterns")
    void analyzeBatch() {
        // Given: Multiple reports with various tool gaps
        List<ComparisonReport> reports = List.of(
                createReport("test-1",
                        List.of("Read"),
                        List.of("Read", "TodoWrite")),
                createReport("test-2",
                        List.of("Glob", "Read"),
                        List.of("Glob", "Read", "TodoWrite")),
                createReport("test-3",
                        List.of("Read", "Write"),
                        List.of("Read", "Write"))
        );

        // When
        BatchAnalysisResult result = agent.analyzeAll(reports);

        // Then
        assertThat(result.totalTests()).isEqualTo(3);
        assertThat(result.testsWithGaps()).isEqualTo(2);
        assertThat(result.allToolGaps().stream().map(String::toLowerCase).toList())
                .contains("todowrite");
        assertThat(result.analysis()).isNotEmpty();
        System.out.println("Batch analysis: " + result.analysis());
    }

    private ComparisonReport createReport(String name,
                                          List<String> miniAgentToolSequence,
                                          List<String> claudeCodeToolSequence) {
        UseCase useCase = UseCase.builder()
                .name(name)
                .prompt("Test prompt")
                .category("test")
                .maxTurns(10)
                .timeoutSeconds(60)
                .build();

        // Create tool call events from sequence
        List<ToolCallEvent> miniToolCalls = miniAgentToolSequence.stream()
                .map(tool -> ToolCallEvent.success(tool, Map.of(), "output"))
                .toList();
        List<ToolCallEvent> claudeToolCalls = claudeCodeToolSequence.stream()
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
