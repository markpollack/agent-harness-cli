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

import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.llm.LLMJudge;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;
import org.springframework.ai.chat.client.ChatClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-powered judge that evaluates agent output against expected behavior.
 *
 * <p>This judge uses Claude Opus 4.5 to provide a nuanced 0-10 numerical score
 * based on how well the agent's output matches the expected behavior defined
 * in the test case. This enables more granular loss calculation compared to
 * binary pass/fail evaluation.
 *
 * <p><strong>Loss Calculation:</strong>
 * <pre>
 * loss = 1 - (judge_score / 10)
 * </pre>
 *
 * <p><strong>Score Guide:</strong>
 * <ul>
 *   <li>0: Complete failure, task not attempted or completely wrong</li>
 *   <li>3: Partial attempt, major issues</li>
 *   <li>5: Partially correct, significant gaps</li>
 *   <li>7: Mostly correct, minor issues</li>
 *   <li>10: Perfect execution, exactly as expected</li>
 * </ul>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class ExpectedBehaviorJudge extends LLMJudge {

    private static final Pattern SCORE_PATTERN = Pattern.compile("Score:\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    private final String expectedBehavior;

    /**
     * Create an ExpectedBehaviorJudge with expected behavior and chat client.
     *
     * @param expectedBehavior the expected behavior description from the test case
     * @param chatClientBuilder the chat client builder for LLM calls (using Opus 4.5)
     */
    public ExpectedBehaviorJudge(String expectedBehavior, ChatClient.Builder chatClientBuilder) {
        super("ExpectedBehavior", "Evaluates agent output against expected behavior (0-10)", chatClientBuilder);
        this.expectedBehavior = expectedBehavior;
    }

    @Override
    protected String buildPrompt(JudgmentContext context) {
        String goal = context.goal();
        String workspace = context.workspace() != null ? context.workspace().toString() : "Not specified";
        String output = context.agentOutput().orElse("No output provided");

        // Extract files modified from metadata if available
        String filesModified = extractFilesModified(context);

        return String.format("""
            You are evaluating an AI agent's task completion.

            **Task Goal**: %s
            **Expected Behavior**: %s
            **Agent Output**: %s
            **Workspace**: %s
            **Files Modified**: %s

            Rate the agent's performance from 0-10:
            - 0: Complete failure, task not attempted or completely wrong
            - 3: Partial attempt, major issues
            - 5: Partially correct, significant gaps
            - 7: Mostly correct, minor issues
            - 10: Perfect execution, exactly as expected

            Consider:
            1. Did the agent understand the task correctly?
            2. Is the solution semantically correct?
            3. Was the approach efficient and appropriate?
            4. Are there edge cases or issues missed?

            Format your response as:
            Score: [0-10]
            Reasoning: [detailed explanation]
            """, goal, expectedBehavior, output, workspace, filesModified);
    }

    @Override
    protected Judgment parseResponse(String response, JudgmentContext context) {
        double score = extractScore(response);
        String reasoning = extractReasoning(response);

        // Determine pass/fail based on score threshold (>= 7 is considered pass)
        boolean pass = score >= 7.0;

        return Judgment.builder()
            .score(NumericalScore.outOfTen(score))
            .status(pass ? JudgmentStatus.PASS : JudgmentStatus.FAIL)
            .reasoning(reasoning)
            .metadata("raw_score", score)
            .metadata("loss", 1.0 - (score / 10.0))
            .build();
    }

    /**
     * Extract the numerical score from the LLM response.
     */
    private double extractScore(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                // Clamp to valid range
                return Math.max(0.0, Math.min(10.0, score));
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        // Default to middle score if parsing fails
        return 5.0;
    }

    /**
     * Extract the reasoning from the LLM response.
     */
    private String extractReasoning(String response) {
        int reasoningIndex = response.indexOf("Reasoning:");
        if (reasoningIndex >= 0) {
            return response.substring(reasoningIndex + "Reasoning:".length()).trim();
        }

        // Fallback: return everything after Score line
        int scoreIndex = response.indexOf("Score:");
        if (scoreIndex >= 0) {
            int newlineIndex = response.indexOf('\n', scoreIndex);
            if (newlineIndex >= 0) {
                return response.substring(newlineIndex + 1).trim();
            }
        }

        // Final fallback: return full response
        return response;
    }

    /**
     * Extract files modified information from context metadata.
     */
    private String extractFilesModified(JudgmentContext context) {
        Object filesModified = context.metadata().get("files_modified");
        if (filesModified != null) {
            return filesModified.toString();
        }
        return "Not tracked";
    }

    /**
     * Get the expected behavior this judge evaluates against.
     */
    public String getExpectedBehavior() {
        return expectedBehavior;
    }

}
