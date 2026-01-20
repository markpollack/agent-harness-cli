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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ExpectedBehaviorJudge using real Anthropic API.
 *
 * <p>These tests require ANTHROPIC_API_KEY environment variable to be set.
 * The judge uses Claude Opus 4.5 for evaluation.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ExpectedBehaviorJudgeIT {

    private static ChatClient.Builder chatClientBuilder;

    @BeforeAll
    static void setUp() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        AnthropicApi api = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model("claude-opus-4-5")  // Use Opus 4.5 for judge
                .maxTokens(4096)  // Higher limit for detailed reasoning
                .build();

        ChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();

        chatClientBuilder = ChatClient.builder(chatModel);
    }

    @Test
    void shouldEvaluatePerfectExecution() {
        // Given
        ExpectedBehaviorJudge judge = new ExpectedBehaviorJudge(
                "The agent should read the file contents and return them exactly as they appear",
                chatClientBuilder);

        JudgmentContext context = JudgmentContext.builder()
                .goal("Read and return the contents of config.txt")
                .workspace(Path.of("/tmp/test"))
                .agentOutput("File contents: debug=true\nport=8080\nhost=localhost")
                .executionTime(Duration.ofSeconds(2))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment).isNotNull();
        assertThat(judgment.score()).isInstanceOf(NumericalScore.class);
        NumericalScore score = (NumericalScore) judgment.score();

        // A perfect execution should get a high score (>= 7)
        assertThat(score.value()).isGreaterThanOrEqualTo(7.0);
        assertThat(judgment.reasoning()).isNotBlank();

        System.out.println("Score: " + score.value());
        System.out.println("Status: " + judgment.status());
        System.out.println("Reasoning: " + judgment.reasoning());
        System.out.println("Loss: " + judgment.metadata().get("loss"));
    }

    @Test
    void shouldEvaluatePartialExecution() {
        // Given
        ExpectedBehaviorJudge judge = new ExpectedBehaviorJudge(
                "The agent should implement a complete Calculator class with add, subtract, multiply, and divide methods",
                chatClientBuilder);

        JudgmentContext context = JudgmentContext.builder()
                .goal("Implement a Calculator class with basic arithmetic operations")
                .workspace(Path.of("/tmp/test"))
                .agentOutput("""
                    Created Calculator.java:
                    ```java
                    public class Calculator {
                        public int add(int a, int b) {
                            return a + b;
                        }
                        public int subtract(int a, int b) {
                            return a - b;
                        }
                    }
                    ```
                    Note: multiply and divide methods to be added later.
                    """)
                .executionTime(Duration.ofSeconds(5))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment).isNotNull();
        NumericalScore score = (NumericalScore) judgment.score();

        // Partial implementation should get a medium score (3-7)
        assertThat(score.value()).isBetween(3.0, 7.0);

        System.out.println("Score: " + score.value());
        System.out.println("Status: " + judgment.status());
        System.out.println("Reasoning: " + judgment.reasoning());
        System.out.println("Loss: " + judgment.metadata().get("loss"));
    }

    @Test
    void shouldEvaluateFailedExecution() {
        // Given
        ExpectedBehaviorJudge judge = new ExpectedBehaviorJudge(
                "The agent should fix the bug in the authentication function",
                chatClientBuilder);

        JudgmentContext context = JudgmentContext.builder()
                .goal("Fix the authentication bug where users can bypass login")
                .workspace(Path.of("/tmp/test"))
                .agentOutput("I couldn't find the authentication function. Please provide more details.")
                .executionTime(Duration.ofSeconds(10))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment).isNotNull();
        NumericalScore score = (NumericalScore) judgment.score();

        // Failed task should get a low score (< 5)
        assertThat(score.value()).isLessThan(5.0);
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);

        System.out.println("Score: " + score.value());
        System.out.println("Status: " + judgment.status());
        System.out.println("Reasoning: " + judgment.reasoning());
        System.out.println("Loss: " + judgment.metadata().get("loss"));
    }

    @Test
    void shouldIncludeFilesModifiedInEvaluation() {
        // Given
        ExpectedBehaviorJudge judge = new ExpectedBehaviorJudge(
                "The agent should create a new Java file with a simple Hello World class",
                chatClientBuilder);

        JudgmentContext context = JudgmentContext.builder()
                .goal("Create HelloWorld.java")
                .workspace(Path.of("/tmp/test"))
                .agentOutput("""
                    Created HelloWorld.java:
                    ```java
                    public class HelloWorld {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                    ```
                    """)
                .executionTime(Duration.ofSeconds(3))
                .startedAt(Instant.now())
                .status(ExecutionStatus.SUCCESS)
                .metadata("files_modified", "HelloWorld.java")
                .build();

        // When
        Judgment judgment = judge.judge(context);

        // Then
        assertThat(judgment).isNotNull();
        NumericalScore score = (NumericalScore) judgment.score();

        // Perfect execution with file created should get high score
        assertThat(score.value()).isGreaterThanOrEqualTo(8.0);
        assertThat(judgment.status()).isEqualTo(JudgmentStatus.PASS);

        System.out.println("Score: " + score.value());
        System.out.println("Status: " + judgment.status());
        System.out.println("Reasoning: " + judgment.reasoning());
        System.out.println("Loss: " + judgment.metadata().get("loss"));
    }

}
