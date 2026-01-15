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
package org.springaicommunity.agents.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LinearAgentCallback")
class LinearAgentCallbackTest {

    private StringWriter outputWriter;
    private PrintWriter out;

    @BeforeEach
    void setUp() {
        outputWriter = new StringWriter();
        out = new PrintWriter(outputWriter, true);
    }

    private LinearAgentCallback createCallback(String input) {
        BufferedReader in = new BufferedReader(new StringReader(input));
        return new LinearAgentCallback(out, in);
    }

    @Nested
    @DisplayName("onThinking")
    class OnThinking {

        @Test
        @DisplayName("should print thinking indicator when verbose")
        void shouldPrintThinkingIndicator() {
            LinearAgentCallback callback = createCallback("");
            callback.onThinking();

            assertThat(outputWriter.toString()).contains("[Thinking...]");
        }

        @Test
        @DisplayName("should not print when not verbose")
        void shouldNotPrintWhenNotVerbose() {
            BufferedReader in = new BufferedReader(new StringReader(""));
            LinearAgentCallback callback = new LinearAgentCallback(out, in, false);
            callback.onThinking();

            assertThat(outputWriter.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("onToolCall")
    class OnToolCall {

        @Test
        @DisplayName("should print tool name")
        void shouldPrintToolName() {
            LinearAgentCallback callback = createCallback("");
            callback.onToolCall("ReadTool", "{\"file\": \"test.txt\"}");

            assertThat(outputWriter.toString()).contains("Tool: ReadTool");
        }
    }

    @Nested
    @DisplayName("onToolResult")
    class OnToolResult {

        @Test
        @DisplayName("should print truncated result when verbose")
        void shouldPrintTruncatedResult() {
            LinearAgentCallback callback = createCallback("");
            String longResult = "x".repeat(300);
            callback.onToolResult("ReadTool", longResult);

            String output = outputWriter.toString();
            assertThat(output).contains("Result:");
            assertThat(output).contains("...");
            assertThat(output.length()).isLessThan(300);
        }

        @Test
        @DisplayName("should not print when not verbose")
        void shouldNotPrintWhenNotVerbose() {
            BufferedReader in = new BufferedReader(new StringReader(""));
            LinearAgentCallback callback = new LinearAgentCallback(out, in, false);
            callback.onToolResult("ReadTool", "some result");

            assertThat(outputWriter.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("onResponse")
    class OnResponse {

        @Test
        @DisplayName("should print response text")
        void shouldPrintResponseText() {
            LinearAgentCallback callback = createCallback("");
            callback.onResponse("Hello, I can help you.", true);

            assertThat(outputWriter.toString()).contains("Hello, I can help you.");
        }

        @Test
        @DisplayName("should not print blank responses")
        void shouldNotPrintBlankResponses() {
            LinearAgentCallback callback = createCallback("");
            callback.onResponse("", true);
            callback.onResponse("  ", true);
            callback.onResponse(null, true);

            assertThat(outputWriter.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("onError")
    class OnError {

        @Test
        @DisplayName("should print error message")
        void shouldPrintErrorMessage() {
            LinearAgentCallback callback = createCallback("");
            callback.onError(new RuntimeException("Something went wrong"));

            assertThat(outputWriter.toString()).contains("Error: Something went wrong");
        }
    }

    @Nested
    @DisplayName("onQuestion")
    class OnQuestion {

        @Test
        @DisplayName("should return selected option by number")
        void shouldReturnSelectedOptionByNumber() {
            LinearAgentCallback callback = createCallback("1\n");

            Question question = new Question(
                    "Which language?",
                    "Language",
                    List.of(
                            new Question.Option("Java", "Use Java"),
                            new Question.Option("Python", "Use Python")
                    ),
                    false
            );

            Map<String, String> answers = callback.onQuestion(List.of(question));

            assertThat(answers).containsEntry("Which language?", "Java");
        }

        @Test
        @DisplayName("should handle custom answer")
        void shouldHandleCustomAnswer() {
            LinearAgentCallback callback = createCallback("3\nTypeScript\n");

            Question question = new Question(
                    "Which language?",
                    "Language",
                    List.of(
                            new Question.Option("Java", "Use Java"),
                            new Question.Option("Python", "Use Python")
                    ),
                    false
            );

            Map<String, String> answers = callback.onQuestion(List.of(question));

            assertThat(answers).containsEntry("Which language?", "TypeScript");
        }

        @Test
        @DisplayName("should treat non-numeric input as custom answer")
        void shouldTreatNonNumericAsCustom() {
            LinearAgentCallback callback = createCallback("Kotlin\n");

            Question question = new Question(
                    "Which language?",
                    "Language",
                    List.of(
                            new Question.Option("Java", "Use Java"),
                            new Question.Option("Python", "Use Python")
                    ),
                    false
            );

            Map<String, String> answers = callback.onQuestion(List.of(question));

            assertThat(answers).containsEntry("Which language?", "Kotlin");
        }
    }
}
