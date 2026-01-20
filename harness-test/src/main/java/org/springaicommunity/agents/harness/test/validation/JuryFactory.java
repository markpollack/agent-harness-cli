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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.harness.test.usecase.SuccessCriterion;
import org.springaicommunity.agents.harness.test.usecase.UseCase;
import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.fs.FileContentJudge;
import org.springaicommunity.judge.fs.FileExistsJudge;
import org.springaicommunity.judge.jury.Jury;
import org.springaicommunity.judge.jury.SimpleJury;
import org.springaicommunity.judge.jury.WeightedAverageStrategy;
import org.springaicommunity.judge.result.Judgment;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Factory for building Jury instances from UseCase success criteria.
 *
 * <p>Maps UseCase.successCriteria to spring-ai-agents-judge judges:
 * <ul>
 *   <li>{@code file_exists} → {@code FileExistsJudge}</li>
 *   <li>{@code file_contains} → {@code FileContentJudge}</li>
 *   <li>{@code no_exceptions} → Custom exception detection judge</li>
 *   <li>{@code output_contains} → Custom output pattern judge</li>
 * </ul>
 *
 * <p>When a ChatClient.Builder is provided and the UseCase has an expectedBehavior
 * field, an LLM-powered ExpectedBehaviorJudge is added with 60% weight, while
 * deterministic judges share the remaining 40%.
 */
public class JuryFactory {

    private static final Logger logger = LoggerFactory.getLogger(JuryFactory.class);

    /** Weight for LLM judge when expectedBehavior is defined */
    private static final double LLM_JUDGE_WEIGHT = 0.6;

    /** Weight multiplier for deterministic judges when LLM judge is present */
    private static final double DETERMINISTIC_WEIGHT_MULTIPLIER = 0.4;

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Create factory for building deterministic judges only.
     * No LLM-based evaluation will be performed.
     */
    public JuryFactory() {
        this(null);
    }

    /**
     * Create factory with ChatClient for LLM-based judges.
     *
     * @param chatClientBuilder the chat client builder for LLM judges (null for deterministic only)
     */
    public JuryFactory(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * Build a Jury from UseCase criteria.
     *
     * @param useCase the use case with success criteria
     * @param workspacePath the workspace path for file-based judges
     * @return configured Jury
     */
    public Jury buildJury(UseCase useCase, Path workspacePath) {
        SimpleJury.Builder juryBuilder = SimpleJury.builder()
                .votingStrategy(new WeightedAverageStrategy());

        // Check if LLM judge will be added
        boolean hasExpectedBehavior = useCase.expectedBehavior() != null && !useCase.expectedBehavior().isBlank();
        boolean addLlmJudge = hasExpectedBehavior && chatClientBuilder != null;

        // Determine weight multiplier for deterministic judges
        double weightMultiplier = addLlmJudge ? DETERMINISTIC_WEIGHT_MULTIPLIER : 1.0;

        // Add deterministic judges from success criteria
        for (SuccessCriterion criterion : useCase.successCriteria()) {
            Judge judge = createJudge(criterion, workspacePath);
            if (judge != null) {
                double weight = calculateWeight(criterion) * weightMultiplier;
                juryBuilder.judge(judge, weight);
                logger.debug("Added judge for criterion: {} with weight {}", criterion.type(), weight);
            }
        }

        // Add LLM judge for expectedBehavior if ChatClient is available
        if (addLlmJudge) {
            ExpectedBehaviorJudge llmJudge = new ExpectedBehaviorJudge(
                    useCase.expectedBehavior(), chatClientBuilder);
            juryBuilder.judge(llmJudge, LLM_JUDGE_WEIGHT);
            logger.debug("Added LLM ExpectedBehaviorJudge with weight {}", LLM_JUDGE_WEIGHT);
        } else if (hasExpectedBehavior) {
            logger.warn("Expected behavior defined but no ChatClient.Builder provided - skipping LLM judge");
        }

        // Ensure at least one judge
        if (useCase.successCriteria().isEmpty() && !addLlmJudge) {
            logger.debug("No success criteria, adding default success judge");
            juryBuilder.judge(ctx -> Judgment.pass("No specific criteria defined"), 1.0);
        }

        return juryBuilder.build();
    }

    /**
     * Create a Judge from a SuccessCriterion.
     */
    Judge createJudge(SuccessCriterion criterion, Path workspacePath) {
        return switch (criterion.type()) {
            case SuccessCriterion.TYPE_FILE_EXISTS -> createFileExistsJudge(criterion, workspacePath);
            case SuccessCriterion.TYPE_FILE_CONTAINS -> createFileContainsJudge(criterion, workspacePath);
            case SuccessCriterion.TYPE_NO_EXCEPTIONS -> createNoExceptionsJudge();
            case SuccessCriterion.TYPE_OUTPUT_CONTAINS -> createOutputContainsJudge(criterion);
            default -> {
                logger.warn("Unknown criterion type: {}", criterion.type());
                yield null;
            }
        };
    }

    private Judge createFileExistsJudge(SuccessCriterion criterion, Path workspacePath) {
        if (criterion.args().isEmpty()) {
            logger.warn("file_exists criterion missing path argument");
            return null;
        }
        String relativePath = criterion.args().get(0);
        // FileExistsJudge resolves path against JudgmentContext.workspace()
        return new FileExistsJudge(relativePath);
    }

    private Judge createFileContainsJudge(SuccessCriterion criterion, Path workspacePath) {
        if (criterion.args().size() < 2) {
            logger.warn("file_contains criterion requires [path, text] arguments");
            return null;
        }
        String relativePath = criterion.args().get(0);
        String expectedText = criterion.args().get(1);
        // FileContentJudge resolves path against JudgmentContext.workspace()
        // Use CONTAINS mode for partial matching
        return new FileContentJudge(relativePath, expectedText, FileContentJudge.MatchMode.CONTAINS);
    }

    private Judge createNoExceptionsJudge() {
        return ctx -> {
            String output = ctx.agentOutput().orElse("");
            // Check for common exception patterns
            boolean hasException = Pattern.compile(
                    "(?i)(exception|error|failed|traceback|stack.?trace)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(output).find();

            // More specific checks
            boolean hasJavaException = output.contains("Exception in thread")
                    || output.contains("at java.")
                    || output.contains("Caused by:");

            if (hasJavaException) {
                return Judgment.fail("Java exception detected in output");
            }
            if (hasException && output.contains("Traceback")) {
                return Judgment.fail("Python exception detected in output");
            }
            return Judgment.pass("No exceptions detected in output");
        };
    }

    private Judge createOutputContainsJudge(SuccessCriterion criterion) {
        if (criterion.args().isEmpty()) {
            logger.warn("output_contains criterion missing text argument");
            return ctx -> Judgment.fail("Invalid output_contains criterion");
        }
        String expectedText = criterion.args().get(0);
        return ctx -> {
            String output = ctx.agentOutput().orElse("");
            if (output.contains(expectedText)) {
                return Judgment.pass("Output contains expected text: " + expectedText);
            } else {
                return Judgment.fail("Output does not contain expected text: " + expectedText);
            }
        };
    }

    /**
     * Calculate weight for a criterion based on type.
     */
    double calculateWeight(SuccessCriterion criterion) {
        return switch (criterion.type()) {
            case SuccessCriterion.TYPE_FILE_CONTAINS -> 0.3;  // Higher weight for content checks
            case SuccessCriterion.TYPE_FILE_EXISTS -> 0.2;
            case SuccessCriterion.TYPE_NO_EXCEPTIONS -> 0.2;
            case SuccessCriterion.TYPE_OUTPUT_CONTAINS -> 0.2;
            case SuccessCriterion.TYPE_COMMAND_SUCCEEDS -> 0.3;
            default -> 0.1;
        };
    }

}
