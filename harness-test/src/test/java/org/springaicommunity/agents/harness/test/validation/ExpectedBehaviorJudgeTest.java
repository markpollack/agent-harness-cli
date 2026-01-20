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

package org.springaicommunity.agents.harness.test.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExpectedBehaviorJudge.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpectedBehaviorJudgeTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private ExpectedBehaviorJudge judge;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        judge = new ExpectedBehaviorJudge("The agent should read the file and return its contents", chatClientBuilder);
    }

    @Test
    void shouldParseHighScoreAsPassing() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 9
            Reasoning: The agent correctly read the file and returned its contents. Minor formatting differences but semantically correct.
            """);

        JudgmentContext context = createContext("Read file.txt", "File contents: Hello World");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
        assertThat(judgment.score()).isInstanceOf(NumericalScore.class);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(9.0);
        assertThat(judgment.reasoning()).contains("correctly read");
        assertThat((Double) judgment.metadata().get("loss")).isCloseTo(0.1, within(0.001));
    }

    @Test
    void shouldParseLowScoreAsFailing() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 3
            Reasoning: The agent attempted to read the file but returned an error instead of the contents.
            """);

        JudgmentContext context = createContext("Read file.txt", "Error: File not found");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(3.0);
        assertThat(judgment.metadata().get("loss")).isEqualTo(0.7);
    }

    @Test
    void shouldHandlePerfectScore() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 10
            Reasoning: Perfect execution. The agent read the file and returned exactly the expected output.
            """);

        JudgmentContext context = createContext("Read file.txt", "Hello World");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(10.0);
        assertThat(judgment.metadata().get("loss")).isEqualTo(0.0);
    }

    @Test
    void shouldHandleZeroScore() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 0
            Reasoning: Complete failure. The agent did not attempt the task at all.
            """);

        JudgmentContext context = createContext("Read file.txt", "");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(0.0);
        assertThat(judgment.metadata().get("loss")).isEqualTo(1.0);
    }

    @Test
    void shouldHandleDecimalScores() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 7.5
            Reasoning: Mostly correct with some minor issues.
            """);

        JudgmentContext context = createContext("Read file.txt", "Some output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(7.5);
        assertThat(judgment.metadata().get("loss")).isEqualTo(0.25);
    }

    @Test
    void shouldDefaultToMiddleScoreOnParsingFailure() {
        // Given
        when(responseSpec.content()).thenReturn("""
            The agent did okay but not great. I'd give it a passing grade.
            """);

        JudgmentContext context = createContext("Read file.txt", "Some output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        // Default to 5.0 when score cannot be parsed
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(5.0);
    }

    @Test
    void shouldClampScoreAboveTen() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 15
            Reasoning: Outstanding work!
            """);

        JudgmentContext context = createContext("Read file.txt", "Perfect output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(10.0);
    }

    @Test
    void shouldClampScoreBelowZero() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: -5
            Reasoning: Terrible work!
            """);

        JudgmentContext context = createContext("Read file.txt", "Wrong output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(0.0);
    }

    @Test
    void shouldExtractReasoningAfterScoreLine() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 8
            This is the reasoning that follows the score.
            It spans multiple lines.
            """);

        JudgmentContext context = createContext("Read file.txt", "Output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.reasoning()).contains("This is the reasoning");
        assertThat(judgment.reasoning()).contains("multiple lines");
    }

    @Test
    void shouldIncludeFilesModifiedFromMetadata() {
        // Given
        when(responseSpec.content()).thenReturn("""
            Score: 9
            Reasoning: Correct file created.
            """);

        JudgmentContext context = JudgmentContext.builder()
                .goal("Create test.txt")
                .workspace(Path.of("/tmp/test"))
                .agentOutput("File created")
                .executionTime(Duration.ofSeconds(5))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .metadata("files_modified", "test.txt")
                .build();

        // When
        Judgment judgment = judge.judge(context);

        // Then - verify judgment was created successfully with files_modified in prompt
        // The prompt is constructed internally and includes metadata
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
        NumericalScore score = (NumericalScore) judgment.score();
        assertThat(score.value()).isEqualTo(9.0);
    }

    @Test
    void shouldStoreExpectedBehavior() {
        // Create a fresh judge for this test to avoid unused stubbing issues
        ExpectedBehaviorJudge testJudge = new ExpectedBehaviorJudge(
            "The agent should read the file and return its contents", chatClientBuilder);
        assertThat(testJudge.getExpectedBehavior()).isEqualTo("The agent should read the file and return its contents");
    }

    @Test
    void shouldPassBorderlineScore() {
        // Given - Score of exactly 7 should pass
        when(responseSpec.content()).thenReturn("""
            Score: 7
            Reasoning: Meets the minimum threshold.
            """);

        JudgmentContext context = createContext("Test task", "Output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);
    }

    @Test
    void shouldFailJustBelowThreshold() {
        // Given - Score of 6.9 should fail
        when(responseSpec.content()).thenReturn("""
            Score: 6.9
            Reasoning: Just below the threshold.
            """);

        JudgmentContext context = createContext("Test task", "Output");

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
    }

    private JudgmentContext createContext(String goal, String output) {
        return JudgmentContext.builder()
                .goal(goal)
                .workspace(Path.of("/tmp/test"))
                .agentOutput(output)
                .executionTime(Duration.ofSeconds(5))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .build();
    }

}
