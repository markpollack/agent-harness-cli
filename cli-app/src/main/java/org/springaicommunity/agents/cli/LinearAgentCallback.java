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

import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agents.harness.callback.AgentCallback;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentCallback implementation for linear (non-TUI) mode.
 * <p>
 * Outputs to stdout with plain text, reads from stdin for questions.
 * Used for --linear mode (expect testing) and -p/--print mode (CI automation).
 */
public class LinearAgentCallback implements AgentCallback {

    private final PrintWriter out;
    private final BufferedReader in;
    private final boolean verbose;

    public LinearAgentCallback(PrintWriter out, BufferedReader in) {
        this(out, in, true);
    }

    public LinearAgentCallback(PrintWriter out, BufferedReader in, boolean verbose) {
        this.out = out;
        this.in = in;
        this.verbose = verbose;
    }

    @Override
    public void onThinking() {
        if (verbose) {
            out.println("[Thinking...]");
            out.flush();
        }
    }

    @Override
    public void onToolCall(String toolName, String toolInput) {
        out.printf("Tool: %s%n", toolName);
        out.flush();
    }

    @Override
    public void onToolResult(String toolName, String toolResult) {
        if (verbose && toolResult != null) {
            String truncated = toolResult.length() > 200
                    ? toolResult.substring(0, 200) + "..."
                    : toolResult;
            out.printf("Result: %s%n", truncated);
            out.flush();
        }
    }

    @Override
    public Map<String, String> onQuestion(List<Question> questions) {
        Map<String, String> answers = new HashMap<>();

        for (Question question : questions) {
            out.println();
            out.println(question.question());

            var options = question.options();
            for (int i = 0; i < options.size(); i++) {
                var opt = options.get(i);
                out.printf("  %d. %s - %s%n", i + 1, opt.label(), opt.description());
            }
            out.printf("  %d. Other (type custom answer)%n", options.size() + 1);
            out.print("Choice: ");
            out.flush();

            try {
                String line = in.readLine();
                if (line == null || line.isBlank()) {
                    continue;
                }

                try {
                    int choice = Integer.parseInt(line.trim());
                    if (choice >= 1 && choice <= options.size()) {
                        answers.put(question.question(), options.get(choice - 1).label());
                    } else if (choice == options.size() + 1) {
                        out.print("Enter answer: ");
                        out.flush();
                        String customAnswer = in.readLine();
                        if (customAnswer != null && !customAnswer.isBlank()) {
                            answers.put(question.question(), customAnswer.trim());
                        }
                    }
                } catch (NumberFormatException e) {
                    // Treat as custom answer
                    answers.put(question.question(), line.trim());
                }
            } catch (IOException e) {
                // Skip on I/O error
            }
        }

        return answers;
    }

    @Override
    public void onResponse(String text, boolean isFinal) {
        if (text != null && !text.isBlank()) {
            out.println(text);
            out.flush();
        }
    }

    @Override
    public void onError(Throwable error) {
        out.printf("Error: %s%n", error.getMessage());
        out.flush();
    }

    @Override
    public void onComplete() {
        // No-op for linear mode
    }
}
