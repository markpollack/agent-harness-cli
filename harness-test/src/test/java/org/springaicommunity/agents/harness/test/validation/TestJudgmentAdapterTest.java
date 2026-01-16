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
import org.springaicommunity.agents.judge.jury.Verdict;
import org.springaicommunity.agents.judge.result.Judgment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TestJudgmentAdapter.
 */
class TestJudgmentAdapterTest {

    private TestJudgmentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TestJudgmentAdapter();
    }

    @Test
    void adaptPassingVerdict() {
        Judgment passing = Judgment.pass("All checks passed");
        Verdict verdict = Verdict.builder()
                .aggregated(passing)
                .individual(List.of(passing))
                .individualByName(Map.of("Judge1", passing))
                .weights(Map.of())
                .build();

        TestJudgmentAdapter.ValidationResult result = adapter.adapt(verdict);

        assertThat(result.passed()).isTrue();
        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.reasoning()).isEqualTo("All checks passed");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void adaptFailingVerdict() {
        Judgment failing = Judgment.fail("Check failed");
        Verdict verdict = Verdict.builder()
                .aggregated(failing)
                .individual(List.of(failing))
                .individualByName(Map.of("Judge1", failing))
                .weights(Map.of())
                .build();

        TestJudgmentAdapter.ValidationResult result = adapter.adapt(verdict);

        assertThat(result.passed()).isFalse();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.issues()).contains("Judge1: Check failed");
    }

    @Test
    void adaptMixedVerdict() {
        Judgment passing = Judgment.pass("Check passed");
        Judgment failing = Judgment.fail("Check failed");
        Judgment aggregated = Judgment.pass("Majority passed");

        Verdict verdict = Verdict.builder()
                .aggregated(aggregated)
                .individual(List.of(passing, passing, failing))
                .individualByName(Map.of(
                        "FileExists", passing,
                        "FileContent", passing,
                        "NoExceptions", failing
                ))
                .weights(Map.of())
                .build();

        TestJudgmentAdapter.ValidationResult result = adapter.adapt(verdict);

        assertThat(result.passed()).isTrue();
        // 2 out of 3 passed
        assertThat(result.confidence()).isCloseTo(0.667, within(0.01));
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0)).contains("NoExceptions");
    }

    @Test
    void calculateConfidenceWithAllPassing() {
        Judgment pass1 = Judgment.pass("ok");
        Judgment pass2 = Judgment.pass("ok");

        Verdict verdict = Verdict.builder()
                .aggregated(pass1)
                .individual(List.of(pass1, pass2))
                .individualByName(Map.of())
                .weights(Map.of())
                .build();

        double confidence = adapter.calculateConfidence(verdict);

        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    void calculateConfidenceWithAllFailing() {
        Judgment fail1 = Judgment.fail("error");
        Judgment fail2 = Judgment.fail("error");

        Verdict verdict = Verdict.builder()
                .aggregated(fail1)
                .individual(List.of(fail1, fail2))
                .individualByName(Map.of())
                .weights(Map.of())
                .build();

        double confidence = adapter.calculateConfidence(verdict);

        assertThat(confidence).isEqualTo(0.0);
    }

    @Test
    void calculateConfidenceWithEmptyJudgments() {
        Judgment aggregated = Judgment.pass("default");

        Verdict verdict = Verdict.builder()
                .aggregated(aggregated)
                .individual(List.of())
                .individualByName(Map.of())
                .weights(Map.of())
                .build();

        double confidence = adapter.calculateConfidence(verdict);

        assertThat(confidence).isEqualTo(1.0);
    }

}
