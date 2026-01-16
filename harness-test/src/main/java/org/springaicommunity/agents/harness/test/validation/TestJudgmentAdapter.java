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

import org.springaicommunity.agents.judge.jury.Verdict;
import org.springaicommunity.agents.judge.result.Judgment;
import org.springaicommunity.agents.judge.result.JudgmentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapts spring-ai-agents-judge Verdict to TestResult.
 *
 * <p>Transforms the rich Verdict structure into test-friendly results:
 * <ul>
 *   <li>Extracts pass/fail from aggregated judgment</li>
 *   <li>Collects reasoning from all judges</li>
 *   <li>Identifies specific failures</li>
 * </ul>
 */
public class TestJudgmentAdapter {

    /**
     * Test validation result.
     */
    public record ValidationResult(
        boolean passed,
        double confidence,
        String reasoning,
        List<String> issues,
        Map<String, Judgment> judgeResults
    ) {

        public static ValidationResult passed(String reasoning, Map<String, Judgment> judgeResults) {
            return new ValidationResult(true, 1.0, reasoning, List.of(), judgeResults);
        }

        public static ValidationResult failed(String reasoning, List<String> issues, Map<String, Judgment> judgeResults) {
            return new ValidationResult(false, 0.0, reasoning, issues, judgeResults);
        }
    }

    /**
     * Convert a Verdict to ValidationResult.
     *
     * @param verdict the jury verdict
     * @return validation result
     */
    public ValidationResult adapt(Verdict verdict) {
        Judgment aggregated = verdict.aggregated();

        boolean passed = aggregated.pass();
        String reasoning = aggregated.reasoning();

        // Collect issues from failed judges
        List<String> issues = new ArrayList<>();
        for (var entry : verdict.individualByName().entrySet()) {
            Judgment judgment = entry.getValue();
            if (!judgment.pass()) {
                issues.add(entry.getKey() + ": " + judgment.reasoning());
            }
        }

        // Calculate confidence based on how many judges passed
        double confidence = calculateConfidence(verdict);

        return new ValidationResult(
                passed,
                confidence,
                reasoning,
                issues,
                Map.copyOf(verdict.individualByName())
        );
    }

    /**
     * Calculate confidence score based on judge agreement.
     */
    double calculateConfidence(Verdict verdict) {
        if (verdict.individual().isEmpty()) {
            return 1.0;
        }

        long passed = verdict.individual().stream()
                .filter(j -> j.status() == JudgmentStatus.PASS)
                .count();

        return (double) passed / verdict.individual().size();
    }

}
